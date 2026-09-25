<?php declare(strict_types=1);

namespace Mmo\Server;

use Mmo\Database\PlayerRecord;
use Mmo\Database\PlayerSave;
use Mmo\Game\Actor;
use Mmo\Game\FloorItem;
use Mmo\Game\Inventory;
use Mmo\Game\ItemCatalog;
use Mmo\Game\Loot;
use Mmo\Game\RoomEvent;
use Mmo\Protocol\ProtocolException;
use Mmo\World\GridCell;
use Mmo\World\Mulberry32;
use Mmo\World\PathFinder;
use Mmo\World\WorldGenerator;
use Mmo\World\WorldGrid;

final class Room
{
    public const TICK_RATE = 20;
    public const SNAPSHOT_RATE = 10;
    public const PLAYER_SPEED = 4.0;
    public const PORING_SPEED = 1.15;
    public const PLAYER_ATTACK = 12;
    public const ATTACK_RANGE = 1.5;
    public const ATTACK_INTERVAL = 0.8;
    public const ATTACK_IMPACT_DELAY = 0.18;
    public const RESPAWN_DELAY = 5.0;
    public const APPLE_HEAL = 15;

    /** @var array<string, Actor> */
    private array $actors = [];
    /** @var array<string, ClientConnection> */
    private array $connections = [];
    /** @var array<string, FloorItem> */
    private array $items = [];
    private int $eventId = 0;
    private int $nextItemId = 1;
    private int $simulationTick = 0;
    private ?float $emptySince = null;

    public function __construct(
        public readonly string $code,
        public readonly string $name,
        public readonly int $seed,
        public readonly int $maxPlayers,
        private readonly WorldGenerator $generated,
        private readonly Mulberry32 $random,
        private readonly PathFinder $pathFinder,
    ) {
        foreach ($generated->spawnCells as $index => $cell) {
            $position = $generated->grid->cellCenter($cell);
            $poring = new Actor(
                id: 'poring-' . $index,
                kind: Actor::KIND_PORING,
                name: 'Poring',
                x: $position['x'],
                y: $position['y'],
                z: $position['z'],
                facing: 0.0,
                state: Actor::STATE_IDLE,
                hp: 50,
                maxHp: 50,
                attack: 6,
                defense: 0,
                spawnCell: $cell,
            );
            $poring->nextThinkAt = $this->random->range(1.0, 4.0);
            $poring->animationOffset = $this->random->range(0.0, 10.0);
            $poring->nextThinkAt = $this->random->range(0.5, 3.5);
            $this->actors[$poring->id] = $poring;
        }
    }

    public static function create(
        string $code,
        string $name,
        int $seed,
        int $maxPlayers,
    ): self {
        $random = new Mulberry32($seed);

        return new self(
            $code,
            $name,
            $seed,
            $maxPlayers,
            WorldGenerator::generateWithRandom($random),
            $random,
            new PathFinder(),
        );
    }

    public function grid(): WorldGrid
    {
        return $this->generated->grid;
    }

    public function tickCount(): int
    {
        return $this->simulationTick;
    }

    public function simulationTick(): int
    {
        return $this->simulationTick;
    }

    public function connectionCount(): int
    {
        return count($this->connections);
    }

    public function retainedPlayerCount(): int
    {
        $count = 0;
        foreach ($this->actors as $actor) {
            if ($actor->isPlayer() && !$actor->connected) {
                ++$count;
            }
        }

        return $count;
    }

    public function hasConnectionForPlayer(string $playerId): bool
    {
        foreach ($this->connections as $connection) {
            if ($connection->playerId === $playerId) {
                return true;
            }
        }

        return false;
    }

