<?php declare(strict_types=1);

namespace Mmo\Game;

final readonly class RoomEvent
{
    public function __construct(
        public int $id,
        public string $kind,
        public ?string $actorId,
        public ?int $amount,
        public string $text,
        public ?string $itemId,
    ) {
    }

    /** @return array<string, int|string|null> */
    public function snapshot(): array
    {
        return [
            'id' => $this->id,
            'kind' => $this->kind,
            'actorId' => $this->actorId,
            'amount' => $this->amount,
            'text' => $this->text,
            'itemId' => $this->itemId,
        ];
    }
}
