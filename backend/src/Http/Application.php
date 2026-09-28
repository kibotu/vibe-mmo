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
use Mmo\Server\PollingRoomService;
use Mmo\Server\RoomStateCodec;
use Mmo\Server\RoomStateRepository;
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
        public readonly ?PollingRoomService $polling = null,
        public readonly ?RoomStateRepository $roomStates = null,
    ) {
    }

    public static function boot(?string $configurationPath = null): self
    {
        // MMO_SECRETS_FILE lets a container point at a configuration merged for
        // its own runtime; the default is the file beside this code.
        $config = Config::fromFile(
            $configurationPath ?? (getenv('MMO_SECRETS_FILE') ?: BackendPaths::root() . '/secrets.yml'),
        );
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

        $application = new self($config, $logger, $database, $sessions, $players, $rooms, $cookies, $csrf, $guests, $admin, $control);
        $roomStates = new RoomStateRepository($database, $config);

        // The polling service needs the booted application to reach the player
        // roster, so it is built after and injected through a fresh instance
        // rather than mutating the readonly one.
        return new self(
            $config,
            $logger,
            $database,
            $sessions,
            $players,
            $rooms,
            $cookies,
            $csrf,
            $guests,
            $admin,
            $control,
            new PollingRoomService($application, $roomStates, new RoomStateCodec()),
            $roomStates,
        );
    }
}