    public function attach(PlayerRecord $record, ClientConnection $connection, float $now): Actor
    {
        if ($this->connectionCount() >= $this->maxPlayers
            && !$this->hasConnectionForPlayer($record->publicId)
            && !isset($this->actors[$record->publicId])
        ) {
            throw new ProtocolException('room_full', 'The room is full.');
        }

        $actor = $this->actors[$record->publicId] ?? $this->playerFromRecord($record, $now);
        $actor->connected = true;
        $actor->disconnectedAt = null;
        $this->connections[$connection->id] = $connection;
        if ($this->emptySince !== null) {
            $this->emptySince = null;
        }

        return $actor;
    }

    public function detach(string $connectionId, float $now): void
    {
        $connection = $this->connections[$connectionId] ?? null;
        if ($connection === null) {
            return;
        }
        unset($this->connections[$connectionId]);
        $connection->connected = false;
        $actor = $this->actors[$connection->playerId] ?? null;
        if ($actor !== null && !$this->hasConnectionForPlayer($connection->playerId)) {
            $actor->connected = false;
            $actor->disconnectedAt = $now;
        }
        if ($this->connections === [] && $this->emptySince === null) {
            $this->emptySince = $now;
        }
    }

    public function emptySeconds(float $now): ?float
    {
        return $this->emptySince === null ? null : max(0.0, $now - $this->emptySince);
    }

    /** @return list<PlayerSave> */
    public function playerSaves(): array
    {
        $saves = [];
        foreach ($this->actors as $actor) {
            if (!$actor->isPlayer() || $actor->databaseId === null || $actor->inventory === null) {
                continue;
            }
            $saves[] = new PlayerSave(
                $actor->databaseId,
                $actor->hp,
                $actor->x,
                $actor->y,
                $actor->z,
                $actor->inventory->slots(),
                $actor->lastProcessedInput,
                $actor->state,
                $actor->targetId,
                $actor->nextAttackAt,
            );
        }

        return $saves;
    }

    /** Remove disconnected actors only after their reconnect grace has elapsed. */
    public function prune(float $now, int $graceSeconds): void
    {
        foreach ($this->actors as $id => $actor) {
            if ($actor->isPlayer()
                && !$actor->connected
                && $actor->disconnectedAt !== null
                && $now - $actor->disconnectedAt >= $graceSeconds
            ) {
                unset($this->actors[$id]);
            }
        }
    }

    public function sendWelcome(ClientConnection $connection, float $serverTime): void
    {
        $connection->send([
            'type' => 'welcome',
            'connectionId' => $connection->id,
            'playerId' => $connection->playerId,
            'room' => ['code' => $this->code, 'name' => $this->name],
            'seed' => $this->seed,
            'tickRate' => self::TICK_RATE,
            'snapshotRate' => self::SNAPSHOT_RATE,
            'interpolationMs' => 100,
            'lastProcessedInput' => $connection->inputSequencer->lastProcessed(),
            'serverTime' => $serverTime,
        ]);
    }

    public function sendSnapshot(ClientConnection $connection, int $tick, float $serverTime): void
    {
        $actor = $this->actors[$connection->playerId] ?? null;
        $actorSnapshots = [];
        foreach ($this->actors as $candidate) {
            if ($candidate->isPlayer() && !$candidate->connected) {
                continue;
            }
            $actorSnapshots[] = $candidate->snapshot();
        }
        $itemSnapshots = array_map(
            static fn (FloorItem $item): array => $item->snapshot(),
            array_values($this->items),
        );
        $connection->send([
            'type' => 'snapshot',
            'tick' => $tick,
            'serverTime' => $serverTime,
            'lastProcessedInput' => $connection->inputSequencer->lastProcessed(),
            'actors' => $actorSnapshots,
            'items' => $itemSnapshots,
            'inventory' => $actor?->inventory?->slots() ?? array_fill(0, Inventory::CAPACITY, null),
        ]);
    }

    public function recordProcessedInput(ClientConnection $connection, int $sequence): void
    {
        $actor = $this->actors[$connection->playerId] ?? null;
        if ($actor !== null && $actor->isPlayer() && $sequence > $actor->lastProcessedInput) {
            $actor->lastProcessedInput = $sequence;
        }
    }

