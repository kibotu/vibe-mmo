<?php declare(strict_types=1);

namespace Mmo\Game;

final class ItemCatalog
{
    /** @var array<string, array{name: string, stackable: bool, maxStack: int}> */
    private const ITEMS = [
        'jellopy' => ['name' => 'Jellopy', 'stackable' => true, 'maxStack' => 99],
        'empty_bottle' => ['name' => 'Empty Bottle', 'stackable' => true, 'maxStack' => 99],
        'apple' => ['name' => 'Apple', 'stackable' => true, 'maxStack' => 99],
        'sticky_mucus' => ['name' => 'Sticky Mucus', 'stackable' => true, 'maxStack' => 99],
        'knife' => ['name' => 'Knife [4]', 'stackable' => false, 'maxStack' => 1],
    ];

    /** @return array{name: string, stackable: bool, maxStack: int}|null */
    public static function definition(string $itemId): ?array
    {
        return self::ITEMS[$itemId] ?? null;
    }

    /** @return list<array{itemId: string|null, weight: float}> */
    public static function poringLootTable(): array
    {
        return [
            ['itemId' => 'jellopy', 'weight' => 0.65],
            ['itemId' => 'empty_bottle', 'weight' => 0.15],
            ['itemId' => 'apple', 'weight' => 0.10],
            ['itemId' => 'sticky_mucus', 'weight' => 0.04],
            ['itemId' => 'knife', 'weight' => 0.01],
            ['itemId' => null, 'weight' => 0.05],
        ];
    }
}
