<?php declare(strict_types=1);

namespace Mmo\Tests;

use Mmo\Game\Actor;
use Mmo\Game\FloorItem;
use Mmo\Game\GridCell;
use Mmo\Server\PollingSnapshot;
use Mmo\Server\Room;
use Mmo\Server\RoomConnection;
use Mmo\Server\RoomStateCodec;
use Mmo\Protocol\InputSequencer;
use Mmo\Protocol\Intent;
use PHPUnit\Framework\Attributes\CoversClass;
use PHPUnit\Framework\TestCase;

/**
 * A stand-in connection for room logic, so these tests need no socket.
 */
final class TestRoomConnection implements RoomConnection
{
    /** @var list<array<string, mixed>> */
    public array $sent = [];

    public function __construct(
        private readonly string $playerId,
        private readonly InputSequencer $sequencer = new InputSequencer(),
    ) {
    }

    public function getId(): string
    {
        return 'test-' . $this->playerId;
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
        return $this->sequencer;
    }

    public function send(array $message): bool
    {
        $this->sent[] = $message;

        return true;
    }
}

/**
 * The client's snapshot parser is strict: a missing field or a wrong slot count
 * makes it reject every snapshot and the game never connects. These tests pin
 * the shape the client depends on, so a future change to the snapshot cannot
 * break the client without a test failing here.
 */
#[CoversClass(Room::class)]
#[CoversClass(RoomStateCodec::class)]
#[CoversClass(PollingSnapshot::class)]
#[CoversClass(TestRoomConnection::class)]
final class RoomSnapshotTest extends TestCase
{
    private const SEED = 1337;

    public function testPolledSnapshotCarriesTheFieldsTheClientParserRequires(): void
    {
        $room = Room::create('payon', 'Payon Forest', self::SEED, 20);
        $room->presentForPolling($this->record('player-1'), 1000.0);

        // This is the document a browser actually receives. An earlier version
        // omitted inventory, the client rejected every snapshot, and the game
        // never connected, so the contract is pinned here.
        $snapshot = (new PollingSnapshot())->build($room, 'player-1', 1000.0, 1, true);

        self::assertSame('snapshot', $snapshot['type']);
        foreach (['tick', 'serverTime', 'lastProcessedInput', 'actors', 'items', 'inventory'] as $field) {
            self::assertArrayHasKey($field, $snapshot);
        }

        // The client requires exactly this many slots and refuses the snapshot
        // outright otherwise.
        self::assertCount(20, $snapshot['inventory']);
    }

    public function testPolledSnapshotReportsTheCallersOwnWatermarkNotAnotherPlayers(): void
    {
        $room = Room::create('payon', 'Payon Forest', self::SEED, 20);
        $mine = $room->presentForPolling($this->record('player-1'), 1000.0);
        $theirs = $room->presentForPolling($this->record('player-2'), 1000.0);

        $connection = new TestRoomConnection('player-1');
        $mine->lastProcessedInput = 9;
        $theirs->lastProcessedInput = 3;

        $snapshot = (new PollingSnapshot())->build($room, 'player-1', 1000.0, 0, true);

        // A watermark belonging to another player would make this client
        // discard or replay input.
        self::assertSame(9, $snapshot['lastProcessedInput']);
    }

    public function testDaemonSnapshotCarriesTheFieldsTheClientParserRequires(): void
    {
        $room = Room::create('payon', 'Payon Forest', self::SEED, 20);
        $connection = new TestRoomConnection('player-1');
        $actor = $room->presentForPolling($this->record('player-1'), 1000.0);

        $room->handleIntent($connection, new Intent(1, 'move', ['x' => 33, 'z' => 33]), ['x' => 33, 'z' => 33]);
        $room->tick(1000.0, 0.05);
        $room->sendSnapshot($connection, $room->simulationTick(), 1000.0);

        self::assertCount(1, $connection->sent);
        $snapshot = $connection->sent[0];

        self::assertSame('snapshot', $snapshot['type']);
        self::assertArrayHasKey('tick', $snapshot);
        self::assertArrayHasKey('serverTime', $snapshot);
        self::assertArrayHasKey('lastProcessedInput', $snapshot);
        self::assertArrayHasKey('actors', $snapshot);
        self::assertArrayHasKey('items', $snapshot);

        // The client requires exactly this many slots, and rejects the snapshot
        // outright otherwise.
        self::assertArrayHasKey('inventory', $snapshot);
        self::assertCount(20, $snapshot['inventory']);
    }