    /** @param array<string, int|string> $payload */
    public function handleIntent(ClientConnection $connection, \Mmo\Protocol\Intent $intent, array $payload): void
    {
        $actor = $this->actors[$connection->playerId] ?? null;
        if ($actor === null || !$actor->isPlayer() || !$actor->connected) {
            throw new ProtocolException('not_connected', 'The player is not attached to this room.');
        }
        $actor->lastProcessedInput = $intent->sequence;
        if ($actor->isDead()) {
            throw new ProtocolException('player_dead', 'The player cannot act while dead.');
        }

        match ($intent->name) {
            'move' => $this->move($actor, (int) $payload['x'], (int) $payload['z']),
            'target' => $this->target($actor, (string) $payload['targetId']),
            'attack' => $this->attack($actor, isset($payload['targetId']) ? (string) $payload['targetId'] : null, microtime(true)),
            'pickup' => $this->pickup($actor, (string) $payload['itemId']),
            'use_item' => $this->useItem($actor),
            default => throw new ProtocolException('unknown_intent', 'The intent type is not supported.'),
        };
    }

    public function tick(float $now, float $deltaSeconds): void
    {
        ++$this->simulationTick;
        foreach ($this->actors as $actor) {
            if ($actor->isPlayer()) {
                $this->updatePlayer($actor, $now, $deltaSeconds);
            } else {
                $this->updatePoring($actor, $now, $deltaSeconds);
            }
        }
    }

    /** @return array<string, mixed> */
    public function status(float $now): array
    {
        return [
            'code' => $this->code,
            'name' => $this->name,
            'state' => $this->connections === [] ? 'paused' : 'running',
            'connectedPlayers' => $this->connectionCount(),
            'retainedPlayers' => $this->retainedPlayerCount(),
            'maxPlayers' => $this->maxPlayers,
            'actors' => count($this->actors),
            'items' => count($this->items),
            'emptySeconds' => $this->emptySeconds($now),
        ];
    }

    private function playerFromRecord(PlayerRecord $record, float $now): Actor
    {
        $grid = $this->generated->grid;
        $valid = is_finite($record->x)
            && is_finite($record->y)
            && is_finite($record->z)
            && $record->x >= 0.0
            && $record->x < $grid->width()
            && $record->z >= 0.0
            && $record->z < $grid->height()
            && $grid->isWalkable($grid->cellAt($record->x, $record->z)->x, $grid->cellAt($record->x, $record->z)->z);
        $spawn = new GridCell(32, 32);
        $position = $valid
            ? [
                'x' => $record->x,
                'y' => $grid->heightAt($record->x, $record->z),
                'z' => $record->z,
            ]
            : $grid->cellCenter($spawn);
        $state = in_array($record->state, [
            Actor::STATE_IDLE,
            Actor::STATE_WALK,
            Actor::STATE_ATTACK,
            Actor::STATE_HURT,
            Actor::STATE_DEAD,
        ], true) ? $record->state : Actor::STATE_IDLE;
        $hp = max(0, min(40, $record->hp));
        if ($hp === 0) {
            $state = Actor::STATE_DEAD;
        } elseif (in_array($state, [Actor::STATE_WALK, Actor::STATE_ATTACK, Actor::STATE_HURT], true)) {
            $state = Actor::STATE_IDLE;
        }
        $actor = new Actor(
            id: $record->publicId,
            kind: Actor::KIND_PLAYER,
            name: $record->name,
            x: $position['x'],
            y: $position['y'],
            z: $position['z'],
            facing: M_PI,
            state: $state,
            hp: $hp,
            maxHp: 40,
            attack: self::PLAYER_ATTACK,
            defense: 0,
            spawnCell: $spawn,
            targetId: $record->targetId,
            nextAttackAt: $record->nextAttackAt,
        );
        $actor->databaseId = $record->databaseId;
        $actor->inventory = Inventory::fromSlots($record->inventory);
        $actor->lastProcessedInput = $record->lastProcessedInput;
        if ($actor->isDead()) {
            $actor->respawnAt = $now + self::RESPAWN_DELAY;
        }
        $this->actors[$actor->id] = $actor;

        return $actor;
    }

