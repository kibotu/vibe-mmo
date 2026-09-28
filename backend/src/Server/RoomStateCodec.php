<?php declare(strict_types=1);

namespace Mmo\Server;

use Mmo\Game\Actor;
use Mmo\Game\FloorItem;
use Mmo\World\GridCell;

/**
 * Serialises the non-player part of a room to and from the room_state table.
 *
 * The WebSocket daemon keeps this in memory, where the fields are plain PHP
 * values. Persisting requires explicit encoding, so this class is the single
 * place that knows the on-disk shape. Everything is written as JSON scalars;
 * an unrecognised value is dropped rather than trusted, because a corrupt
 * document must not be able to resurrect an actor in an impossible state.
 *
 * A poring's spawn cell is deliberately not stored. It is readonly and already
 * derived from the room seed, so a room rebuilt from that seed has the same
 * spawn points.
 */
final class RoomStateCodec
{
    private const FORMAT = 1;

    /**
     * @return array{state: array<string, mixed>, tick: int}
     */
    public function encode(Room $room): array
    {
        $porings = [];
        foreach ($room->poringActors() as $poring) {
            $porings[$poring->id] = [
                'x' => $poring->x,
                'y' => $poring->y,
                'z' => $poring->z,
                'facing' => $poring->facing,
                'state' => $poring->state,
                'hp' => $poring->hp,
                'targetId' => $poring->targetId,
                'nextAttackAt' => $poring->nextAttackAt,
                'respawnAt' => $poring->respawnAt,
                'hurtUntil' => $poring->hurtUntil,
                'nextThinkAt' => $poring->nextThinkAt,
                'animationOffset' => $poring->animationOffset,
                'path' => array_map(
                    static fn (GridCell $cell): array => ['x' => $cell->x, 'z' => $cell->z],
                    $poring->path,
                ),
                'pathGoal' => $poring->pathGoal === null
                    ? null
                    : ['x' => $poring->pathGoal->x, 'z' => $poring->pathGoal->z],
            ];
        }

        $items = [];
        foreach ($room->floorItems() as $item) {
            $items[$item->id] = [
                'itemId' => $item->itemId,
                'quantity' => $item->quantity,
                'x' => $item->x,
                'y' => $item->y,
                'z' => $item->z,
                'bobOffset' => $item->bobOffset,
            ];
        }

        return [
            'state' => [
                'format' => self::FORMAT,
                'porings' => $porings,
                'items' => $items,
                'nextItemId' => $room->nextFloorItemId(),
                'eventId' => $room->lastEventId(),
            ],
            'tick' => $room->simulationTick(),
        ];
    }

    /**
     * Applies a stored document onto a freshly created room. A room built by
     * Room::create() already has porings at their spawn points, so this only
     * overwrites the mutable fields and adds floor items.
     *
     * @param array<string, mixed> $state
     */
    public function decodeInto(Room $room, array $state, int $tick): void
    {
        if (($state['format'] ?? null) !== self::FORMAT) {
            return;
        }

        foreach (($state['porings'] ?? []) as $id => $poringState) {
            $poring = $room->poringActors()[$id] ?? null;
            if ($poring === null || !is_array($poringState)) {
                continue;
            }
            $poring->x = $this->float($poringState, 'x', $poring->x);
            $poring->y = $this->float($poringState, 'y', $poring->y);
            $poring->z = $this->float($poringState, 'z', $poring->z);
            $poring->facing = $this->float($poringState, 'facing', $poring->facing);
            $poring->hp = (int) ($poringState['hp'] ?? $poring->hp);
            $poring->targetId = is_string($poringState['targetId'] ?? null) ? $poringState['targetId'] : null;
            $poring->nextAttackAt = $this->float($poringState, 'nextAttackAt', 0.0);
            $poring->respawnAt = $this->nullableFloat($poringState, 'respawnAt');
            $poring->hurtUntil = $this->nullableFloat($poringState, 'hurtUntil');
            $poring->nextThinkAt = $this->float($poringState, 'nextThinkAt', 0.0);
            $poring->animationOffset = $this->float($poringState, 'animationOffset', 0.0);

            $stateValue = $poringState['state'] ?? null;
            if (is_string($stateValue) && in_array($stateValue, [
                Actor::STATE_IDLE, Actor::STATE_WALK, Actor::STATE_ATTACK, Actor::STATE_HURT, Actor::STATE_DEAD,
            ], true)) {
                $poring->state = $stateValue;
            }

            $poring->path = [];
            foreach (($poringState['path'] ?? []) as $cell) {
                if (is_array($cell) && isset($cell['x'], $cell['z']) && is_numeric($cell['x']) && is_numeric($cell['z'])) {
                    $poring->path[] = new GridCell((int) $cell['x'], (int) $cell['z']);
                }
            }
            $goal = $poringState['pathGoal'] ?? null;
            $poring->pathGoal = is_array($goal) && isset($goal['x'], $goal['z']) && is_numeric($goal['x']) && is_numeric($goal['z'])
                ? new GridCell((int) $goal['x'], (int) $goal['z'])
                : null;
        }

        $room->clearFloorItems();
        foreach (($state['items'] ?? []) as $id => $itemState) {
            if (!is_string($id) || !is_array($itemState) || !is_string($itemState['itemId'] ?? null)) {
                continue;
            }
            $room->addFloorItem(new FloorItem(
                $id,
                $itemState['itemId'],
                max(0, (int) ($itemState['quantity'] ?? 1)),
                $this->float($itemState, 'x', 32.5),
                $this->float($itemState, 'y', 0.0),
                $this->float($itemState, 'z', 32.5),
                $this->float($itemState, 'bobOffset', 0.0),
            ));
        }

        $room->restoreCounters(
            max(1, (int) ($state['nextItemId'] ?? 1)),
            max(0, (int) ($state['eventId'] ?? 0)),
            $tick,
        );
    }

    /** @param array<string, mixed> $values */
    private function float(array $values, string $key, float $default): float
    {
        return isset($values[$key]) && is_numeric($values[$key]) ? (float) $values[$key] : $default;
    }

    /** @param array<string, mixed> $values */
    private function nullableFloat(array $values, string $key): ?float
    {
        return isset($values[$key]) && is_numeric($values[$key]) ? (float) $values[$key] : null;
    }
}
