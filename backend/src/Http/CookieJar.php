<?php declare(strict_types=1);

namespace Mmo\Http;

use Mmo\Config\Config;

final class CookieJar
{
    public function __construct(private readonly Config $config)
    {
    }

    /** @param array<string, string> $cookies */
    public function guestCredentials(array $cookies): ?array
    {
        $value = $cookies[$this->config->string('session.cookie_name')] ?? null;
        if ($value === null) {
            return null;
        }
        $parts = explode('.', $value, 2);
        if (count($parts) !== 2) {
            return null;
        }

        return [$parts[0], $parts[1]];
    }

    public function setGuest(string $selector, string $validator): void
    {
        $lifetime = $this->config->int('session.lifetime_seconds', 2_592_000);
        $attributes = [
            $this->config->string('session.cookie_name') . '=' . $selector . '.' . $validator,
            'Path=/',
            'Max-Age=' . $lifetime,
            'HttpOnly',
            'SameSite=' . $this->sameSite(),
        ];
        if ($this->isSecure()) {
            $attributes[] = 'Secure';
        }
        header('Set-Cookie: ' . implode('; ', $attributes), false);
    }

    public function clearGuest(): void
    {
        $attributes = [
            $this->config->string('session.cookie_name') . '=',
            'Path=/',
            'Max-Age=0',
            'Expires=Thu, 01 Jan 1970 00:00:00 GMT',
            'HttpOnly',
            'SameSite=' . $this->sameSite(),
        ];
        if ($this->isSecure()) {
            $attributes[] = 'Secure';
        }
        header('Set-Cookie: ' . implode('; ', $attributes), false);
    }

    private function isSecure(): bool
    {
        return $this->config->bool(
            'sessions.cookie_secure',
            strtolower((string) parse_url($this->config->string('server.public_url'), PHP_URL_SCHEME)) === 'https',
        );
    }

    private function sameSite(): string
    {
        $value = strtoupper($this->config->string('sessions.cookie_samesite', 'Lax'));
        if (!in_array($value, ['LAX', 'STRICT', 'NONE'], true)) {
            throw new \InvalidArgumentException('Unsupported session cookie SameSite policy.');
        }

        return ucfirst(strtolower($value));
    }
}
