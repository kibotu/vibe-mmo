#!/usr/bin/env php
<?php declare(strict_types=1);

use Amp\Http\Server\Middleware\ForwardedHeaderType;
use Amp\Http\Server\SocketHttpServer;
use Amp\Socket\InternetAddress;
use Amp\Websocket\ConstantRateLimit;
use Amp\Websocket\Parser\Rfc6455ParserFactory;
use Amp\Websocket\PeriodicHeartbeatQueue;
use Amp\Websocket\Server\AllowOriginAcceptor;
use Amp\Websocket\Server\Rfc6455ClientFactory;
use Amp\Websocket\Server\Websocket;
use Mmo\Database\PlayerRepository;
use Mmo\Database\RoomRepository;
use Mmo\Database\SessionRepository;
use Mmo\Http\Application;
use Mmo\Protocol\IntentValidator;
use Mmo\Runtime\AtomicJsonFile;
use Mmo\Runtime\RuntimeCommandPoller;
use Mmo\Runtime\RuntimePaths;
use Mmo\Runtime\StatusStore;
use Mmo\Server\RoomManager;
use Mmo\Server\SafeErrorHandler;
use Mmo\Server\SafeExceptionHandler;
use Mmo\Server\WebSocketGameHandler;
use Mmo\Server\WsRoute;
use Revolt\EventLoop;

require dirname(__DIR__) . '/vendor/autoload.php';

set_time_limit(0);
$application = Application::boot();
$config = $application->config;
$logger = $application->logger;
$database = $application->database;
$database->ping();

$rooms = new RoomManager(
    $config,
    new PlayerRepository($database),
    new RoomRepository($database, $config),
    new IntentValidator(),
);
$handler = new WebSocketGameHandler($config, new SessionRepository($database), $rooms, $logger);
$errorHandler = new SafeErrorHandler();
$exceptionHandler = new SafeExceptionHandler($errorHandler, $logger);
$http = SocketHttpServer::createForBehindProxy(
    $logger,
    ForwardedHeaderType::XForwardedFor,
    $config->strings('server.trusted_proxies', []),
    false,
    $config->int('server.request_concurrency', 200),
    ['GET'],
    null,
    $exceptionHandler,
);
$host = $config->string('server.host');
$port = $config->int('server.port');
$http->expose(new InternetAddress($host, $port));

$messageLimit = $config->int('server.max_message_bytes', 4096);
$clientFactory = new Rfc6455ClientFactory(
    new PeriodicHeartbeatQueue(3, $config->int('server.heartbeat_seconds', 15)),
    new ConstantRateLimit(
        $config->int('server.websocket_bytes_per_second', 65_536),
        $config->int('server.websocket_frames_per_second', 60),
    ),
    new Rfc6455ParserFactory(true, true, $messageLimit, $messageLimit),
    $messageLimit,
    3.0,
);
$websocket = new Websocket(
    $http,
    $logger,
    new AllowOriginAcceptor(array_map(
        static fn (string $origin): string => rtrim($origin, '/'),
        $config->strings('server.allowed_origins'),
    )),
    $handler,
    null,
    $clientFactory,
);
$http->start(new WsRoute($websocket), $errorHandler);

$runtimePaths = new RuntimePaths($config);
$statusStore = new StatusStore(new AtomicJsonFile($runtimePaths->statusFile));
$commandPoller = new RuntimeCommandPoller(new AtomicJsonFile($runtimePaths->commandFile));
$startedAt = microtime(true);
$state = 'starting';
$tickCount = 0;
$lastTickAt = 0.0;
$tickDriftMs = 0.0;
$totalDriftMs = 0.0;
$maxDriftMs = 0.0;
$expectedTickAt = $startedAt + 0.05;
$stopping = false;

$writeStatus = static function () use (
    $statusStore,
    $rooms,
    $config,
    $startedAt,
    &$state,
    &$tickCount,
    &$lastTickAt,
    &$tickDriftMs,
    &$totalDriftMs,
    &$maxDriftMs,
): void {
    $totals = $rooms->totals();
    if ($state === 'running' && $rooms->paused()) {
        $state = 'paused';
    } elseif ($state === 'paused' && !$rooms->paused() && $totals['connectionsActive'] > 0) {
        $state = 'running';
    } elseif ($state === 'running' && $totals['connectionsActive'] === 0) {
        $state = 'paused';
    }
    $now = microtime(true);
    $statusStore->write([
        'pid' => getmypid(),
        'state' => $state,
        'startedAt' => $startedAt,
        'uptime' => max(0.0, $now - $startedAt),
        'memory' => [
            'currentBytes' => memory_get_usage(true),
            'peakBytes' => memory_get_peak_usage(true),
        ],
        'limits' => [
            'connections' => $config->int('server.max_connections', 500),
            'connectionsPerRoom' => $config->int('rooms.max_players', 100),
            'messageBytes' => $config->int('server.max_message_bytes', 4096),
            'bytesPerSecond' => $config->int('server.websocket_bytes_per_second', 65_536),
            'framesPerSecond' => $config->int('server.websocket_frames_per_second', 60),
            'rooms' => $config->int('server.max_rooms', 32),
        ],
        'tick' => [
            'count' => $tickCount,
            'rate' => 20,
            'lastAt' => $lastTickAt,
            'driftMs' => $tickDriftMs,
            'totalDriftMs' => $totalDriftMs,
            'maxDriftMs' => $maxDriftMs,
        ],
        'network' => [
            'inboundMessages' => $totals['messagesIn'],
            'inboundBytes' => $totals['bytesIn'],
            'outboundBytes' => $totals['bytesOut'],
        ],
        'totals' => $totals,
        'rooms' => $rooms->roomStatus($now),
        'connections' => $rooms->connectionStatus(),
    ]);
};

