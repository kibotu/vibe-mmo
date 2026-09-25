<?php declare(strict_types=1);

namespace Mmo\Database;

final readonly class CreatedGuest
{
    public function __construct(
        public GuestIdentity $identity,
        public string $selector,
        public string $validator,
    ) {
    }
}
