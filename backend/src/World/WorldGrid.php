<?php declare(strict_types=1);

namespace Mmo\World;

final class WorldGrid
{
    public const WIDTH = 64;
    public const HEIGHT = 64;

    /** @var list<float> */
    private array $heights;
    /** @var list<bool> */
    private array $walkable;
    /** @var list<bool> */
    private array $water;
    /** @var list<bool> */
    private array $paths;
    /** @var list<bool> */
    private array $bridges;

    public function __construct(Mulberry32 $random)
    {
        $this->heights = array_fill(0, self::WIDTH * self::HEIGHT, 0.0);
        $this->walkable = array_fill(0, self::WIDTH * self::HEIGHT, false);
        $this->water = array_fill(0, self::WIDTH * self::HEIGHT, false);
        $this->paths = array_fill(0, self::WIDTH * self::HEIGHT, false);
        $this->bridges = array_fill(0, self::WIDTH * self::HEIGHT, false);

        $this->generate($random);
    }

    public function width(): int
    {
        return self::WIDTH;
    }

    public function height(): int
    {
        return self::HEIGHT;
    }

    public function isWalkable(int $x, int $z): bool
    {
        return $this->inside($x, $z) && $this->walkable[$this->index($x, $z)];
    }

    public function isPassable(int $x, int $z): bool
    {
        return $this->isWalkable($x, $z) && (!$this->isWater($x, $z) || $this->isBridge($x, $z));
    }

    public function setWalkable(int $x, int $z, bool $value): void
    {
        if ($this->inside($x, $z)) {
            $this->walkable[$this->index($x, $z)] = $value;
        }
    }

    public function isWater(int $x, int $z): bool
    {
        return $this->inside($x, $z) && $this->water[$this->index($x, $z)];
    }

    public function isPath(int $x, int $z): bool
    {
        return $this->inside($x, $z) && $this->paths[$this->index($x, $z)];
    }

    public function isBridge(int $x, int $z): bool
    {
        return $this->inside($x, $z) && $this->bridges[$this->index($x, $z)];
    }

    public function isReserved(int $x, int $z): bool
    {
        return (abs($x - 32) <= 4 && abs($z - 32) <= 4)
            || $this->isPath($x, $z)
            || $this->isBridge($x, $z)
            || $this->isWater($x, $z);
    }

    public function cellAt(float $x, float $z): GridCell
    {
        return new GridCell(
            (int) max(0.0, min((float) self::WIDTH - 1.0, floor($x))),
            (int) max(0.0, min((float) self::HEIGHT - 1.0, floor($z))),
        );
    }

    /** @return array{x: float, y: float, z: float} */
    public function cellCenter(GridCell $cell): array
    {
        return [
            'x' => $cell->x + 0.5,
            'y' => $this->heightAt($cell->x + 0.5, $cell->z + 0.5),
            'z' => $cell->z + 0.5,
        ];
    }

    public function heightAt(float $x, float $z): float
    {
        $gx = max(0.0, min((float) self::WIDTH - 1.000001, $x));
        $gz = max(0.0, min((float) self::HEIGHT - 1.000001, $z));
        $x0 = (int) floor($gx);
        $z0 = (int) floor($gz);
        $x1 = min($x0 + 1, self::WIDTH - 1);
        $z1 = min($z0 + 1, self::HEIGHT - 1);
        $tx = $gx - $x0;
        $tz = $gz - $z0;

        $h00 = $this->heights[$this->index($x0, $z0)];
        $h10 = $this->heights[$this->index($x1, $z0)];
        $h01 = $this->heights[$this->index($x0, $z1)];
        $h11 = $this->heights[$this->index($x1, $z1)];
        $a = $h00 + ($h10 - $h00) * $tx;
        $b = $h01 + ($h11 - $h01) * $tx;

        return $a + ($b - $a) * $tz;
    }

    public function cornerHeight(int $x, int $z): float
    {
        return $this->heights[$this->index(
            max(0, min(self::WIDTH - 1, $x)),
            max(0, min(self::HEIGHT - 1, $z)),
        )];
    }

    public function checksum(): string
    {
        $context = hash_init('sha256');
        foreach ($this->walkable as $index => $walkable) {
            hash_update($context, pack('C', $walkable ? 1 : 0));
            hash_update($context, pack('g', $this->heights[$index]));
        }

        return hash_final($context);
    }

    private function generate(Mulberry32 $random): void
    {
        for ($z = 0; $z < self::HEIGHT; ++$z) {
            for ($x = 0; $x < self::WIDTH; ++$x) {
                $index = $this->index($x, $z);
                $rolling = sin($x * 0.19) * 0.16 + cos($z * 0.23) * 0.12;
                $small = sin(($x + $z) * 0.41) * 0.035;
                $terrainHeight = $rolling + $small + $random->range(-0.025, 0.025);

                $streamCenter = 44.0 + sin($x * 0.16) * 0.7;
                $isWater = abs($z - $streamCenter) < 1.15;
                $isBridge = $x >= 30 && $x <= 34 && $z >= 42 && $z <= 46;
                $isPath = (abs($x - 32) < 1.25 && $z <= 45.0)
                    || (abs($z - 32) < 1.25 && $x >= 25.0);

                $this->water[$index] = $isWater;
                $this->paths[$index] = $isPath;
                $this->bridges[$index] = $isBridge;
                $height = $isBridge ? 0.16 : ($isWater ? -0.42 : $terrainHeight);
                $this->heights[$index] = self::toFloat32($height);
                $this->walkable[$index] = $x > 0
                    && $z > 0
                    && $x < self::WIDTH - 1
                    && $z < self::HEIGHT - 1
                    && (!$isWater || $isBridge);
            }
        }
    }

    private function index(int $x, int $z): int
    {
        return $z * self::WIDTH + $x;
    }

    private function inside(int $x, int $z): bool
    {
        return $x >= 0 && $z >= 0 && $x < self::WIDTH && $z < self::HEIGHT;
    }

    private static function toFloat32(float $value): float
    {
        /** @var array{1: float} $unpacked */
        $unpacked = unpack('g', pack('g', $value));

        return $unpacked[1];
    }
}
