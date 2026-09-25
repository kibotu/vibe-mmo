<?php declare(strict_types=1);

namespace Mmo\World;

final class WorldGenerator
{
    public function __construct(
        public readonly WorldGrid $grid,
        /** @var list<GridCell> */
        public readonly array $spawnCells,
        /** @var list<array{x: int, z: int, height: float, scale: float, rotation: float}> */
        public readonly array $trees,
        /** @var list<array{x: int, z: int, scale: float, rotation: float}> */
        public readonly array $rocks,
        /** @var list<array{x: int, z: int, scale: float}> */
        public readonly array $mushrooms,
        public readonly Mulberry32 $random,
    ) {
    }

    public static function generate(int $seed, int $poringCount = 16): self
    {
        return self::generateWithRandom(new Mulberry32($seed), $poringCount);
    }

    public static function generateWithRandom(Mulberry32 $random, int $poringCount = 16): self
    {
        $grid = new WorldGrid($random);
        $spawnCells = self::findSpawnCells($grid, $poringCount, $random);

        $reserved = ['32,32' => true];
        foreach ($spawnCells as $cell) {
            $reserved[$cell->x . ',' . $cell->z] = true;
        }

        [$trees, $rocks, $mushrooms] = self::buildProps($grid, $random, $reserved);
        self::consumeFlowerRandom($grid, $random);

        return new self($grid, $spawnCells, $trees, $rocks, $mushrooms, $random);
    }

    /** @return list<GridCell> */
    private static function findSpawnCells(WorldGrid $grid, int $count, Mulberry32 $random): array
    {
        $candidates = [];
        for ($z = 18; $z <= 44; ++$z) {
            for ($x = 18; $x <= 46; ++$x) {
                $distance = hypot($x - 32, $z - 32);
                if ($distance < 4.0 || $distance > 14.0 || !$grid->isWalkable($x, $z) || $grid->isWater($x, $z)) {
                    continue;
                }
                $candidates[] = new GridCell($x, $z);
            }
        }

        for ($i = count($candidates) - 1; $i > 0; --$i) {
            $j = $random->int(0, $i);
            [$candidates[$i], $candidates[$j]] = [$candidates[$j], $candidates[$i]];
        }

        return array_slice($candidates, 0, $count);
    }

    /**
     * @param array<string, true> $reserved
     * @return array{list<array{x: int, z: int, height: float, scale: float, rotation: float}>, list<array{x: int, z: int, scale: float, rotation: float}>, list<array{x: int, z: int, scale: float}>}
     */
    private static function buildProps(WorldGrid $grid, Mulberry32 $random, array $reserved): array
    {
        $trees = [];
        $rocks = [];
        $mushrooms = [];

        for ($attempt = 0; $attempt < 1400 && count($trees) < 78; ++$attempt) {
            $x = $random->int(3, $grid->width() - 4);
            $z = $random->int(3, $grid->height() - 4);
            $key = $x . ',' . $z;
            if (!$grid->isWalkable($x, $z) || $grid->isReserved($x, $z) || isset($reserved[$key])) {
                continue;
            }
            foreach ($trees as $tree) {
                if (hypot($tree['x'] - $x, $tree['z'] - $z) < 2.1) {
                    continue 2;
                }
            }

            $trees[] = [
                'x' => $x,
                'z' => $z,
                'height' => $random->range(0.9, 1.25),
                'scale' => $random->range(0.85, 1.2),
                'rotation' => $random->range(0.0, M_PI * 2.0),
            ];
            $grid->setWalkable($x, $z, false);
            $reserved[$key] = true;
        }

        for ($attempt = 0; $attempt < 500 && count($rocks) < 26; ++$attempt) {
            $x = $random->int(3, $grid->width() - 4);
            $z = $random->int(3, $grid->height() - 4);
            $key = $x . ',' . $z;
            if (!$grid->isWalkable($x, $z) || $grid->isReserved($x, $z) || isset($reserved[$key])) {
                continue;
            }

            $scale = $random->range(0.35, 0.75);
            $rocks[] = [
                'x' => $x,
                'z' => $z,
                'scale' => $scale,
                'rotation' => $random->range(0.0, M_PI * 2.0),
            ];
            if ($scale > 0.5) {
                $grid->setWalkable($x, $z, false);
            }
            $reserved[$key] = true;
        }

        for ($attempt = 0; $attempt < 600 && count($mushrooms) < 34; ++$attempt) {
            $x = $random->int(3, $grid->width() - 4);
            $z = $random->int(3, $grid->height() - 4);
            $key = $x . ',' . $z;
            if (!$grid->isWalkable($x, $z) || $grid->isReserved($x, $z) || isset($reserved[$key])) {
                continue;
            }
            $mushrooms[] = ['x' => $x, 'z' => $z, 'scale' => $random->range(0.7, 1.1)];
        }

        return [$trees, $rocks, $mushrooms];
    }

    private static function consumeFlowerRandom(WorldGrid $grid, Mulberry32 $random): void
    {
        for ($i = 0; $i < 150; ++$i) {
            $x = $random->range(2.0, (float) $grid->width() - 2.0);
            $z = $random->range(2.0, (float) $grid->height() - 2.0);
            // The original renderer consumes both values even for rejected flowers.
            unset($x, $z);
        }
    }

    public function checksum(): string
    {
        $parts = [$this->grid->checksum()];
        foreach ($this->spawnCells as $cell) {
            $parts[] = $cell->x . ':' . $cell->z;
        }
        foreach ($this->trees as $tree) {
            $parts[] = 't' . $tree['x'] . ':' . $tree['z'];
        }
        foreach ($this->rocks as $rock) {
            $parts[] = 'r' . $rock['x'] . ':' . $rock['z'];
        }

        return hash('sha256', implode('|', $parts));
    }
}
