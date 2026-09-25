<?php declare(strict_types=1);

namespace Mmo\Server;

use Amp\Websocket\WebsocketClient;
use Amp\Websocket\WebsocketClosedException;
use Mmo\Protocol\InputSequencer;

final class ClientConnection
{
    public bool $connected = true;
    public bool $superseded = false;
    public float $lastSeen;

    public function __construct(
        public readonly string $id,
        public readonly string $playerId,
        public readonly string $playerName,
        public readonly string $forwardedIp,
        public readonly string $roomCode,
        public readonly WebsocketClient $client,
        public readonly InputSequencer $inputSequencer,
        public readonly float $connectedAt,
        ?float $lastSeen = null,
        public int $inboundMessages = 0,
        public int $inboundBytes = 0,
        public int $outboundBytes = 0,
    ) {
        $this->lastSeen = $lastSeen ?? $connectedAt;
    }

    /** @param array<string, mixed> $message */
    public function send(array $message): bool
    {
        if (!$this->connected || $this->client->isClosed()) {
            return false;
        }
        $json = json_encode($message, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
        try {
            $this->client->sendText($json);
        } catch (WebsocketClosedException) {
            $this->connected = false;

            return false;
        }
        $this->outboundBytes += strlen($json);
        $this->lastSeen = microtime(true);

        return true;
    }

    public function markInbound(int $bytes): void
    {
        ++$this->inboundMessages;
        $this->inboundBytes += $bytes;
        $this->lastSeen = microtime(true);
    }

    /** @return array<string, bool|float|int|string|list<int>> */
    public function status(): array
    {
        return [
            'connectionId' => $this->id,
            'playerId' => $this->playerId,
            'playerName' => $this->playerName,
            'forwardedIp' => $this->forwardedIp,
            'room' => $this->roomCode,
            'connected' => $this->connected && !$this->client->isClosed(),
            'connectedAt' => $this->connectedAt,
            'lastSeen' => $this->lastSeen,
            'inboundMessages' => $this->inboundMessages,
            'inboundBytes' => $this->inboundBytes,
            'outboundBytes' => $this->outboundBytes,
        ];
    }
}
