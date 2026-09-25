<?php declare(strict_types=1);

namespace Mmo\Game;

use Mmo\World\GridCell;

final class Actor
{
    public const KIND_PLAYER = 'player';
    public const KIND_PORING = 'poring';

    public const STATE_IDLE = 'idle';
    public const STATE_WALK = 'walk';
    public const STATE_ATTACK = 'attack';
    public const STATE_HURT = 'hurt';
    public const STATE_DEAD = 'dead';

    /** @var list<GridCell> */
    public array $path = [];
    public ?GridCell $pathGoal = null;
    public ?float $attackImpactAt = null;
    public ?string $attackTargetId = null;
    public ?float $respawnAt = null;
    public ?float $hurtUntil = null;
    public float $nextPathRefreshAt = 0.0;
    public float $nextThinkAt = 0.0;
    public float $animationOffset = 0.0;
    public bool $connected = false;
    public ?float $disconnectedAt = null;
    public ?Inventory $inventory = null;
    public ?int $databaseId = null;
    public int $lastProcessedInput = 0;

    public function __construct(
        public readonly string $id,
        public readonly string $kind,
        public string $name,
        public float $x,
        public float $y,
        public float $z,
        public float $facing,
        public string $state,
        public int $hp,
        public readonly int $maxHp,
        public readonly int $attack,
        public readonly int $defense,
        public readonly GridCell $spawnCell,
        public ?string $targetId = null,
        public float $nextAttackAt = 0.0,
    ) {
    }

    public function isPlayer(): bool
    {
        return $this->kind === self::KIND_PLAYER;
    }

    public function isDead(): bool
    {
        return $this->state === self::STATE_DEAD;
    }

    public function distanceTo(Actor $other): float
    {
        return hypot($this->x - $other->x, $this->z - $other->z);
    }

    /** @return array<string, int|float|string|null> */
    public function snapshot(): array
    {
        return [
            'id' => $this->id,
            'kind' => $this->kind,
            'name' => $this->name,
            'position' => [
                'x' => $this->x,
                'y' => $this->y,
                'z' => $this->z,
            ],
            'facing' => $this->facing,
            'state' => $this->state,
            'hp' => $this->hp,
            'maxHp' => $this->maxHp,
            'targetId' => $this->targetId,
            'nextAttackAt' => $this->nextAttackAt,
        ];
    }
}
