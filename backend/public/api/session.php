<?php declare(strict_types=1);

use Mmo\Http\Application;
use Mmo\Http\JsonResponse;
use Mmo\Http\SecurityHeaders;

require dirname(__DIR__, 2) . '/vendor/autoload.php';

try {
    $application = Application::boot();
    SecurityHeaders::apply();
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
        header('Allow: GET');
        JsonResponse::error('method_not_allowed', 'Method not allowed.', 405);
    }
    $fetchSite = $_SERVER['HTTP_SEC_FETCH_SITE'] ?? null;
    if (is_string($fetchSite) && !in_array($fetchSite, ['same-origin', 'none'], true)) {
        JsonResponse::error('cross_site_denied', 'Cross-site session requests are not accepted.', 403);
    }
    $requestOrigin = $_SERVER['HTTP_ORIGIN'] ?? null;
    if (is_string($requestOrigin)
        && !in_array($requestOrigin, array_map(
            static fn (string $origin): string => rtrim($origin, '/'),
            $application->config->strings('server.allowed_origins'),
        ), true)
    ) {
        JsonResponse::error('origin_denied', 'Request origin is not allowed.', 403);
    }

    $credentials = $application->cookies->guestCredentials($_COOKIE);
    $identity = $application->guests->authenticate($credentials);
    if ($identity === null) {
        JsonResponse::error('guest_required', 'Join the lobby before requesting a game session.', 401);
    }

    $rooms = $application->rooms->publicRooms();
    $roomCode = $identity->roomCode;
    if ($roomCode === null || !$application->rooms->isJoinable($roomCode)) {
        $roomCode = $rooms[0]['code'] ?? null;
        if ($roomCode === null) {
            JsonResponse::error('no_active_room', 'No room is currently available.', 503);
        }
        $application->sessions->updateRoom($identity->databaseId, $roomCode);
    }
    $room = null;
    foreach ($rooms as $candidate) {
        if ($candidate['code'] === $roomCode) {
            $room = $candidate;
            break;
        }
    }
    if ($room === null) {
        JsonResponse::error('no_active_room', 'No room is currently available.', 503);
    }

    $ticket = $application->sessions->issueTicket($identity, 30);
    $publicUrl = $application->config->string('server.public_url');
    $parts = parse_url($publicUrl);
    $scheme = ($parts['scheme'] ?? 'https') === 'https' ? 'wss' : 'ws';
    $host = $parts['host'] ?? 'localhost';
    $port = isset($parts['port']) ? ':' . (int) $parts['port'] : '';
    $path = '/' . ltrim($application->config->string('server.websocket_path', '/ws'), '/');
    $websocketUrl = sprintf('%s://%s%s%s', $scheme, $host, $port, $path);

    JsonResponse::send([
        'ticketExpiresIn' => 30,
        'player' => ['id' => $identity->playerId, 'name' => $identity->name],
        'room' => ['code' => $roomCode, 'name' => $room['name']],
        'seed' => $application->config->int('world.seed', 1337),
        'websocket' => ['url' => $websocketUrl, 'ticket' => $ticket],
        'config' => [
            'tickRate' => 20,
            'snapshotRate' => 10,
            'interpolationMs' => 100,
        ],
    ]);
} catch (Throwable) {
    JsonResponse::error('service_unavailable', 'The game session is temporarily unavailable.', 503);
}
