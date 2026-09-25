<?php declare(strict_types=1);

namespace Mmo\World;

final readonly class GridCell
{
    public function __construct(
        public int $x,
        public int $z,
    ) {
    }
}
