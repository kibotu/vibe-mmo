<?php declare(strict_types=1);

namespace Mmo\Database;

use PDO;
use Throwable;

final class PlayerRepository
{
    public function __construct(private readonly Database $database)
    {
    }

    public function findByPublicId(string $publicId): ?PlayerRecord
    {
        $statement = $this->database->pdo()->prepare(
            'SELECT id, public_id, name, room_code, hp, x, y, z, inventory,
                    last_processed_input, state, target_id, next_attack_at
             FROM ' . $this->database->table('players') . ' WHERE public_id = :public_id LIMIT 1',
        );
        $statement->execute(['public_id' => $publicId]);
        $row = $statement->fetch();
        if (!is_array($row)) {
            return null;
        }

        return PlayerRecord::fromRow($row, (string) $row['inventory']);
    }

    /**
     * Every player currently recorded in a room, oldest first.
     *
     * The long-polling runtime rebuilds a room per request and needs the whole
     * roster so porings can see everyone and remote movement reaches the
     * snapshot. The daemon does not use this; it holds its own roster in memory.
     *
     * @return list<PlayerRecord>
     */
    public function forRoom(string $roomCode, int $limit = 20): array
    {
        if ($roomCode === '') {
            return [];
        }

        $statement = $this->database->pdo()->prepare(
            'SELECT id, public_id, name, room_code, hp, x, y, z, inventory,
                    last_processed_input, state, target_id, next_attack_at
             FROM ' . $this->database->table('players')
            . ' WHERE room_code = :room ORDER BY id LIMIT ' . max(1, $limit),
        );
        $statement->execute(['room' => $roomCode]);

        $records = [];
        foreach ($statement->fetchAll() as $row) {
            if (is_array($row)) {
                $records[] = PlayerRecord::fromRow($row, (string) $row['inventory']);
            }
        }

        return $records;
    }

    /** @param list<PlayerSave> $players */    public function saveAll(array $players): int
    {
        if ($players === []) {
            return 0;
        }

        $pdo = $this->database->pdo();
        $statement = $pdo->prepare(
            'UPDATE ' . $this->database->table('players') . '
             SET hp = :hp, x = :x, y = :y, z = :z, inventory = :inventory,
                 last_processed_input = :last_processed_input, state = :state,
                 target_id = :target_id, next_attack_at = :next_attack_at,
                 updated_at = UTC_TIMESTAMP(6)
             WHERE id = :id',
        );

        $pdo->beginTransaction();
        try {
            $count = 0;
            foreach ($players as $player) {
                $statement->execute([
                    'hp' => max(0, min(40, $player->hp)),
                    'x' => $player->x,
                    'y' => $player->y,
                    'z' => $player->z,
                    'inventory' => json_encode($player->inventory, JSON_THROW_ON_ERROR),
                    'last_processed_input' => max(0, $player->lastProcessedInput),
                    'state' => $player->state,
                    'target_id' => $player->targetId,
                    'next_attack_at' => $player->nextAttackAt,
                    'id' => $player->databaseId,
                ]);
                ++$count;
            }
            $pdo->commit();

            return $count;
        } catch (Throwable $exception) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            throw $exception;
        }
    }
}
