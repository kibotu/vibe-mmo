<?php declare(strict_types=1);

namespace Mmo\World;

final class Mulberry32
{
    private const MASK_32 = 0xFFFF_FFFF;
    private const ADDEND = 0x6D2B_79F5;

    private int $state;

    public function __construct(int $seed)
    {
        $this->state = $seed & self::MASK_32;
    }

    public function next(): float
    {
        $t = ($this->state + self::ADDEND) & self::MASK_32;
        $this->state = $t;

        $mixed = ($t ^ ($t >> 15)) & self::MASK_32;
        $t = self::imul($mixed, $t | 1);

        $t ^= $t + self::imul(($t ^ ($t >> 7)) & self::MASK_32, $t | 61);
        $t &= self::MASK_32;

        return (($t ^ ($t >> 14)) & self::MASK_32) / 4_294_967_296.0;
    }

    public function range(float $min, float $max): float
    {
        return $min + $this->next() * ($max - $min);
    }

    public function int(int $min, int $maxInclusive): int
    {
        return (int) floor($this->range((float) $min, (float) $maxInclusive + 1.0));
    }

    public function chance(float $probability): bool
    {
        return $this->next() < $probability;
    }

    private static function imul(int $left, int $right): int
    {
        $leftLow = $left & 0xFFFF;
        $rightLow = $right & 0xFFFF;
        $cross = (($left >> 16) * $rightLow) + ($leftLow * ($right >> 16));

        return ($leftLow * $rightLow + ($cross << 16)) & self::MASK_32;
    }
}
