<?php declare(strict_types=1);

namespace Mmo\Server;

use Mmo\Protocol\InputSequencer;

/**
 * The part of a client connection that room logic depends on.
 *
 * Room only ever reads a connection's identity and sequencing and asks it to
 * deliver a message. Extracting that into an interface lets the long-polling
 * path supply its own implementation, because a poll has no socket to write to,
 * while the WebSocket path keeps using ClientConnection unchanged.
 */
interface RoomConnection
{
    public function getId(): string;

    public function getPlayerId(): string;

    public function isConnected(): bool;

    public function inputSequencer(): InputSequencer;

    /** @param array<string, mixed> $message */
    public function send(array $message): bool;
}
