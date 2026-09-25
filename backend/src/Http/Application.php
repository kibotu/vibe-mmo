<?php declare(strict_types=1);

namespace Mmo\Http;

use Monolog\Formatter\LineFormatter;
use Monolog\Handler\StreamHandler;
use Monolog\Logger;
use Mmo\Auth\AdminAuthenticator;
use Mmo\Auth\GuestService;
use Mmo\Config\Config;
use Mmo\Control\ServerControl;
use Mmo\Database\Database;
use Mmo\Database\PlayerRepository;
use Mmo\Database\RoomRepository;
use Mmo\Database\SessionRepository;
use Mmo\Support\BackendPaths;
use Psr\Log\LoggerInterface;

final class Application
{
    private function __construct(
        public readonly Config $config,
        public readonly LoggerInterface $logger,
        public readonly Database $database,
        public readonly SessionRepository $sessions,
        public readonly PlayerRepository $players,
        public readonly RoomRepository $rooms,
        public readonly CookieJar $cookies,
        public readonly Csrf $csrf,
        public readonly GuestService $guests,
        public readonly AdminAuthenticator $admin,
        public readonly ServerControl $control,
    ) {
    }

    public static function boot(?string $configurationPath = null): self
    {
        $config = Config::fromFile($configurationPath ?? BackendPaths::root() . '/secrets.yml');
        date_default_timezone_set($config->string('app.timezone', 'UTC'));
        $logger = new Logger('mmo-backend');
        $handler = new StreamHandler('php://stderr', Logger::INFO);
        $handler->setFormatter(new LineFormatter(null, 'Y-m-d\TH:i:s.uP', true, true));
        $logger->pushHandler($handler);

        $database = new Database($config);
        $sessions = new SessionRepository($database);
        $players = new PlayerRepository($database);
        $rooms = new RoomRepository($database, $config);
        $cookies = new CookieJar($config);
        $csrf = new Csrf($config);
        $guests = new GuestService($config, $sessions, $rooms, $cookies);
        $admin = new AdminAuthenticator($config);
        $control = new ServerControl($config);

        return new self($config, $logger, $database, $sessions, $players, $rooms, $cookies, $csrf, $guests, $admin, $control);
    }
}
