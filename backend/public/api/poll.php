<?php declare(strict_types=1);

use Mmo\Http\Application;
use Mmo\Http\JsonResponse;
use Mmo\Http\SecurityHeaders;

require dirname(__DIR__, 2) . '/vendor/autoload.php';

/**
 * Long-poll endpoint for the authoritative simulation.
 *
 * The WebSocket daemon cannot run on shared hosting, so each request replays
 * the caller's queued intents, advances the room to now, and returns a
 * snapshot. Holding the response briefly turns this into a genuine long poll:
 * a room with nothing to report costs no requests.
 *
 * Body (JSON): {"intents":[{"seq":1,"intent":"move","payload":{...}}],"hold":0.5}
 */

try {
    $application = Application::boot();
    SecurityHeaders::apply();

    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
        header('Allow: POST');
        JsonResponse::error('method_not_allowed', 'Method not allowed.', 405);
    }

    $identity = $application->guests->authenticate(
        $application->cookies->guestCredentials($_COOKIE),
    );
    if ($identity === null) {
        JsonResponse::error('guest_required', 'Join the lobby before polling the room.', 401);
    }
    if ($application->polling === null) {
        JsonResponse::error('service_unavailable', 'The polling runtime is unavailable.', 503);
    }

    $room = $application->rooms->find($identity->roomCode ?? '');
    if ($room === null) {
        JsonResponse::error('no_active_room', 'No room is currently available.', 503);
    }

    $raw = file_get_contents('php://input');
    $decoded = $raw === false || $raw === '' ? [] : json_decode($raw, true);
    if (!is_array($decoded)) {
        JsonResponse::error('invalid_request', 'The request body must be a JSON object.', 400);
    }

    // A client-supplied hold could otherwise pin a worker for the full
    // execution limit, so the server caps it.
    $hold = isset($decoded['hold']) && is_numeric($decoded['hold'])
        ? max(0.0, min(5.0, (float) $decoded['hold']))
        : 0.0;

    $intents = [];
    foreach (($decoded['intents'] ?? []) as $entry) {
        if (!is_array($entry)) {
            continue;
        }
        $intents[] = [
            'seq' => (int) ($entry['seq'] ?? 0),
            'intent' => (string) ($entry['intent'] ?? ''),
            'payload' => is_array($entry['payload'] ?? null) ? $entry['payload'] : [],
        ];
    }
    if (count($intents) > 64) {
        JsonResponse::error('invalid_request', 'Too many intents in one poll.', 400);
    }

    $record = $application->players->findByPublicId($identity->playerId);
    if ($record === null) {
        JsonResponse::error('guest_required', 'Join the lobby before polling the room.', 401);
    }

    JsonResponse::send($application->polling->poll(
        $room['code'],
        $room['name'],
        $application->config->int('world.seed', 1337),
        $room['maxPlayers'],
        $record,
        $intents,
        microtime(true),
        $hold,
    ));
} catch (Throwable) {
    JsonResponse::error('service_unavailable', 'The room is temporarily unavailable.', 503);
}
