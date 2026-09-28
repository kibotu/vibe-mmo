<?php declare(strict_types=1);

namespace Mmo\Server;

use Mmo\Protocol\InputSequencer;

/**
 * A RoomConnection for the long-polling path, where no socket exists.
 *
 * Messages are recorded rather than written, so a poll can return the events a
 * simulated step produced instead of pushing them to a live socket. Input
 * sequencing is the real InputSequencer, so intent ordering and deduplication
 * behave identically on both transports.
 */
final class PollingConnection implements RoomConnection
{
    /** @var list<array<string, mixed>> */
    private array $outbound = [];

    public function __construct(
        private readonly string $id,
        private readonly string $playerId,
        private readonly InputSequencer $inputSequencer,
    ) {
    }

    public function getId(): string
    {
        return $this->id;
    }

    public function getPlayerId(): string
    {
        return $this->playerId;
    }

    public function isConnected(): bool
    {
        return true;
    }

    public function inputSequencer(): InputSequencer
    {
        return $this->inputSequencer;
    }

    public function send(array $message): bool
    {
        $this->outbound[] = $message;

        return true;
    }

    /** @return list<array<string, mixed>> */
    public function drain(): array
    {
        $messages = $this->outbound;
        $this->outbound = [];

        return $messages;
    }
}
