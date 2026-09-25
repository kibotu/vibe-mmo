<?php declare(strict_types=1);

namespace Mmo\Tests;

use Mmo\Game\Inventory;
use PHPUnit\Framework\Attributes\CoversClass;
use PHPUnit\Framework\TestCase;

#[CoversClass(Inventory::class)]
final class InventoryTest extends TestCase
{
    public function testKeepsExactlyTwentyAuthoritativeSlots(): void
    {
        $inventory = new Inventory();
        self::assertCount(20, $inventory->slots());
        self::assertSame(0, $inventory->add('knife', 20));
        self::assertSame(1, $inventory->add('apple', 1));
        self::assertCount(20, array_filter($inventory->slots()));
    }

    public function testStacksAndRemovesWithoutPartialConsumption(): void
    {
        $inventory = new Inventory();
        self::assertSame(0, $inventory->add('jellopy', 101));
        self::assertSame(101, $inventory->count('jellopy'));
        self::assertTrue($inventory->remove('jellopy'));
        self::assertSame(100, $inventory->count('jellopy'));
        self::assertFalse($inventory->remove('jellopy', 101));
        self::assertSame(100, $inventory->count('jellopy'));
    }

    public function testRejectsInvalidQuantities(): void
    {
        $inventory = new Inventory();
        self::assertSame(0, $inventory->add('apple', 0));
        self::assertFalse($inventory->remove('apple', -1));
        self::assertSame(0, $inventory->count('apple'));
    }
}
