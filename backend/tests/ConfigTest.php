<?php declare(strict_types=1);

namespace Mmo\Tests;

use Mmo\Config\Config;
use Mmo\Config\ConfigException;
use PHPUnit\Framework\Attributes\CoversClass;
use PHPUnit\Framework\TestCase;

#[CoversClass(Config::class)]
final class ConfigTest extends TestCase
{
    public function testLoadsRequiredValuesWithDotAccess(): void
    {
        $config = Config::fromArray($this->validValues());

        self::assertSame('127.0.0.1', $config->get('database.host'));
        self::assertSame(3306, $config->int('database.port'));
        self::assertSame(['http://localhost:8080'], $config->strings('server.allowed_origins'));
        self::assertSame(1337, $config->get('world.seed'));
    }

    public function testRejectsAMissingRequiredSecret(): void
    {
        $values = $this->validValues();
        unset($values['admin']['password']);

        $this->expectException(ConfigException::class);
        $this->expectExceptionMessage('admin.password');
        Config::fromArray($values);
    }

    public function testSupportsDeploymentConfigurationAliases(): void
    {
        $config = Config::fromArray([
            'app' => ['public_url' => 'https://play.example'],
            'database' => [
                'host' => 'db',
                'host_port' => 3306,
                'name' => 'mmo',
                'user' => 'mmo',
                'password' => 'secret',
            ],
            'server' => [
                'host' => '127.0.0.1',
                'port' => 8080,
                'map_seed' => 1337,
                'allowed_origins' => ['https://play.example'],
                'trusted_proxy_networks' => ['127.0.0.0/8'],
            ],
            'sessions' => ['cookie_name' => 'guest', 'lifetime_seconds' => 3600],
            'admin' => ['username' => 'admin', 'password' => 'secret'],
        ]);

        self::assertSame('mmo', $config->string('database.username'));
        self::assertSame(1337, $config->int('world.seed'));
        self::assertSame('guest', $config->string('session.cookie_name'));
        self::assertSame('https://play.example', $config->string('server.public_url'));
    }

    public function testProductionRejectsDevelopmentCookieAndExampleSecrets(): void
    {
        $values = $this->validValues();
        $values['app'] = ['environment' => 'production'];
        $values['server']['public_url'] = 'http://play.example';
        $values['sessions'] = ['cookie_name' => 'guest', 'lifetime_seconds' => 3600, 'cookie_secure' => false];
        $values['database']['root_password'] = 'change-this-root-password';

        $this->expectException(ConfigException::class);
        $this->expectExceptionMessage('HTTPS');
        Config::fromArray($values);
    }

    public function testProductionRejectsPlaceholderSecrets(): void
    {
        $values = $this->validValues();
        $values['app'] = ['environment' => 'production'];
        $values['server']['public_url'] = 'https://play.example';
        $values['server']['allowed_origins'] = ['https://play.example'];
        $values['sessions'] = ['cookie_name' => 'guest', 'lifetime_seconds' => 3600, 'cookie_secure' => true];
        $values['database']['root_password'] = 'change-this-root-password';

        $this->expectException(ConfigException::class);
        $this->expectExceptionMessage('example value');
        Config::fromArray($values);
    }

    public function testRejectsNoneSameSiteWithoutSecureCookie(): void
    {
        $values = $this->validValues();
        $values['sessions'] = ['cookie_name' => 'guest', 'lifetime_seconds' => 3600, 'cookie_secure' => false, 'cookie_samesite' => 'None'];

        $this->expectException(ConfigException::class);
        $this->expectExceptionMessage('SameSite=None');
        Config::fromArray($values);
    }

    /** @return array<string, mixed> */
    private function validValues(): array
    {
        return [
            'database' => [
                'host' => '127.0.0.1',
                'port' => 3306,
                'name' => 'mmo_test',
                'username' => 'mmo',
                'password' => 'test-password',
            ],
            'server' => [
                'host' => '127.0.0.1',
                'port' => 8080,
                'public_url' => 'http://localhost:8080',
                'allowed_origins' => ['http://localhost:8080'],
                'trusted_proxies' => [],
            ],
            'session' => ['cookie_name' => 'mmo_guest', 'lifetime_seconds' => 3600],
            'admin' => ['username' => 'admin', 'password' => 'test-password'],
            'world' => ['seed' => 1337],
        ];
    }
}
