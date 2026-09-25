<?php declare(strict_types=1);

namespace Mmo\Config;

use Symfony\Component\Yaml\Yaml;

final class Config
{
    /** @var array<string, array<string, list<string>>> */
    private const ALIASES = [
        'app.base_url' => ['app.url', 'app.public_url', 'server.public_url'],
        'database.port' => ['database.host_port'],
        'database.name' => ['database.database'],
        'database.username' => ['database.user'],
        'server.host' => ['websocket.host', 'server.websocket.host'],
        'server.port' => ['websocket.port', 'server.websocket.port'],
        'server.public_url' => ['app.base_url', 'app.url', 'app.public_url'],
        'server.allowed_origins' => [
            'websocket.allowed_origins',
            'server.websocket.allowed_origins',
            'cors.allowed_origins',
        ],
        'server.trusted_proxies' => [
            'proxy.trusted_proxies',
            'nginx.trusted_proxies',
            'server.trusted_proxy_networks',
        ],
        'session.cookie_name' => ['sessions.cookie_name', 'app.guest_cookie_name'],
        'world.seed' => ['server.map_seed'],
        'rooms.max_players' => ['server.max_players_per_room'],
        'rooms.disconnect_grace_seconds' => ['server.disconnected_player_grace_seconds'],
        'rooms.empty_ttl_seconds' => ['server.empty_room_ttl_seconds'],
        'rooms.persist_interval_seconds' => ['server.persistence_interval_seconds'],
        'server.max_message_bytes' => ['server.websocket_max_message_bytes'],
        'server.websocket_bytes_per_second' => ['server.max_inbound_bytes_per_second'],
        'server.websocket_frames_per_second' => ['server.max_inbound_frames_per_second'],
        'runtime.directory' => ['server.runtime_directory'],
        'control.driver' => ['server_control.driver', 'process_control.driver'],
        'control.supervisor_config' => ['server_control.supervisor_config'],
        'control.supervisor_program' => ['server_control.supervisor_program'],
        'control.service' => ['control.systemd_service', 'server_control.service'],
    ];

    /** @var list<string> */
    private const REQUIRED = [
        'database.host',
        'database.port',
        'database.name',
        'database.username',
        'database.password',
        'server.host',
        'server.port',
        'server.public_url',
        'server.allowed_origins',
        'session.cookie_name',
        'admin.username',
        'admin.password',
        'world.seed',
    ];

    /**
     * @param array<string, mixed> $values
     */
    private function __construct(private readonly array $values)
    {
    }

    public static function fromFile(string $path): self
    {
        if (!is_file($path) || !is_readable($path)) {
            throw new ConfigException('The secrets configuration is unavailable.');
        }

        try {
            $parsed = Yaml::parseFile($path, Yaml::PARSE_EXCEPTION_ON_INVALID_TYPE);
        } catch (\Throwable $exception) {
            throw new ConfigException('The secrets configuration is not valid YAML.', previous: $exception);
        }

        if (!is_array($parsed)) {
            throw new ConfigException('The secrets configuration must contain a YAML mapping.');
        }

        /** @var array<string, mixed> $parsed */
        return self::fromArray($parsed);
    }

    /**
     * @param array<string, mixed> $values
     */
    public static function fromArray(array $values): self
    {
        $config = new self($values);
        $config->validate();

        return $config;
    }

    public function has(string $key): bool
    {
        return $this->find($key) !== null;
    }

    public function get(string $key, mixed $default = null): mixed
    {
        $path = $this->find($key);
        if ($path === null) {
            return $default;
        }
        $value = $this->values;
        foreach (explode('.', $path) as $segment) {
            $value = $value[$segment];
        }

        return $value;
    }

    public function string(string $key, ?string $default = null): string
    {
        $value = $this->get($key, $default);
        if (!is_string($value) || trim($value) === '') {
            throw new ConfigException(sprintf('Configuration value "%s" must be a non-empty string.', $key));
        }

        return $value;
    }

    public function int(string $key, ?int $default = null): int
    {
        $value = $this->get($key, $default);
        if (is_int($value)) {
            return $value;
        }
        if (is_string($value) && preg_match('/^-?\d+$/D', $value) === 1) {
            return (int) $value;
        }

        throw new ConfigException(sprintf('Configuration value "%s" must be an integer.', $key));
    }