    public function testWatermarkAdvancesSoTheClientStopsResendingIntents(): void
    {
        $room = Room::create('payon', 'Payon Forest', self::SEED, 20);
        $connection = new TestRoomConnection('player-1');
        $actor = $room->presentForPolling($this->record('player-1'), 1000.0);

        // The daemon reports the sequencer, which the transport advances on
        // accept; the polled path reports the actor, which recordProcessedInput
        // advances. Both must move, or the client replays input forever.
        $intent = new Intent(5, 'move', ['x' => 33, 'z' => 33]);
        $connection->inputSequencer()->accept($intent);
        $room->handleIntent($connection, $intent, ['x' => 33, 'z' => 33]);
        $room->recordProcessedInput($connection, 5);
        $room->sendSnapshot($connection, $room->simulationTick(), 1000.0);

        self::assertSame(5, $connection->sent[0]['lastProcessedInput']);
        self::assertSame(5, $actor->lastProcessedInput);
    }

    public function testPersistedStateRoundTripsThroughTheCodec(): void
    {
        $codec = new RoomStateCodec();
        $room = Room::create('payon', 'Payon Forest', self::SEED, 20);
        $poring = array_values($room->poringActors())[0];
        $poring->hp = 31;
        $poring->x = 21.5;
        $poring->z = 24.25;
        $poring->state = Actor::STATE_WALK;
        $room->addFloorItem(new FloorItem('floor-9', 'jellopy', 2, 30.0, 0.0, 31.0, 0.5));
        // Tick first, then capture: a tick moves the poring, so encoding before
        // the tick would assert against a position that no longer exists.
        $room->tick(1000.0, 0.05);
        $expectedX = $poring->x;
        $expectedZ = $poring->z;

        $encoded = $codec->encode($room);

        // A room is rebuilt per request, so the decoded state must be identical
        // to what the previous request simulated.
        $rebuilt = Room::create('payon', 'Payon Forest', self::SEED, 20);
        $codec->decodeInto($rebuilt, $encoded['state'], $encoded['tick']);

        $restored = $rebuilt->poringActors()[$poring->id];
        self::assertNotNull($restored);
        self::assertSame(31, $restored->hp);
        self::assertSame($expectedX, $restored->x);
        self::assertSame($expectedZ, $restored->z);
        self::assertSame(Actor::STATE_WALK, $restored->state);

        self::assertCount(1, $rebuilt->floorItems());
        self::assertSame('jellopy', $rebuilt->floorItems()[0]->itemId);
        self::assertSame($room->simulationTick(), $rebuilt->simulationTick());
    }

    public function testDecodeIgnoresAnUnknownFormatRatherThanCorruptingTheRoom(): void
    {
        $codec = new RoomStateCodec();
        $room = Room::create('payon', 'Payon Forest', self::SEED, 20);
        $before = array_values($room->poringActors())[0]->hp;

        $codec->decodeInto($room, ['format' => 99, 'porings' => ['poring-0' => ['hp' => 1]]], 7);

        // A document from a future or corrupt writer is ignored, not half-applied.
        self::assertSame($before, array_values($room->poringActors())[0]->hp);
    }

    private function record(string $publicId): \Mmo\Database\PlayerRecord
    {
        return new \Mmo\Database\PlayerRecord(
            1,
            $publicId,
            'Tester',
            'payon',
            40,
            32.5,
            0.0,
            32.5,
            array_fill(0, 20, null),
            0,
            Actor::STATE_IDLE,
            null,
            0.0,
        );
    }
}
