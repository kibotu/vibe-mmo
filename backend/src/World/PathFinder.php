<?php declare(strict_types=1);

namespace Mmo\World;

final class PathFinder
{
    /** @return list<GridCell> */
    public function find(WorldGrid $grid, GridCell $start, GridCell $goal): array
    {
        if (!$grid->isWalkable($start->x, $start->z) || !$grid->isWalkable($goal->x, $goal->z)) {
            return [];
        }
        if ($start->x === $goal->x && $start->z === $goal->z) {
            return [];
        }

        $open = [[
            'cell' => $start,
            'g' => 0.0,
            'f' => self::heuristic($start, $goal),
        ]];
        $cameFrom = [];
        $gScore = [self::key($start->x, $start->z) => 0.0];
        $directions = [
            [-1, -1], [0, -1], [1, -1],
            [-1, 0], [1, 0],
            [-1, 1], [0, 1], [1, 1],
        ];

        while ($open !== []) {
            usort($open, static function (array $left, array $right): int {
                return $left['f'] <=> $right['f']
                    ?: $left['cell']->z <=> $right['cell']->z
                    ?: $left['cell']->x <=> $right['cell']->x;
            });
            $current = array_shift($open);
            $currentCell = $current['cell'];

            if ($currentCell->x === $goal->x && $currentCell->z === $goal->z) {
                return $this->reconstruct($cameFrom, $goal, $start);
            }

            foreach ($directions as [$dx, $dz]) {
                $nx = $currentCell->x + $dx;
                $nz = $currentCell->z + $dz;
                if (!$grid->isWalkable($nx, $nz)) {
                    continue;
                }
                if ($dx !== 0 && $dz !== 0 && (
                    !$grid->isWalkable($currentCell->x + $dx, $currentCell->z)
                    || !$grid->isWalkable($currentCell->x, $currentCell->z + $dz)
                )) {
                    continue;
                }

                $step = $dx !== 0 && $dz !== 0 ? M_SQRT2 : 1.0;
                $tentativeG = $current['g'] + $step;
                $nodeKey = self::key($nx, $nz);
                if ($tentativeG >= ($gScore[$nodeKey] ?? INF)) {
                    continue;
                }

                $next = new GridCell($nx, $nz);
                $cameFrom[$nodeKey] = $currentCell;
                $gScore[$nodeKey] = $tentativeG;
                $open[] = [
                    'cell' => $next,
                    'g' => $tentativeG,
                    'f' => $tentativeG + self::heuristic($next, $goal),
                ];
            }
        }

        return [];
    }

    /**
     * @param array<string, GridCell> $cameFrom
     * @return list<GridCell>
     */
    private function reconstruct(array $cameFrom, GridCell $goal, GridCell $start): array
    {
        $result = [];
        $cursor = $goal;
        while ($cursor->x !== $start->x || $cursor->z !== $start->z) {
            $result[] = $cursor;
            $previous = $cameFrom[self::key($cursor->x, $cursor->z)] ?? null;
            if ($previous === null) {
                return [];
            }
            $cursor = $previous;
        }

        return array_reverse($result);
    }

    private static function heuristic(GridCell $from, GridCell $to): float
    {
        $dx = abs($from->x - $to->x);
        $dz = abs($from->z - $to->z);

        return max($dx, $dz) + (M_SQRT2 - 1.0) * min($dx, $dz);
    }

    private static function key(int $x, int $z): string
    {
        return $x . ',' . $z;
    }
}
