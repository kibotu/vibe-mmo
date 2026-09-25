<?php declare(strict_types=1);

namespace Mmo\Database;

final readonly class GuestIdentity
{
    public function __construct(
        public int $sessionId,
        public int $databaseId,
        public string $playerId,
        public string $name,
        public ?string $roomCode,
    ) {
    }
}
