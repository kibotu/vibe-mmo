<?php declare(strict_types=1);

namespace Mmo\Server;

use Mmo\Database\PlayerRecord;
use Mmo\Http\Application;
use Mmo\Protocol\InputSequencer;
use Mmo\Protocol\Intent;

/**
 * One long-poll cycle: apply the caller's intents, advance the room to now, and
 * return a snapshot.
 *
 * This replaces the daemon on hosting that cannot keep a process alive. The
 * room is hydrated from room_state, stepped forward by the elapsed wall-clock
 * time, and written back, so the simulation keeps the same authoritative
 * semantics as the WebSocket path.
 */
final class PollingRoomService
{
    private const STEP_SECONDS = 1 / Room::TICK_RATE;
    private const MAX_STEPS_PER_REQUEST = 40;

    public function __construct(
        private readonly Application $application,
        private readonly RoomStateRepository $states,
        private readonly RoomStateCodec $codec,
        private readonly PollingSnapshot $snapshots,
    ) {
    }

    /**
     * @param list<array{seq: int, intent: string, payload: array<string, mixed>}> $intents
     * @return array<string, mixed>
     */
    public function poll(
        string $roomCode,
        string $roomName,
        int $seed,
        int $maxPlayers,
        PlayerRecord $self,
        array $intents,
        float $now,
        float $holdSeconds,
    ): array {
        $room = Room::create($roomCode, $roomName, $seed, $maxPlayers);

        // Every player in the room is re-presented each request: the polling
        // player so their intents land, the rest so porings can see them and
        // remote movement appears in the snapshot.
        foreach ($this->application->players->forRoom($roomCode) as $record) {
            $room->presentForPolling($record, $now);
        }
        $room->presentForPolling($self, $now);

        if (!$this->states->acquireLock($roomCode)) {
            // Another request is simulating this room right now. Answer with the
            // state as it stands rather than piling up behind the lock.
            return $this->snapshots->build($room, $self->publicId, $now, 0, false);
        }

        try {
            $stored = $this->states->load($roomCode);
            if ($stored !== null) {
                $this->codec->decodeInto($room, $stored['state'], $stored['tick']);
            }

            $since = $stored === null ? $now : (float) $stored['simulatedAt'];

            // Holding turns this into a true long poll: a quiet room costs no
            // requests, and a busy one still answers as soon as state moves.
            if ($holdSeconds > 0.0) {
                $deadline = microtime(true) + $holdSeconds;
                while (microtime(true) < $deadline) {
                    usleep(50_000);
                    $fresh = $this->states->load($roomCode);
                    if ($fresh !== null && $fresh['tick'] > $room->simulationTick()) {
                        break;
                    }
                }
            }

            $connection = new PollingConnection(
                'poll-' . $self->publicId,
                $self->publicId,
                new InputSequencer($self->lastProcessedInput),
            );

            $outcome = $this->applyIntents($room, $connection, $intents, $now);
            $elapsed = max(0.0, min(microtime(true) - $since, $this->states->maxCatchUpSeconds()));
            $steps = $this->advance($room, $elapsed, microtime(true));

            $this->application->players->saveAll($room->playerSaves());
            $encoded = $this->codec->encode($room);
            $this->states->save($roomCode, $encoded['state'], $encoded['tick'], microtime(true));

            $response = $this->snapshots->build($room, $self->publicId, microtime(true), $outcome['applied'], true);
            $response['steps'] = $steps;
            $response['simulatedSeconds'] = round($elapsed, 4);
            $response['events'] = $connection->drain();
            $response['rejected'] = $outcome['rejected'];
            // Echoed so a client can tell a lost request from a rejected intent
            // without inferring it from movement.
            $response['intentsReceived'] = count($intents);

            return $response;
        } finally {
            $this->states->releaseLock($roomCode);
        }
    }

    /**
     * Steps the simulation in fixed sizes so behaviour matches the daemon,
     * where a step is always TICK_RATE long. The cap keeps a long pause from
     * turning into hundreds of steps inside one request.
     */
    private function advance(Room $room, float $elapsed, float $now): int
    {
        $steps = 0;
        $remaining = $elapsed;
        while ($remaining > 0.0 && $steps < self::MAX_STEPS_PER_REQUEST) {
            $step = min(self::STEP_SECONDS, $remaining);
            $room->tick($now, $step);
            $remaining -= $step;
            ++$steps;
        }

        return $steps;
    }

    /**
     * @param list<array{seq: int, intent: string, payload: array<string, mixed>}> $intents
     * @return array{applied: int, rejected: list<array{seq: int, intent: string, reason: string}>}
     */
    private function applyIntents(
        Room $room,
        PollingConnection $connection,
        array $intents,
        float $now,
    ): array {
        // Oldest first, so the newest intent always wins regardless of the order
        // the poller batched them in.
        usort($intents, static fn (array $a, array $b): int => ($a['seq'] ?? 0) <=> ($b['seq'] ?? 0));

        $applied = 0;
        $rejected = [];
        foreach ($intents as $intent) {
            $sequence = (int) ($intent['seq'] ?? 0);
            $name = (string) ($intent['intent'] ?? '');
            $payload = is_array($intent['payload'] ?? null) ? $intent['payload'] : [];
            if ($sequence <= 0 || $name === '') {
                continue;
            }

            try {
                $parsed = new Intent($sequence, $name, $payload);
                $connection->inputSequencer()->accept($parsed);
                $room->handleIntent($connection, $parsed, $payload);
                $room->recordProcessedInput($connection, $sequence);
                ++$applied;
            } catch (\Throwable $exception) {
                // A rejected intent is ordinary input, not a server fault: an
                // unreachable cell, a full inventory, a dead target. It is
                // reported so the cause is diagnosable rather than invisible.
                $rejected[] = [
                    'seq' => $sequence,
                    'intent' => $name,
                    'reason' => $exception->getMessage(),
                ];
            }
        }

        return ['applied' => $applied, 'rejected' => $rejected];
    }
}
