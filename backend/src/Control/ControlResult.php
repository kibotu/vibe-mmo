<?php declare(strict_types=1);

namespace Mmo\Control;

final readonly class ControlResult
{
    public function __construct(
        public bool $ok,
        public string $message,
        public ?string $command = null,
    ) {
    }
}
