<?php declare(strict_types=1);

namespace Mmo\Tests;

use InvalidArgumentException;
use Mmo\Config\Config;
use Mmo\Config\ConfigException;
use Mmo\Database\Database;
use PHPUnit\Framework\Attributes\CoversClass;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

#[CoversClass(Database::class)]
#[CoversClass(Config::class)]
final class DatabasePrefixTest extends TestCase
{
    public function testEveryTableIsPrefixed(): void
    {
        $database = new Database($this->config('mmo_'));

        self::assertSame('mmo_rooms', $database->table('rooms'));
        self::assertSame('mmo_players', $database->table('players'));
        self::assertSame('mmo_player_sessions', $database->table('player_sessions'));
        self::assertSame('mmo_schema_migrations', $database->table('schema_migrations'));
    }

    public function testAllKnownTablesAreCovered(): void
    {
        $database = new Database($this->config('mmo_'));

        foreach (Database::TABLES as $table) {
            self::assertSame('mmo_' . $table, $database->table($table));
        }
    }

    public function testPrefixIsAppliedVerbatim(): void
    {
        $database = new Database($this->config('other_'));

        self::assertSame('other_rooms', $database->table('rooms'));
    }

    public function testRejectsAnUnknownTable(): void
    {
        $database = new Database($this->config('mmo_'));

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('Unknown table "roms"');
        $database->table('roms');
    }

    /** @return iterable<string, array{string}> */
    public static function invalidPrefixes(): iterable
    {
        yield 'empty' => [''];
        yield 'no trailing underscore' => ['mmo'];
        yield 'leading digit' => ['1mmo_'];
        yield 'sql injection attempt' => ["mmo_\`; DROP TABLE players; --_"];
        yield 'hyphen' => ['mmo-a_'];
        yield 'too long' => [str_repeat('a', 40) . '_'];
    }

    #[DataProvider('invalidPrefixes')]
    public function testRejectsAnUnusablePrefix(string $prefix): void
    {
        $this->expectException(ConfigException::class);
        $this->expectExceptionMessage('database.prefix');
        $this->config($prefix);
    }

    public function testPrefixIsRequired(): void
    {
        $this->expectException(ConfigException::class);
        $this->expectExceptionMessage('database.prefix');
        $this->config(null);
    }

    private function config(?string $prefix): Config
    {
        $database = ['host' => '127.0.0.1', 'port' => 3306, 'name' => 'mmo_test', 'username' => 'mmo', 'password' => 'p'];
        if ($prefix !== null) {
            $database['prefix'] = $prefix;
        }

        return Config::fromArray([
            'database' => $database,
            'server' => [
                'host' => '127.0.0.1',
                'port' => 8080,
                'public_url' => 'http://localhost:8080',
                'allowed_origins' => ['http://localhost:8080'],
                'trusted_proxies' => [],
            ],
            'session' => ['cookie_name' => 'mmo_guest', 'lifetime_seconds' => 3600],
            'admin' => ['username' => 'admin', 'password' => 'p'],
            'world' => ['seed' => 1337],
        ]);
    }
}
