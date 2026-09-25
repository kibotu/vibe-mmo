<?php declare(strict_types=1);

namespace Mmo\Server;

use Amp\Websocket\WebsocketClient;
use Mmo\Config\Config;
use Mmo\Database\PlayerRepository;
use Mmo\Database\RoomRepository;
use Mmo\Database\TicketIdentity;
use Mmo\Protocol\InputSequencer;
use Mmo\Protocol\Intent;
use Mmo\Protocol\IntentValidator;
use Mmo\Protocol\ProtocolException;
use Mmo\Support\Uuid;

final class RoomManager
{
    /** @var array<string, Room> */
    private array $rooms = [];
    /** @var array<string, ClientConnection> */
    private array $connections = [];
    private bool $paused = false;
    private int $totalAccepted = 0;
    private int $totalRejected = 0;
    private int $totalErrors = 0;
    private int $totalPersistenceWrites = 0;

    public function __construct(
        private readonly Config $config,
        private readonly PlayerRepository $players,
        private readonly RoomRepository $roomRepository,
        private readonly IntentValidator $intentValidator,
    ) {
    }

    public function connect(
        TicketIdentity $ticket,
        WebsocketClient $client,
        string $forwardedIp,
        float $now,
    ): ClientConnection {
        if (count($this->connections) >= $this->config->int('server.max_connections', 500)) {
            ++$this->totalRejected;
            throw new ProtocolException('server_full', 'The server is at capacity.');
        }

        $roomExists = isset($this->rooms[$ticket->roomCode]);
        if (!$roomExists && count($this->rooms) >= $this->config->int('server.max_rooms', 32)) {
            ++$this->totalRejected;
            throw new ProtocolException('room_limit', 'The server room limit has been reached.');
        }

        foreach ($this->connections as $existing) {
            if ($existing->playerId === $ticket->player->publicId && !$existing->client->isClosed()) {
                $existing->superseded = true;
                $existing->client->close(1000, 'Replaced by a newer connection');
            }
        }

        $room = $this->rooms[$ticket->roomCode] ?? Room::create(
            $ticket->roomCode,
            $ticket->roomName,
            $this->config->int('world.seed', 1337),
            min($ticket->roomMaxPlayers, $this->config->int('rooms.max_players', 100)),
        );
        $this->rooms[$room->code] = $room;
        $this->roomRepository->setStatus($room->code, 'active');

        $connection = new ClientConnection(
            Uuid::v4(),
            $ticket->player->publicId,
            $ticket->player->name,
            $this->sanitizeIp($forwardedIp),
            $room->code,
            $client,
            new InputSequencer($ticket->player->lastProcessedInput),
            $now,
        );
        $room->attach($ticket->player, $connection, $now);
        $this->connections[$connection->id] = $connection;
        ++$this->totalAccepted;
        $room->sendWelcome($connection, $now);
        $room->sendSnapshot($connection, $room->simulationTick(), $now);

        return $connection;
    }

    public function disconnect(string $connectionId, float $now): void
    {
        $connection = $this->connections[$connectionId] ?? null;
        if ($connection === null) {
            return;
        }
        unset($this->connections[$connectionId]);
        $this->rooms[$connection->roomCode]?->detach($connectionId, $now);
    }

    public function handleIntent(ClientConnection $connection, Intent $intent): void
    {
        if ($connection->superseded) {
            return;
        }
        try {
            $connection->inputSequencer->accept($intent);
            $room = $this->rooms[$connection->roomCode] ?? null;
            if ($room === null) {
                throw new ProtocolException('room_missing', 'The room is no longer available.');
            }
            $room->recordProcessedInput($connection, $intent->sequence);
            $payload = $this->intentValidator->validate($intent, $room->grid());
            $room->handleIntent($connection, $intent, $payload);
        } catch (ProtocolException $exception) {
            ++$this->totalErrors;
            $connection->send([
                'type' => 'error',
                'code' => $exception->errorCode,
                'message' => $exception->getMessage(),
            ]);
        }
    }

