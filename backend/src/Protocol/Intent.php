<?php declare(strict_types=1);

namespace Mmo\Protocol;

final readonly class Intent
{
    /** @param array<string, mixed> $payload */
    public function __construct(
        public int $sequence,
        public string $name,
        public array $payload,
    ) {
    }
}
