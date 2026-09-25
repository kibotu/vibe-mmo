<?php declare(strict_types=1);

namespace Mmo\Protocol;

final class ProtocolException extends \InvalidArgumentException
{
    public function __construct(
        public readonly string $errorCode,
        string $message,
    ) {
        parent::__construct($message);
    }
}
