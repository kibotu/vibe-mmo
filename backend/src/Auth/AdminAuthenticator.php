<?php declare(strict_types=1);

namespace Mmo\Auth;

use Mmo\Config\Config;

final class AdminAuthenticator
{
    private const SESSION_KEY = 'mmo_admin_authenticated';

    public function __construct(private readonly Config $config)
    {
    }

    public function startSession(): void
    {
        if (session_status() === PHP_SESSION_ACTIVE) {
            return;
        }
        session_name($this->config->string('session.admin_session_name', 'mmo_admin'));
        session_set_cookie_params([
            'lifetime' => 0,
            'path' => '/',
            'secure' => strtolower((string) parse_url($this->config->string('server.public_url'), PHP_URL_SCHEME)) === 'https',
            'httponly' => true,
            'samesite' => 'Strict',
        ]);
        session_start();
    }

    public function login(mixed $username, mixed $password): bool
    {
        if (!is_string($username) || !is_string($password)) {
            return false;
        }
        $expectedUser = $this->config->string('admin.username');
        $expectedPassword = $this->config->string('admin.password');
        $userMatches = hash_equals(hash('sha256', $expectedUser, true), hash('sha256', $username, true));
        $passwordMatches = str_starts_with($expectedPassword, '$2y$')
            || str_starts_with($expectedPassword, '$argon2')
            ? password_verify($password, $expectedPassword)
            : hash_equals(hash('sha256', $expectedPassword, true), hash('sha256', $password, true));
        if (!$userMatches || !$passwordMatches) {
            return false;
        }

        session_regenerate_id(true);
        $_SESSION[self::SESSION_KEY] = true;

        return true;
    }

    public function authenticated(): bool
    {
        return ($_SESSION[self::SESSION_KEY] ?? false) === true;
    }

    public function logout(): void
    {
        unset($_SESSION[self::SESSION_KEY]);
        session_regenerate_id(true);
    }
}