    private function updatePlayer(Actor $player, float $now, float $deltaSeconds): void
    {
        if (!$player->connected) {
            return;
        }
        if ($player->isDead()) {
            if ($player->respawnAt !== null && $now >= $player->respawnAt) {
                $this->respawnPlayer($player);
            }

            return;
        }
        if ($player->hurtUntil !== null && $now >= $player->hurtUntil) {
            $player->hurtUntil = null;
            if ($player->state === Actor::STATE_HURT) {
                $player->state = Actor::STATE_IDLE;
            }
        }

        $target = $player->targetId !== null ? ($this->actors[$player->targetId] ?? null) : null;
        if ($target === null || $target->isDead() || $target->id === $player->id) {
            $player->targetId = null;
            $target = null;
        }
        if ($target !== null) {
            $this->pursueTarget($player, $target, $now, $deltaSeconds);
        } else {
            $this->moveActor($player, $deltaSeconds, self::PLAYER_SPEED);
        }

        if ($player->attackImpactAt !== null && $now >= $player->attackImpactAt) {
            $this->resolveAttack($player, $now);
        }
    }

    private function updatePoring(Actor $poring, float $now, float $deltaSeconds): void
    {
        if ($poring->isDead()) {
            if ($poring->respawnAt !== null && $now >= $poring->respawnAt) {
                $this->respawnPoring($poring, $now);
            }

            return;
        }
        if ($poring->hurtUntil !== null) {
            if ($now < $poring->hurtUntil) {
                $poring->state = Actor::STATE_HURT;

                return;
            }
            $poring->hurtUntil = null;
            $poring->state = Actor::STATE_IDLE;
        }

        $target = $poring->targetId !== null ? ($this->actors[$poring->targetId] ?? null) : null;
        if ($target !== null && (!$target->isPlayer() || !$target->connected || $target->isDead() || $poring->distanceTo($target) > 10.0)) {
            $poring->targetId = null;
            $target = null;
        }
        if ($target === null) {
            foreach ($this->actors as $candidate) {
                if (!$candidate->isPlayer() || !$candidate->connected || $candidate->isDead()) {
                    continue;
                }
                if ($poring->distanceTo($candidate) <= 4.0) {
                    $poring->targetId = $candidate->id;
                    $target = $candidate;
                    break;
                }
            }
        }
        if ($target !== null) {
            $this->pursueTarget($poring, $target, $now, $deltaSeconds);
        } elseif ($poring->path === [] && $now >= $poring->nextThinkAt) {
            $this->choosePoringWander($poring, $now);
            $this->moveActor($poring, $deltaSeconds, self::PORING_SPEED);
        } else {
            $this->moveActor($poring, $deltaSeconds, self::PORING_SPEED);
        }

        if ($poring->attackImpactAt !== null && $now >= $poring->attackImpactAt) {
            $this->resolveAttack($poring, $now);
        }
    }

    private function pursueTarget(Actor $attacker, Actor $target, float $now, float $deltaSeconds): void
    {
        $distance = $attacker->distanceTo($target);
        if ($distance <= self::ATTACK_RANGE) {
            $attacker->path = [];
            $attacker->pathGoal = null;
            $attacker->facing = atan2($target->x - $attacker->x, $target->z - $attacker->z);
            if ($attacker->state !== Actor::STATE_ATTACK
                && $attacker->state !== Actor::STATE_HURT
                && $now >= $attacker->nextAttackAt
            ) {
                $this->beginAttack($attacker, $target->id, $now);
            } elseif ($attacker->state !== Actor::STATE_ATTACK && $attacker->state !== Actor::STATE_HURT) {
                $attacker->state = Actor::STATE_IDLE;
            }
        } else {
            if ($attacker->nextPathRefreshAt <= $now || $attacker->pathGoal === null) {
                $this->pathToTarget($attacker, $target, $now);
            }
            $this->moveActor($attacker, $deltaSeconds, $attacker->isPlayer() ? self::PLAYER_SPEED : self::PORING_SPEED);
        }
    }

