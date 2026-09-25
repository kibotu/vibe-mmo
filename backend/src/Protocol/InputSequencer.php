<?php declare(strict_types=1);

namespace Mmo\Protocol;

final class InputSequencer
{
    public function __construct(private int $lastProcessed = 0)
    {
    }

    public function accept(Intent $intent): void
    {
        if ($intent->sequence <= $this->lastProcessed) {
            throw new ProtocolException('stale_sequence', 'The intent sequence must increase monotonically.');
        }
        $this->lastProcessed = $intent->sequence;
    }

    public function lastProcessed(): int
    {
        return $this->lastProcessed;
    }
}
