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
        $statement = $this->database->pdo()->query(
            "SELECT r.code, r.name, r.status, r.max_players,
                    COUNT(p.id) AS player_count
             FROM rooms r
             LEFT JOIN players p ON p.room_code = r.code
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

    public function isJoinable(string $code): bool
    {
        $statement = $this->database->pdo()->prepare(
            "SELECT 1 FROM rooms WHERE code = :code AND status IN ('active', 'paused') LIMIT 1",
        );
        $statement->execute(['code' => $code]);

        return $statement->fetchColumn() !== false;
    }

    public function setStatus(string $code, string $status): void
    {
        $statement = $this->database->pdo()->prepare(
            'UPDATE rooms SET status = :status, updated_at = UTC_TIMESTAMP(6) WHERE code = :code',
        );
        $statement->execute(['status' => $status, 'code' => $code]);
    }

    public function maxPlayers(string $code): int
    {
        $statement = $this->database->pdo()->prepare('SELECT max_players FROM rooms WHERE code = :code LIMIT 1');
        $statement->execute(['code' => $code]);
        $value = $statement->fetchColumn();

        return $value === false ? $this->config->int('rooms.max_players', 100) : (int) $value;
    }
}