    private function pathToTarget(Actor $attacker, Actor $target, float $now): void
    {
        $grid = $this->generated->grid;
        $start = $grid->cellAt($attacker->x, $attacker->z);
        $targetCell = $grid->cellAt($target->x, $target->z);
        $candidates = [];
        for ($dz = -1; $dz <= 1; ++$dz) {
            for ($dx = -1; $dx <= 1; ++$dx) {
                $candidate = new GridCell($targetCell->x + $dx, $targetCell->z + $dz);
                if ($grid->isWalkable($candidate->x, $candidate->z)
                    && hypot($candidate->x - $targetCell->x, $candidate->z - $targetCell->z) <= self::ATTACK_RANGE
                ) {
                    $candidates[] = $candidate;
                }
            }
        }
        usort($candidates, static fn (GridCell $left, GridCell $right): int =>
            hypot($left->x - $start->x, $left->z - $start->z) <=> hypot($right->x - $start->x, $right->z - $start->z));

        foreach ($candidates as $candidate) {
            $path = $this->pathFinder->find($grid, $start, $candidate);
            if ($path !== [] || ($candidate->x === $start->x && $candidate->z === $start->z)) {
                $attacker->path = $path;
                $attacker->pathGoal = $candidate;
                $attacker->nextPathRefreshAt = $now + 0.35;

                return;
            }
        }
    }

    private function move(Actor $actor, int $x, int $z): void
    {
        $actor->targetId = null;
        $actor->attackTargetId = null;
        $actor->attackImpactAt = null;
        if ($actor->state === Actor::STATE_ATTACK) {
            $actor->state = Actor::STATE_IDLE;
        }
        $actor->path = $this->pathFinder->find(
            $this->generated->grid,
            $this->generated->grid->cellAt($actor->x, $actor->z),
            new GridCell($x, $z),
        );
        $actor->pathGoal = new GridCell($x, $z);
    }

    private function target(Actor $actor, string $targetId): void
    {
        $target = $this->actors[$targetId] ?? null;
        if ($target === null || $target->id === $actor->id || $target->isDead() || ($target->isPlayer() && !$target->connected)) {
            throw new ProtocolException('invalid_target', 'The target is not available.');
        }
        $actor->targetId = $targetId;
        $actor->attackTargetId = null;
        $actor->attackImpactAt = null;
        if ($actor->state === Actor::STATE_ATTACK) {
            $actor->state = Actor::STATE_IDLE;
        }
        $actor->path = [];
        $actor->pathGoal = null;
        $actor->nextPathRefreshAt = 0.0;
        $this->addEvent('message', $actor->id, null, 'Targeting ' . $target->name . '.', null);
    }

    private function attack(Actor $actor, ?string $targetId, float $now): void
    {
        if ($targetId !== null) {
            $this->target($actor, $targetId);
        }
        $target = $actor->targetId !== null ? ($this->actors[$actor->targetId] ?? null) : null;
        if ($target === null || $target->isDead()) {
            throw new ProtocolException('no_target', 'Select a living target first.');
        }
        $this->pursueTarget($actor, $target, $now, 0.0);
    }

    private function beginAttack(Actor $actor, string $targetId, float $now): void
    {
        $actor->state = Actor::STATE_ATTACK;
        $actor->attackTargetId = $targetId;
        $actor->attackImpactAt = $now + self::ATTACK_IMPACT_DELAY;
        $actor->nextAttackAt = $now + self::ATTACK_INTERVAL;
    }

