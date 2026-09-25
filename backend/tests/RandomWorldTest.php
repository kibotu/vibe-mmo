<?php declare(strict_types=1);

namespace Mmo\Tests;

use Mmo\World\Mulberry32;
use Mmo\World\PathFinder;
use Mmo\World\WorldGenerator;
use PHPUnit\Framework\Attributes\CoversClass;
use PHPUnit\Framework\TestCase;

#[CoversClass(Mulberry32::class)]
#[CoversClass(WorldGenerator::class)]
#[CoversClass(PathFinder::class)]
final class RandomWorldTest extends TestCase
{
    public function testMulberry32MatchesTheFrontendSequence(): void
    {
        $random = new Mulberry32(1337);
        $expected = [
            0.1844118325971067,
            0.18998925131745636,
            0.8104719922412187,
            0.6437488221563399,
        ];

        foreach ($expected as $value) {
            self::assertEqualsWithDelta($value, $random->next(), 1.0e-15);
        }
    }

    public function testWorldMapPropsAndSpawnCellsAreReproducible(): void
    {
        $first = WorldGenerator::generate(1337);
        $second = WorldGenerator::generate(1337);
        $expectedSpawns = [
            [24, 30], [35, 21], [26, 31], [44, 26],
            [42, 26], [37, 22], [19, 29], [44, 25],
            [36, 23], [19, 35], [38, 34], [39, 31],
            [30, 25], [36, 19], [23, 23], [34, 19],
        ];

        self::assertSame($first->checksum(), $second->checksum());
        self::assertSame('57aaf804a8c25820980b6c3d348d4e825951abaec511b5354c0940d040bdd1a0', $first->checksum());
        self::assertSame($expectedSpawns, array_map(
            static fn ($cell): array => [$cell->x, $cell->z],
            $first->spawnCells,
        ));
        self::assertCount(78, $first->trees);
        self::assertCount(26, $first->rocks);
        self::assertCount(34, $first->mushrooms);
        self::assertFalse($first->grid->isWalkable(20, 3));
        self::assertFalse($first->grid->isWalkable(29, 57));
        self::assertTrue($first->grid->isPassable(32, 44));
        self::assertEqualsWithDelta(0.16, $first->grid->heightAt(32.5, 44.5), 0.000001);
    }

    public function testPathfindingNeverCutsADiagonalCorner(): void
    {
        $grid = WorldGenerator::generate(1337)->grid;
        $path = (new PathFinder())->find(
            $grid,
            new \Mmo\World\GridCell(18, 18),
            new \Mmo\World\GridCell(27, 27),
        );
        self::assertNotEmpty($path);

        $previous = new \Mmo\World\GridCell(18, 18);
        foreach ($path as $cell) {
            $dx = $cell->x - $previous->x;
            $dz = $cell->z - $previous->z;
            self::assertLessThanOrEqual(1, abs($dx));
            self::assertLessThanOrEqual(1, abs($dz));
            if ($dx !== 0 && $dz !== 0) {
                self::assertTrue($grid->isWalkable($previous->x + $dx, $previous->z));
                self::assertTrue($grid->isWalkable($previous->x, $previous->z + $dz));
            }
            $previous = $cell;
        }
    }
}
