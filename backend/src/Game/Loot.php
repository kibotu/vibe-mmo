<?php declare(strict_types=1);

namespace Mmo\Game;

use Mmo\World\Mulberry32;

final class Loot
{
    public static function rollPoring(Mulberry32 $random): ?string
    {
        $target = $random->next();
        $cursor = 0.0;
        foreach (ItemCatalog::poringLootTable() as $entry) {
            $cursor += $entry['weight'];
            if ($target < $cursor) {
                return $entry['itemId'];
            }
        }

        return null;
    }
}
