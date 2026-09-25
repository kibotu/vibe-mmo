<?php declare(strict_types=1);

namespace Mmo\Tests;

use Mmo\Auth\GuestName;
use Mmo\Config\Config;
use Mmo\Http\Csrf;
use PHPUnit\Framework\Attributes\CoversClass;
use PHPUnit\Framework\TestCase;

#[CoversClass(GuestName::class)]
#[CoversClass(Csrf::class)]
final class SecurityTest extends TestCase
{
    public function testGuestNamesAreUnicodeSafeAndBounded(): void
    {
        self::assertSame('Maple Forest', GuestName::normalize('  Maple   Forest  '));
        self::assertNull(GuestName::normalize('x'));
        self::assertNull(GuestName::normalize('<script>alert(1)</script>'));
    }

    public function testCsrfTokensAreSignedAndTimeBound(): void
    {
        $config = Config::fromArray([
            'database' => ['host' => 'db', 'port' => 3306, 'name' => 'mmo', 'username' => 'mmo', 'password' => 'db-secret'],
            'server' => [
                'host' => '127.0.0.1',
                'port' => 8080,
                'public_url' => 'https://example.test',
                'allowed_origins' => ['https://example.test'],
            ],
            'session' => ['cookie_name' => 'guest', 'lifetime_seconds' => 3600],
            'admin' => ['username' => 'admin', 'password' => 'admin-secret'],
            'world' => ['seed' => 1337],
        ]);
        $csrf = new Csrf($config);
        $token = $csrf->issue();

        self::assertTrue($csrf->validate($token));
        self::assertFalse($csrf->validate($token . 'x'));
        self::assertFalse($csrf->validate(''));
    }
}