    public function float(string $key, ?float $default = null): float
    {
        $value = $this->get($key, $default);
        if (is_int($value) || is_float($value)) {
            return (float) $value;
        }
        if (is_string($value) && is_numeric($value)) {
            return (float) $value;
        }

        throw new ConfigException(sprintf('Configuration value "%s" must be numeric.', $key));
    }

    public function bool(string $key, ?bool $default = null): bool
    {
        $value = $this->get($key, $default);
        if (is_bool($value)) {
            return $value;
        }
        if (is_int($value) && ($value === 0 || $value === 1)) {
            return $value === 1;
        }
        if (is_string($value)) {
            $normalized = strtolower(trim($value));
            if (in_array($normalized, ['1', 'true', 'yes', 'on'], true)) {
                return true;
            }
            if (in_array($normalized, ['0', 'false', 'no', 'off'], true)) {
                return false;
            }
        }

        throw new ConfigException(sprintf('Configuration value "%s" must be boolean.', $key));
    }

    /**
     * @return list<mixed>
     */
    public function list(string $key, ?array $default = null): array
    {
        $value = $this->get($key, $default);
        if (!is_array($value) || !array_is_list($value)) {
            throw new ConfigException(sprintf('Configuration value "%s" must be a list.', $key));
        }

        return $value;
    }

    /**
     * @return list<string>
     */
    public function strings(string $key, ?array $default = null): array
    {
        $values = $this->list($key, $default);
        foreach ($values as $value) {
            if (!is_string($value) || trim($value) === '') {
                throw new ConfigException(sprintf('Configuration value "%s" must contain non-empty strings.', $key));
            }
        }

        return $values;
    }