    public function tick(float $now, float $deltaSeconds): void
    {
        if ($this->paused) {
            return;
        }
        foreach ($this->rooms as $room) {
            if ($room->connectionCount() > 0) {
                $room->tick($now, $deltaSeconds);
            }
        }
    }

    public function sendSnapshots(float $now): void
    {
        foreach ($this->connections as $connection) {
            if (!$connection->connected || $connection->client->isClosed()) {
                $this->disconnect($connection->id, $now);
                continue;
            }
            $room = $this->rooms[$connection->roomCode] ?? null;
            if ($room !== null) {
                $room->sendSnapshot($connection, $room->simulationTick(), $now);
            }
        }
    }

    public function housekeeping(float $now): void
    {
        $grace = $this->config->int('rooms.disconnect_grace_seconds', 120);
        $emptyTtl = $this->config->int('rooms.empty_ttl_seconds', 900);
        foreach ($this->rooms as $code => $room) {
            $room->prune($now, $grace);
            if ($room->connectionCount() === 0 && ($room->emptySeconds($now) ?? 0.0) >= $emptyTtl) {
                unset($this->rooms[$code]);
            }
        }
    }

    public function persist(): int
    {
        $writes = 0;
        foreach ($this->rooms as $room) {
            $writes += $this->persistRoom($room);
        }

        return $writes;
    }

    public function flushAndPause(): int
    {
        $this->paused = true;
        $writes = $this->persist();
        foreach ($this->rooms as $room) {
            $this->roomRepository->setStatus($room->code, 'paused');
        }

        return $writes;
    }

    public function setPaused(bool $paused): void
    {
        $this->paused = $paused;
    }

    public function paused(): bool
    {
        return $this->paused;
    }

    public function disconnectAll(?string $connectionId = null): int
    {
        $count = 0;
        foreach ($this->connections as $id => $connection) {
            if ($connectionId !== null && $id !== $connectionId) {
                continue;
            }
            $connection->client->close(1000, 'Disconnected by administrator');
            $this->disconnect($id, microtime(true));
            ++$count;
        }

        return $count;
    }

    /** @return list<array<string, mixed>> */
    public function roomStatus(float $now): array
    {
        $rooms = [];
        foreach ($this->rooms as $room) {
            $status = $room->status($now);
            $status['state'] = $this->paused ? 'paused' : $status['state'];
            $status['tick'] = $room->simulationTick();
            $rooms[] = $status;
        }
        usort($rooms, static fn (array $left, array $right): int => strcmp((string) $left['code'], (string) $right['code']));

        return $rooms;
    }

    /** @return list<array<string, mixed>> */
    public function connectionStatus(): array
    {
        $statuses = [];
        foreach ($this->connections as $connection) {
            $statuses[] = $connection->status();
        }
        usort($statuses, static fn (array $left, array $right): int => strcmp((string) $left['connectionId'], (string) $right['connectionId']));

        return $statuses;
    }

    /** @return array<string, int> */
    public function totals(): array
    {
        $messages = 0;
        $bytesIn = 0;
        $bytesOut = 0;
        foreach ($this->connections as $connection) {
            $messages += $connection->inboundMessages;
            $bytesIn += $connection->inboundBytes;
            $bytesOut += $connection->outboundBytes;
        }

        return [
            'connectionsAccepted' => $this->totalAccepted,
            'connectionsRejected' => $this->totalRejected,
            'connectionsActive' => count($this->connections),
            'messagesRejected' => $this->totalErrors,
            'messagesIn' => $messages,
            'bytesIn' => $bytesIn,
            'bytesOut' => $bytesOut,
            'persistenceWrites' => $this->totalPersistenceWrites,
        ];
    }

    public function hasConnection(string $connectionId): bool
    {
        return isset($this->connections[$connectionId]);
    }

    private function persistRoom(Room $room): int
    {
        $count = $this->players->saveAll($room->playerSaves());
        $this->totalPersistenceWrites += $count;

        return $count;
    }

    private function sanitizeIp(string $ip): string
    {
        return filter_var($ip, FILTER_VALIDATE_IP) !== false ? $ip : '0.0.0.0';
    }
}
