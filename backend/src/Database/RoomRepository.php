<?php declare(strict_types=1);

namespace Mmo\Database;

use Mmo\Config\Config;

final class RoomRepository
{
    public function __construct(
        private readonly Database $database,
        private readonly Config $config,
    ) {
    }

    /** @return list<array{code: string, name: string, status: string, playerCount: int, maxPlayers: int}> */
    public function publicRooms(): array
    {
        $rooms = $this->database->table('rooms');
        $players = $this->database->table('players');
        $statement = $this->database->pdo()->query(
            "SELECT r.code, r.name, r.status, r.max_players,
                    COUNT(p.id) AS player_count
             FROM {$rooms} r
             LEFT JOIN {$players} p ON p.room_code = r.code
             WHERE r.status IN ('active', 'paused')
             GROUP BY r.code, r.name, r.status, r.max_players
             ORDER BY r.code",
        );
        if ($statement === false) {
            return [];
        }

        $rooms = [];
        foreach ($statement->fetchAll() as $row) {
            $rooms[] = [
                'code' => substr((string) $row['code'], 0, 32),
                'name' => substr((string) $row['name'], 0, 64),
                'status' => ((string) $row['status'] === 'paused') ? 'paused' : 'active',
                'playerCount' => (int) $row['player_count'],
                'maxPlayers' => (int) $row['max_players'],
            ];
        }

        return $rooms;
    }

    /**
     * A single joinable room, including the world seed.
     *
     * The long-polling runtime rebuilds a room per request, so it needs the
     * seed to regenerate the same terrain and poring spawn points. The seed
     * lives in configuration rather than the table because the rooms schema
     * predates the polling runtime and the daemon supplies it the same way.
     *
     * @return array{code: string, name: string, maxPlayers: int}|null
     */
    public function find(string $code): ?array
    {
        $statement = $this->database->pdo()->prepare(
            "SELECT code, name, max_players FROM " . $this->database->table('rooms')
            . " WHERE code = :code AND status IN ('active', 'paused') LIMIT 1",
        );
        $statement->execute(['code' => $code]);
        $row = $statement->fetch();
        if (!is_array($row)) {
            return null;
        }

        return [
            'code' => substr((string) $row['code'], 0, 32),
            'name' => substr((string) $row['name'], 0, 64),
            'maxPlayers' => (int) $row['max_players'],
        ];
    }

    public function isJoinable(string $code): bool    {
        $statement = $this->database->pdo()->prepare(
            'SELECT 1 FROM ' . $this->database->table('rooms')
            . ' WHERE code = :code AND status IN (\'active\', \'paused\') LIMIT 1',
        );
        $statement->execute(['code' => $code]);

        return $statement->fetchColumn() !== false;
    }

    public function setStatus(string $code, string $status): void
    {
        $statement = $this->database->pdo()->prepare(
            'UPDATE ' . $this->database->table('rooms')
            . ' SET status = :status, updated_at = UTC_TIMESTAMP(6) WHERE code = :code',
        );
        $statement->execute(['status' => $status, 'code' => $code]);
    }

    public function maxPlayers(string $code): int
    {
        $statement = $this->database->pdo()->prepare(
            'SELECT max_players FROM ' . $this->database->table('rooms') . ' WHERE code = :code LIMIT 1',
        );
        $statement->execute(['code' => $code]);
        $value = $statement->fetchColumn();

        return $value === false ? $this->config->int('rooms.max_players', 100) : (int) $value;
    }
}