$simulationTimer = EventLoop::repeat(0.05, static function () use (
    $rooms,
    &$tickCount,
    &$lastTickAt,
    &$tickDriftMs,
    &$totalDriftMs,
    &$maxDriftMs,
    &$expectedTickAt,
    &$stopping,
): void {
    if ($stopping) {
        return;
    }
    $now = microtime(true);
    $drift = ($now - $expectedTickAt) * 1000.0;
    $expectedTickAt += 0.05;
    if ($expectedTickAt < $now - 0.25) {
        $expectedTickAt = $now + 0.05;
    }
    $tickDriftMs = $drift;
    $totalDriftMs += abs($drift);
    $maxDriftMs = max($maxDriftMs, abs($drift));
    ++$tickCount;
    $lastTickAt = $now;
    $rooms->tick($now, 0.05);
});
$snapshotTimer = EventLoop::repeat(0.1, static fn () => $rooms->sendSnapshots(microtime(true)));
$persistenceTimer = EventLoop::repeat(
    max(1.0, (float) $config->int('rooms.persist_interval_seconds', 5)),
    static function () use ($rooms, $logger): void {
        try {
            $rooms->persist();
        } catch (Throwable $exception) {
            $logger->error('Periodic player persistence failed.', ['exceptionClass' => $exception::class]);
        }
    },
);
$housekeepingTimer = EventLoop::repeat(1.0, static fn () => $rooms->housekeeping(microtime(true)));
$commandTimer = EventLoop::repeat(0.5, static function () use ($commandPoller, $rooms, $logger): void {
    $command = $commandPoller->poll();
    if ($command === null) {
        return;
    }
    try {
        match ($command['action']) {
            'pause' => $rooms->setPaused(true),
            'resume' => $rooms->setPaused(false),
            'disconnect' => $rooms->disconnectAll($command['connectionId']),
            default => null,
        };
    } catch (Throwable $exception) {
        $logger->warning('Runtime command execution failed.', ['action' => $command['action'], 'exceptionClass' => $exception::class]);
    }
});
$statusTimer = EventLoop::repeat(
    max(0.25, (float) $config->int('server.status_interval_seconds', 1)),
    static function () use ($writeStatus, $logger): void {
        try {
            $writeStatus();
        } catch (Throwable $exception) {
            $logger->error('Runtime status write failed.', ['exceptionClass' => $exception::class]);
        }
    },
);

$state = 'paused';
try {
    $writeStatus();
} catch (Throwable $exception) {
    $logger->error('Initial runtime status write failed.', ['exceptionClass' => $exception::class]);
}
$logger->info('Authoritative game server listening.', ['host' => $host, 'port' => $port]);

$shutdown = EventLoop::getSuspension();
$signalIds = [];
foreach ([defined('SIGINT') ? SIGINT : null, defined('SIGTERM') ? SIGTERM : null] as $signal) {
    if ($signal === null) {
        continue;
    }
    try {
        $signalIds[] = EventLoop::onSignal($signal, static function () use ($shutdown, &$stopping, &$state, $writeStatus, $logger): void {
            if ($stopping) {
                return;
            }
            $stopping = true;
            $state = 'stopping';
            $logger->info('Graceful game server shutdown requested.');
            try {
                $writeStatus();
            } catch (Throwable) {
            }
            $shutdown->resume();
        });
    } catch (Throwable) {
        // Unsupported signal facilities do not prevent normal admin control.
    }
}

$shutdown->suspend();

foreach ([$simulationTimer, $snapshotTimer, $persistenceTimer, $housekeepingTimer, $commandTimer, $statusTimer] as $timer) {
    EventLoop::cancel($timer);
}
foreach ($signalIds as $signalId) {
    EventLoop::cancel($signalId);
}
$state = 'stopping';
$http->stop();
try {
    $rooms->flushAndPause();
} catch (Throwable $exception) {
    $logger->error('Final player persistence failed.', ['exceptionClass' => $exception::class]);
}
$state = 'stopped';
try {
    $writeStatus();
} catch (Throwable $exception) {
    $logger->error('Final runtime status write failed.', ['exceptionClass' => $exception::class]);
}
$logger->info('Authoritative game server stopped.');
