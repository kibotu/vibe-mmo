<?php declare(strict_types=1);

namespace Mmo\Database;

final readonly class TicketIdentity
{
    public function __construct(
        public PlayerRecord $player,
        public string $roomCode,
        public string $roomName,
        public int $roomMaxPlayers,
    ) {
    }
}