    private function validate(): void
    {
        foreach (self::REQUIRED as $key) {
            $value = $this->get($key);
            if ($value === null || $value === '' || $value === []) {
                throw new ConfigException(sprintf('Missing required configuration value "%s".', $key));
            }
        }

        if ($this->int('database.port') < 1 || $this->int('database.port') > 65535) {
            throw new ConfigException('Configuration value "database.port" is out of range.');
        }
        if ($this->int('server.port') < 1 || $this->int('server.port') > 65535) {
            throw new ConfigException('Configuration value "server.port" is out of range.');
        }
        if ($this->int('world.seed') < 0 || $this->int('world.seed') > 4_294_967_295) {
            throw new ConfigException('Configuration value "world.seed" must be an unsigned 32-bit integer.');
        }
        foreach (['server.tick_rate' => 20, 'server.snapshot_rate' => 10, 'server.interpolation_ms' => 100] as $key => $required) {
            if ($this->has($key) && $this->int($key) !== $required) {
                throw new ConfigException(sprintf('Configuration value "%s" must be %d.', $key, $required));
            }
        }
        foreach ([
            'server.max_message_bytes' => [256, 65_536],
            'server.max_connections' => [1, 100_000],
            'server.max_rooms' => [1, 10_000],
            'rooms.max_players' => [1, 10_000],
            'rooms.disconnect_grace_seconds' => [1, 86_400],
            'rooms.empty_ttl_seconds' => [1, 604_800],
            'rooms.persist_interval_seconds' => [1, 3_600],
        ] as $key => [$minimum, $maximum]) {
            $value = $this->int($key, $minimum);
            if ($value < $minimum || $value > $maximum) {
                throw new ConfigException(sprintf('Configuration value "%s" is out of range.', $key));
            }
        }
        $timezone = $this->string('app.timezone', 'UTC');
        if (!in_array($timezone, timezone_identifiers_list(), true)) {
            throw new ConfigException('Configuration value "app.timezone" is invalid.');
        }

        $publicUrl = $this->string('server.public_url');
        if (filter_var($publicUrl, FILTER_VALIDATE_URL) === false || !in_array(parse_url($publicUrl, PHP_URL_SCHEME), ['http', 'https'], true)) {
            throw new ConfigException('Configuration value "server.public_url" must be an HTTP(S) URL.');
        }

        $origins = $this->strings('server.allowed_origins');
        if ($origins === []) {
            throw new ConfigException('At least one WebSocket origin must be allowed.');
        }
        foreach ($origins as $origin) {
            $parts = parse_url($origin);
            if (filter_var($origin, FILTER_VALIDATE_URL) === false
                || !in_array($parts['scheme'] ?? null, ['http', 'https'], true)
                || isset($parts['user'])
                || isset($parts['pass'])
                || isset($parts['query'])
                || isset($parts['fragment'])
                || !in_array($parts['path'] ?? '', ['', '/'], true)
            ) {
                throw new ConfigException('Every allowed WebSocket origin must be an absolute HTTP(S) origin.');
            }
        }
        foreach ($this->strings('server.trusted_proxies', []) as $network) {
            [$address, $prefix] = array_pad(explode('/', $network, 2), 2, null);
            $binary = inet_pton((string) $address);
            $maximumPrefix = $binary !== false && str_contains((string) $address, ':') ? 128 : 32;
            if ($binary === false
                || ($prefix !== null && (preg_match('/^\d{1,3}$/D', $prefix) !== 1 || (int) $prefix > $maximumPrefix))
            ) {
                throw new ConfigException('Every trusted proxy entry must be an IP address or CIDR network.');
            }
        }

        $cookieName = $this->string('session.cookie_name');
        if (preg_match('/^[A-Za-z0-9_]{1,64}$/D', $cookieName) !== 1) {
            throw new ConfigException('The guest cookie name is invalid.');
        }

        $lifetime = $this->int('session.lifetime_seconds', 2_592_000);
        if ($lifetime < 300 || $lifetime > 31_536_000) {
            throw new ConfigException('The guest session lifetime must be between 5 minutes and 1 year.');
        }

        $sameSite = strtoupper((string) $this->get('sessions.cookie_samesite', 'Lax'));
        if (!in_array($sameSite, ['LAX', 'STRICT', 'NONE'], true)) {
            throw new ConfigException('The session cookie SameSite policy is invalid.');
        }
        $cookieSecure = $this->get('sessions.cookie_secure');
        if ($cookieSecure === null) {
            $cookieSecure = parse_url($publicUrl, PHP_URL_SCHEME) === 'https';
        } else {
            $cookieSecure = $this->bool('sessions.cookie_secure');
        }
        if ($sameSite === 'NONE' && $cookieSecure !== true) {
            throw new ConfigException('SameSite=None requires a secure session cookie.');
        }

        $environment = strtolower((string) $this->get('app.environment', 'development'));
        if ($environment !== 'production') {
            return;
        }

        if (parse_url($publicUrl, PHP_URL_SCHEME) !== 'https') {
            throw new ConfigException('Production requires an HTTPS server.public_url.');
        }
        if (strtolower((string) $this->get('control.driver', 'none')) === 'systemctl'
            && in_array((string) $this->get('server.host'), ['0.0.0.0', '::'], true)
        ) {
            throw new ConfigException('A systemd production server must bind to a private interface.');
        }
        if ($cookieSecure !== true) {
            throw new ConfigException('Production requires sessions.cookie_secure=true.');
        }
        foreach ($origins as $origin) {
            if (parse_url($origin, PHP_URL_SCHEME) !== 'https') {
                throw new ConfigException('Production WebSocket origins must use HTTPS.');
            }
        }

        foreach (['database.password', 'database.root_password', 'admin.password', 'ftp.host', 'ftp.password'] as $key) {
            $value = $this->get($key);
            if (is_string($value) && preg_match('/(?:change[-_ ]?this|your[-_ ]|example\.com|replace[-_ ]?me)/i', $value) === 1) {
                throw new ConfigException(sprintf('Production secret "%s" still contains an example value.', $key));
            }
        }
    }

    private function find(string $key): ?string
    {
        $keys = [$key, ...(self::ALIASES[$key] ?? [])];
        foreach ($keys as $candidate) {
            $value = $this->values;
            $found = true;
            foreach (explode('.', $candidate) as $segment) {
                if (!is_array($value) || !array_key_exists($segment, $value)) {
                    $found = false;
                    break;
                }
                $value = $value[$segment];
            }
            if ($found && $value !== null) {
                return $candidate;
            }
        }

        return null;
    }
}
