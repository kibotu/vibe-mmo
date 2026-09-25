<?php declare(strict_types=1);

namespace Mmo\Game;

final class FloorItem
{
    public function __construct(
        public readonly string $id,
        public readonly string $itemId,
        public int $quantity,
        public float $x,
        public float $y,
        public float $z,
        public readonly float $bobOffset,
    ) {
    }

    /** @return array<string, float|int|string> */
    public function snapshot(): array
    {
        return [
            'id' => $this->id,
            'itemId' => $this->itemId,
            'quantity' => $this->quantity,
            'position' => ['x' => $this->x, 'y' => $this->y, 'z' => $this->z],
            'bobOffset' => $this->bobOffset,
        ];
    }
}
