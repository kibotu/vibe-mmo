<?php declare(strict_types=1);

namespace Mmo\Database;

final readonly class PlayerRecord
{
    /** @param list<array{itemId: string, quantity: int}|null> $inventory */
    public function __construct(
        public int $databaseId,
        public string $publicId,
        public string $name,
        public ?string $roomCode,
        public int $hp,
        public float $x,
        public float $y,
        public float $z,
        public array $inventory,
        public int $lastProcessedInput,
        public string $state,
        public ?string $targetId,
        public float $nextAttackAt,
    ) {
    }

    /** @param array<string, mixed> $row */
    public static function fromRow(array $row, string $inventoryJson): self
    {
        $inventory = json_decode($inventoryJson, true);
        if (!is_array($inventory)) {
            $inventory = array_fill(0, 20, null);
        }

        return new self(
            (int) $row['id'],
            (string) $row['public_id'],
            (string) $row['name'],
            $row['room_code'] !== null ? (string) $row['room_code'] : null,
            (int) $row['hp'],
            (float) $row['x'],
            (float) $row['y'],
            (float) $row['z'],
            array_values(array_slice($inventory, 0, 20)),
            (int) $row['last_processed_input'],
            (string) $row['state'],
            $row['target_id'] !== null ? (string) $row['target_id'] : null,
            (float) $row['next_attack_at'],
        );
    }
}
