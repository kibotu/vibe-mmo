<?php declare(strict_types=1);

namespace Mmo\Server;

use Mmo\Game\FloorItem;
use Mmo\Game\Inventory;

/**
 * Builds the snapshot a polled client receives.
 *
 * This shape is a contract with the browser, not an internal detail. The client's
 * parser is strict: it rejects the whole snapshot when a field is missing, and it
 * requires the inventory to hold exactly Inventory::CAPACITY slots. An earlier
 * version of the polling path omitted the inventory, so every snapshot was
 * refused and the game never connected. Keeping the contract in one small,
 * testable class means a change to it has to be deliberate.
 *
 * The daemon's equivalent lives in Room::sendSnapshot, which streams to a socket
 * rather than returning a document.
 */
final class PollingSnapshot
{
    /**
     * @return array<string, mixed>
     */
    public function build(
        Room $room,
        string $playerPublicId,
        float $now,
        int $intentsApplied,
        bool $simulated,
    ): array {
        $actors = [];
        foreach ($room->pollingPlayerSnapshots() as $actor) {
            $actors[] = $actor;
        }
        foreach ($room->poringActors() as $poring) {
            if (!$poring->isDead()) {
                $actors[] = $poring->snapshot();
            }
        }

        // The caller's own inventory and input watermark are per-player. Sending
        // another player's would leak state, and a zero watermark would make the
        // client resend intents that already landed.
        $self = $room->playerActor($playerPublicId);

        return [
            'type' => 'snapshot',
            'tick' => $room->simulationTick(),
            'serverTime' => round($now, 3),
            'lastProcessedInput' => $self?->lastProcessedInput ?? 0,
            'intentsApplied' => $intentsApplied,
            'simulated' => $simulated,
            'actors' => $actors,
            'items' => array_map(
                static fn (FloorItem $item): array => $item->snapshot(),
                $room->floorItems(),
            ),
            'inventory' => $self?->inventory?->slots() ?? array_fill(0, Inventory::CAPACITY, null),
        ];
    }
}
