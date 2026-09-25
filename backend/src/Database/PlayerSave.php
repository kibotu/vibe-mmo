<?php declare(strict_types=1);

namespace Mmo\Database;

final readonly class PlayerSave
{
    /** @param list<array{itemId: string, quantity: int}|null> $inventory */
    public function __construct(
        public int $databaseId,
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
}
