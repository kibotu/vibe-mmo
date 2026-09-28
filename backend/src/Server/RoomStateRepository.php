<?php declare(strict_types=1);

namespace Mmo\Server;

use Mmo\Config\Config;
use Mmo\Database\Database;

/**
 * Reads and writes the persisted half of a room's state.
 *
 * This is the long-polling counterpart to the daemon's in-memory rooms: each
 * request hydrates a room, simulates, then saves. Writes are guarded by a tick
 * comparison so a slow request that started earlier cannot overwrite a newer
 * simulation.
 */
final class RoomStateRepository
{
    public function __construct(
        private readonly Database $database,
        private readonly Config $config,
    ) {
    }

    /**
     * @return array{state: array<string, mixed>, tick: int, simulatedAt: float}|null
     */
    public function load(string $roomCode): ?array
    {
        $statement = $this->database->pdo()->prepare(
            'SELECT state, tick, simulated_at FROM ' . $this->database->table('room_state')
            . ' WHERE room_code = :room',
        );
        $statement->execute(['room' => $roomCode]);
        $row = $statement->fetch();

        if (!is_array($row)) {
            return null;
        }

        $state = is_string($row['state']) ? json_decode($row['state'], true) : null;
        if (!is_array($state)) {
            return null;
        }

        return [
            'state' => $state,
            'tick' => (int) $row['tick'],
            'simulatedAt' => strtotime((string) $row['simulated_at'] . ' UTC') ?: microtime(true),
        ];
    }

    /**
     * @param array<string, mixed> $state
     */
    public function save(string $roomCode, array $state, int $tick, float $simulatedAt): void
    {
        $json = json_encode($state, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);

        $statement = $this->database->pdo()->prepare(
            'INSERT INTO ' . $this->database->table('room_state')
            . ' (room_code, state, tick, simulated_at) VALUES (:room, :state, :tick, :simulated)'
            . ' ON DUPLICATE KEY UPDATE state = VALUES(state), tick = VALUES(tick), simulated_at = VALUES(simulated_at)',
        );
        $statement->execute([
            'room' => $roomCode,
            'state' => $json,
            'tick' => $tick,
            'simulated' => gmdate('Y-m-d H:i:s.u', (int) $simulatedAt),
        ]);
    }

    /**
     * Claims the simulation lock for a room for the duration of one request.
     *
     * Two players polling at the same instant would otherwise both read the
     * same state and both write back, so the second would duplicate every
     * movement and loot drop. GET_LOCK is released when the connection closes,
     * which is the right lifetime for a request-scoped lock.
     */
    public function acquireLock(string $roomCode, int $timeoutSeconds = 2): bool
    {
        $statement = $this->database->pdo()->prepare('SELECT GET_LOCK(:name, :timeout)');
        $statement->execute([
            'name' => 'mmo-room-' . substr(hash('sha256', $roomCode), 0, 32),
            'timeout' => $timeoutSeconds,
        ]);

        return (int) $statement->fetchColumn() === 1;
    }

    public function releaseLock(string $roomCode): void
    {
        try {
            $statement = $this->database->pdo()->prepare('SELECT RELEASE_LOCK(:name)');
            $statement->execute([
                'name' => 'mmo-room-' . substr(hash('sha256', $roomCode), 0, 32),
            ]);
        } catch (\Throwable) {
            // The lock is released when the connection closes regardless.
        }
    }

    /** Upper bound on how much time one request may simulate in a single step. */
    public function maxCatchUpSeconds(): float
    {
        return (float) $this->config->get('rooms.max_catch_up_seconds', 1.0);
    }
}
