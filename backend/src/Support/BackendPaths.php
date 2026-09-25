<?php declare(strict_types=1);

namespace Mmo\Support;

final class BackendPaths
{
    public static function root(): string
    {
        return dirname(__DIR__, 2);
    }
}
