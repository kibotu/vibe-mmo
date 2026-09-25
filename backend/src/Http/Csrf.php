<?php declare(strict_types=1);

namespace Mmo\Http;

use Mmo\Config\Config;

final class Csrf
{
    private readonly string $key;

    public function __construct(Config $config)
    {
        $configured = $config->get('app.key');
        if (is_string($configured) && strlen($configured) >= 32) {
            $this->key = hash('sha256', $configured, true);
        } else {
            $this->key = hash(
                'sha256',
                "csrf\0" . $config->string('database.password') . "\0" . $config->string('admin.password'),
                true,
            );
        }
    }

    public function issue(int $lifetimeSeconds = 7200): string
    {
        $payload = self::base64UrlEncode(json_encode([
            'expires' => time() + $lifetimeSeconds,
            'nonce' => bin2hex(random_bytes(18)),
        ], JSON_THROW_ON_ERROR));
        $signature = self::base64UrlEncode(hash_hmac('sha256', $payload, $this->key, true));

        return $payload . '.' . $signature;
    }

    public function validate(mixed $token): bool
    {
        if (!is_string($token) || strlen($token) > 512) {
            return false;
        }
        [$payload, $signature, $extra] = array_pad(explode('.', $token, 3), 3, null);
        if ($extra !== null || !is_string($payload) || !is_string($signature)) {
            return false;
        }
        $expected = self::base64UrlEncode(hash_hmac('sha256', $payload, $this->key, true));
        if (!hash_equals($expected, $signature)) {
            return false;
        }
        $decoded = json_decode(self::base64UrlDecode($payload), true);
        if (!is_array($decoded) || !is_int($decoded['expires'] ?? null) || $decoded['expires'] < time()) {
            return false;
        }

        return preg_match('/^[0-9a-f]{36}$/D', (string) ($decoded['nonce'] ?? '')) === 1;
    }

    private static function base64UrlEncode(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }

    private static function base64UrlDecode(string $value): string
    {
        $padding = strlen($value) % 4;
        if ($padding !== 0) {
            $value .= str_repeat('=', 4 - $padding);
        }
        $decoded = base64_decode(strtr($value, '-_', '+/'), true);

        return $decoded === false ? '' : $decoded;
    }
}