    private function resolveAttack(Actor $attacker, float $now): void
    {
        $targetId = $attacker->attackTargetId;
        $attacker->attackImpactAt = null;
        $attacker->attackTargetId = null;
        $target = $targetId !== null ? ($this->actors[$targetId] ?? null) : null;
        if ($target === null || $target->isDead() || $attacker->distanceTo($target) > self::ATTACK_RANGE + 0.25) {
            $attacker->state = Actor::STATE_IDLE;

            return;
        }

        $variance = $this->random->range(0.9, 1.1);
        $damage = max(1, (int) round(($attacker->attack - $target->defense * 0.5) * $variance));
        $target->hp = max(0, $target->hp - $damage);
        $target->hurtUntil = $now + 0.2;
        $target->state = Actor::STATE_HURT;
        $target->nextThinkAt = max($target->nextThinkAt, $now + 0.5);
        $this->addEvent('damage', $target->id, $damage, $target->name . ' takes ' . $damage . ' damage.', null);

        if ($target->hp === 0) {
            $this->kill($target, $attacker, $now);
        }
        $attacker->state = Actor::STATE_IDLE;
    }

    private function kill(Actor $target, Actor $killer, float $now): void
    {
        if ($target->isDead()) {
            return;
        }
        $target->hp = 0;
        $target->state = Actor::STATE_DEAD;
        $target->respawnAt = $now + self::RESPAWN_DELAY;
        $target->path = [];
        $target->pathGoal = null;
        $target->targetId = null;
        $target->attackImpactAt = null;
        $target->attackTargetId = null;
        $this->addEvent('death', $target->id, null, $target->name . ' was defeated.', null);
        if ($killer->targetId === $target->id) {
            $killer->targetId = null;
        }
        if ($target->isPlayer()) {
            return;
        }

        $itemId = Loot::rollPoring($this->random);
        if ($itemId !== null) {
            $dropId = 'floor-' . $this->nextItemId++;
            $this->items[$dropId] = new FloorItem(
                $dropId,
                $itemId,
                1,
                $target->x,
                $this->generated->grid->heightAt($target->x, $target->z),
                $target->z,
                $this->random->range(0.0, M_PI * 2.0),
            );
            $name = ItemCatalog::definition($itemId)['name'] ?? $itemId;
            $this->addEvent('loot', $target->id, null, $name . ' dropped.', $itemId);
        } else {
            $this->addEvent('loot', $target->id, null, 'The Poring left nothing behind.', null);
        }
    }

    private function respawnPlayer(Actor $player): void
    {
        $position = $this->generated->grid->cellCenter($player->spawnCell);
        $player->x = $position['x'];
        $player->y = $position['y'];
        $player->z = $position['z'];
        $player->hp = $player->maxHp;
        $player->state = Actor::STATE_IDLE;
        $player->targetId = null;
        $player->path = [];
        $player->pathGoal = null;
        $player->respawnAt = null;
        $this->addEvent('respawn', $player->id, null, $player->name . ' respawned.', null);
    }

    private function respawnPoring(Actor $poring, float $now): void
    {
        $position = $this->generated->grid->cellCenter($poring->spawnCell);
        $poring->x = $position['x'];
        $poring->y = $position['y'];
        $poring->z = $position['z'];
        $poring->hp = $poring->maxHp;
        $poring->state = Actor::STATE_IDLE;
        $poring->targetId = null;
        $poring->path = [];
        $poring->pathGoal = null;
        $poring->respawnAt = null;
        $poring->nextThinkAt = $now + $this->random->range(0.5, 2.5);
        $this->addEvent('respawn', $poring->id, null, 'A Poring respawned.', null);
    }

