<?php declare(strict_types=1);

namespace Mmo\Auth;

use Mmo\Config\Config;
use Mmo\Database\CreatedGuest;
use Mmo\Database\GuestIdentity;
use Mmo\Database\RoomRepository;
use Mmo\Database\SessionRepository;
use Mmo\Http\CookieJar;

final class GuestService
{
    public function __construct(
        private readonly Config $config,
        private readonly SessionRepository $sessions,
        private readonly RoomRepository $rooms,
        private readonly CookieJar $cookies,
    ) {
    }

    public function create(string $name, string $roomCode): ?CreatedGuest
    {
        $normalizedName = GuestName::normalize($name);
        if ($normalizedName === null || !$this->rooms->isJoinable($roomCode)) {
            return null;
        }

        return $this->sessions->createGuest(
            $normalizedName,
            $roomCode,
            $this->config->int('session.lifetime_seconds', 2_592_000),
        );
    }

    public function setCookie(string $selector, string $validator): void
    {
        $this->cookies->setGuest($selector, $validator);
    }

    /** @param array{string, string}|null $credentials */
    public function authenticate(?array $credentials): ?GuestIdentity
    {
        if ($credentials === null) {
            return null;
        }

        return $this->sessions->authenticate($credentials[0], $credentials[1]);
    }
}