    private function choosePoringWander(Actor $poring, float $now): void
    {
        $origin = $this->generated->grid->cellAt($poring->x, $poring->z);
        $destination = new GridCell($origin->x, $origin->z);
        for ($attempt = 0; $attempt < 8; ++$attempt) {
            $candidate = new GridCell(
                $origin->x + $this->random->int(-5, 5),
                $origin->z + $this->random->int(-5, 5),
            );
            if ($this->generated->grid->isWalkable($candidate->x, $candidate->z)) {
                $destination = $candidate;
                break;
            }
        }
        $poring->path = $this->pathFinder->find(
            $this->generated->grid,
            $origin,
            $destination,
        );
        $poring->pathGoal = $destination;
        $poring->nextThinkAt = $now + $this->random->range(1.4, 4.2);
    }

    private function moveActor(Actor $actor, float $deltaSeconds, float $speed): void
    {
        $next = $actor->path[0] ?? null;
        if ($next === null) {
            return;
        }
        $destination = $this->generated->grid->cellCenter($next);
        $dx = $destination['x'] - $actor->x;
        $dz = $destination['z'] - $actor->z;
        $distance = hypot($dx, $dz);
        if ($distance <= 0.0001) {
            array_shift($actor->path);
            if ($actor->path === []) {
                $actor->state = Actor::STATE_IDLE;
            }

            return;
        }

        $actor->facing = atan2($dx, $dz);
        $actor->state = Actor::STATE_WALK;
        $step = $speed * $deltaSeconds;
        if ($step >= $distance) {
            $actor->x = $destination['x'];
            $actor->y = $destination['y'];
            $actor->z = $destination['z'];
            array_shift($actor->path);
        } else {
            $ratio = $step / $distance;
            $actor->x += $dx * $ratio;
            $actor->z += $dz * $ratio;
            $actor->y += ($destination['y'] - $actor->y) * min(1.0, $ratio * 4.0);
        }
        if ($actor->path === []) {
            $actor->state = Actor::STATE_IDLE;
        }
    }

    private function pickup(Actor $player, string $itemId): void
    {
        $item = null;
        foreach ($this->items as $candidate) {
            if ($candidate->itemId === $itemId
                && hypot($candidate->x - $player->x, $candidate->z - $player->z) <= 1.25
            ) {
                $item = $candidate;
                break;
            }
        }
        if ($item === null || $player->inventory === null) {
            throw new ProtocolException('item_not_nearby', 'That item is not nearby.');
        }
        $remaining = $player->inventory->add($item->itemId, $item->quantity);
        if ($remaining === $item->quantity) {
            $this->addEvent('message', $player->id, null, 'Inventory full; the item remains on the ground.', null);

            return;
        }
        $item->quantity = $remaining;
        $name = ItemCatalog::definition($item->itemId)['name'] ?? $item->itemId;
        $this->addEvent('loot', $player->id, null, 'Picked up ' . $name . '.', $item->itemId);
        if ($remaining === 0) {
            unset($this->items[$item->id]);
        }
    }

    private function useItem(Actor $player): void
    {
        if ($player->inventory === null) {
            throw new ProtocolException('item_not_usable', 'No usable item was supplied.');
        }
        if ($player->hp >= $player->maxHp) {
            throw new ProtocolException('already_healthy', 'Health is already full.');
        }
        if (!$player->inventory->remove('apple', 1)) {
            throw new ProtocolException('item_missing', 'You have no Apple.');
        }
        $healed = min(self::APPLE_HEAL, $player->maxHp - $player->hp);
        $player->hp += $healed;
        $this->addEvent('heal', $player->id, $healed, 'Recovered ' . $healed . ' HP.', 'apple');
    }

    private function addEvent(
        string $kind,
        ?string $actorId,
        ?int $amount,
        string $text,
        ?string $itemId,
    ): void {
        $event = new RoomEvent(++$this->eventId, $kind, $actorId, $amount, $text, $itemId);
        foreach ($this->connections as $connection) {
            $connection->send(['type' => 'event', 'event' => $event->snapshot()]);
        }
    }
}
