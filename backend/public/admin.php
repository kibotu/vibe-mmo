<?php declare(strict_types=1);

use Mmo\Http\Application;
use Mmo\Http\SecurityHeaders;
use Mmo\Runtime\AtomicJsonFile;
use Mmo\Runtime\RuntimeCommand;
use Mmo\Runtime\RuntimePaths;
use Mmo\Runtime\StatusStore;

require dirname(__DIR__) . '/vendor/autoload.php';

SecurityHeaders::apply(true);
$e = static fn (mixed $value): string => htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
$authenticated = false;
$csrf = '';
$status = null;
$readOnly = true;
$app = null;
$requestMethod = $_SERVER['REQUEST_METHOD'] ?? 'GET';

try {
    $app = Application::boot();
    $app->admin->startSession();
    $authenticated = $app->admin->authenticated();
    $csrf = $app->csrf->issue();
    $paths = new RuntimePaths($app->config);
    $status = (new StatusStore(new AtomicJsonFile($paths->statusFile)))->read();
    $readOnly = $app->control->readOnly();

    if ($requestMethod === 'POST') {
        if (!$app->csrf->validate($_POST['csrf'] ?? null)) {
            $_SESSION['admin_flash'] = ['error', 'The security token expired. Refresh and try again.'];
            header('Location: admin.php', true, 303);
            exit;
        }

        $action = is_string($_POST['action'] ?? null) ? $_POST['action'] : '';
        if ($action === 'login' && !$authenticated) {
            if ($app->admin->login($_POST['username'] ?? null, $_POST['password'] ?? null)) {
                $_SESSION['admin_flash'] = ['success', 'Administrator session started.'];
            } else {
                usleep(250_000);
                $_SESSION['admin_flash'] = ['error', 'Invalid administrator credentials.'];
            }
            header('Location: admin.php', true, 303);
            exit;
        }
        if ($action === 'logout' && $authenticated) {
            $app->admin->logout();
            $_SESSION['admin_flash'] = ['success', 'Administrator session ended.'];
            header('Location: admin.php', true, 303);
            exit;
        }
        if (!$authenticated) {
            header('Location: admin.php', true, 303);
            exit;
        }

        try {
            if (in_array($action, ['start', 'stop', 'restart'], true)) {
                $result = $app->control->execute($action);
                $_SESSION['admin_flash'] = [$result->ok ? 'success' : 'error', $result->message];
            } elseif (in_array($action, ['pause', 'resume', 'disconnect'], true)) {
                $connectionId = isset($_POST['connectionId']) && is_string($_POST['connectionId'])
                    ? $_POST['connectionId']
                    : null;
                if ($connectionId !== null && preg_match('/^[0-9a-f-]{36}$/Di', $connectionId) !== 1) {
                    throw new InvalidArgumentException('Invalid connection identifier.');
                }
                (new RuntimeCommand(new AtomicJsonFile($paths->commandFile)))->write($action, $connectionId);
                $_SESSION['admin_flash'] = ['success', ucfirst($action) . ' command queued.'];
            } else {
                $_SESSION['admin_flash'] = ['error', 'Unknown dashboard action.'];
            }
        } catch (Throwable) {
            $_SESSION['admin_flash'] = ['error', 'The command could not be queued.'];
        }
        header('Location: admin.php', true, 303);
        exit;
    }
} catch (Throwable) {
    $authenticated = false;
}

$flash = $_SESSION['admin_flash'] ?? null;
unset($_SESSION['admin_flash']);
$formatBytes = static function (int|float $bytes): string {
    $bytes = max(0, (int) $bytes);
    if ($bytes >= 1_048_576) {
        return number_format($bytes / 1_048_576, 1) . ' MiB';
    }
    if ($bytes >= 1024) {
        return number_format($bytes / 1024, 1) . ' KiB';
    }

    return $bytes . ' B';
};
?><!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.svg" type="image/svg+xml">
    <title>MMO Operations</title>
    <style>
        :root{color-scheme:dark;--bg:#09110e;--panel:#111d18;--panel2:#17261f;--line:#2a3c32;--text:#edf2e7;--muted:#94a59a;--green:#75c477;--gold:#e6c875;--red:#e47e70}*{box-sizing:border-box}body{margin:0;background:linear-gradient(145deg,#07100c,#102119);color:var(--text);font:14px/1.5 system-ui,sans-serif;min-height:100vh}.shell{width:min(1280px,calc(100% - 30px));margin:auto;padding:28px 0 60px}header{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:22px}.brand{font-size:12px;letter-spacing:.16em;text-transform:uppercase;color:var(--gold);font-weight:800}.brand span{display:inline-grid;place-items:center;border:1px solid var(--gold);width:34px;height:34px;border-radius:9px;margin-right:10px}.sub{color:var(--muted);font-size:.83rem}h1{font:2rem Georgia,serif;margin:5px 0}.panel{background:rgba(17,29,24,.92);border:1px solid var(--line);border-radius:14px;padding:20px;margin:16px 0;box-shadow:0 16px 50px rgba(0,0,0,.18)}.login{max-width:460px;margin:9vh auto}.field{display:grid;gap:6px;margin:14px 0}label{font-size:.72rem;text-transform:uppercase;letter-spacing:.1em;color:var(--muted);font-weight:800}input,button{font:inherit;border-radius:9px}input{width:100%;padding:11px 12px;background:#08110d;border:1px solid var(--line);color:var(--text)}button,.button{border:1px solid transparent;padding:10px 14px;background:#284b39;color:var(--text);font-weight:750;cursor:pointer;text-decoration:none;display:inline-flex;justify-content:center;align-items:center;gap:6px}.primary{background:linear-gradient(135deg,var(--gold),#c3a24d);color:#152019}.danger{background:#48251f;border-color:#6f392f}.ghost{background:transparent;border-color:var(--line)}button:hover,.button:hover{filter:brightness(1.12)}button:disabled{opacity:.38;cursor:not-allowed}.flash{padding:11px 13px;border-radius:9px;margin-bottom:15px}.flash.success{background:rgba(117,196,119,.12);border:1px solid rgba(117,196,119,.35)}.flash.error{background:rgba(228,126,112,.1);border:1px solid rgba(228,126,112,.35);color:#ffc4bb}.state{display:inline-flex;align-items:center;gap:7px;padding:5px 9px;border-radius:99px;background:rgba(117,196,119,.1);color:var(--green);font-weight:800;text-transform:uppercase;font-size:.7rem;letter-spacing:.08em}.state:before{content:"";width:7px;height:7px;border-radius:50%;background:currentColor}.state.paused,.state.stopped,.state.stopping{color:var(--gold)}.actions{display:flex;flex-wrap:wrap;gap:8px}.metrics{display:grid;grid-template-columns:repeat(6,minmax(130px,1fr));gap:10px}.metric{padding:14px;background:var(--panel2);border:1px solid rgba(255,255,255,.04);border-radius:10px}.metric small{display:block;text-transform:uppercase;letter-spacing:.08em;color:var(--muted);font-size:.65rem}.metric strong{display:block;font-size:1.18rem;margin-top:3px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.table-wrap{overflow:auto;border:1px solid var(--line);border-radius:10px}table{width:100%;border-collapse:collapse;white-space:nowrap}th,td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--line)}th{font-size:.66rem;text-transform:uppercase;letter-spacing:.09em;color:var(--muted);background:#0c1712}td{font-variant-numeric:tabular-nums}tr:last-child td{border-bottom:0}.empty{padding:24px;color:var(--muted);text-align:center}.topline{display:flex;justify-content:space-between;align-items:flex-start;gap:20px;margin-bottom:16px}.topline h2{font:1.35rem Georgia,serif;margin:0}.topline p{margin:3px 0;color:var(--muted)}code{color:var(--gold)}footer{color:var(--muted);font-size:.76rem;margin-top:20px}@media(max-width:980px){.metrics{grid-template-columns:repeat(3,1fr)}.grid{grid-template-columns:1fr}}@media(max-width:600px){.metrics{grid-template-columns:repeat(2,1fr)}header{align-items:flex-start}.shell{width:min(100% - 20px,1280px)}}
    </style>
</head>
<body>
<main class="shell">
    <header><div><div class="brand"><span>M</span>MMO Operations</div><h1>Runtime command deck</h1><div class="sub">Sanitized process, simulation, room, connection, memory, and network telemetry.</div></div><?php if ($authenticated): ?><form method="post"><input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="logout"><button class="ghost">Sign out</button></form><?php endif; ?></header>
    <?php if (is_array($flash)): ?><div class="flash <?= $flash[0] === 'success' ? 'success' : 'error' ?>" role="status"><?= $e($flash[1] ?? '') ?></div><?php endif; ?>

    <?php if (!$authenticated): ?>
        <section class="panel login">
            <h2>Administrator sign in</h2>
            <p class="sub">Credentials and all action tokens are read from the private server configuration.</p>
            <form method="post">
                <input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="login">
                <div class="field"><label for="username">Username</label><input id="username" name="username" autocomplete="username" required autofocus></div>
                <div class="field"><label for="password">Password</label><input id="password" name="password" type="password" autocomplete="current-password" required></div>
                <button class="primary" style="width:100%">Authenticate</button>
            </form>
        </section>
    <?php else: ?>
        <section class="panel">
            <div class="topline"><div><h2>Process controls</h2><p><?= $readOnly ? 'Read-only: no configured local process controller is available.' : 'Fixed local command mapping; arbitrary services and arguments are never accepted.' ?></p></div><span class="state <?= $e($status['state'] ?? 'stopped') ?>"><?= $e($status['state'] ?? 'unknown') ?></span></div>
            <div class="actions">
                <?php foreach (['start', 'stop', 'restart'] as $action): ?><form method="post"><input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="<?= $e($action) ?>"><button class="ghost"<?= $readOnly ? ' disabled' : '' ?>><?= ucfirst($action) ?></button></form><?php endforeach; ?>
                <form method="post"><input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="pause"><button<?= ($status['state'] ?? '') === 'paused' ? ' disabled' : '' ?>>Pause simulation</button></form>
                <form method="post"><input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="resume"><button<?= ($status['state'] ?? '') !== 'paused' ? ' disabled' : '' ?>>Resume simulation</button></form>
                <form method="post"><input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="disconnect"><button class="danger">Disconnect all</button></form>
            </div>
        </section>

        <?php if ($status === null): ?>
            <section class="panel empty">Runtime status is unavailable. Start the game server to begin reporting.</section>
        <?php else: ?>
            <section class="panel metrics" aria-label="Key metrics">
                <div class="metric"><small>PID</small><strong><?= $e($status['pid'] ?? 0) ?></strong></div>
                <div class="metric"><small>Uptime</small><strong><?= number_format((float) ($status['uptime'] ?? 0), 0) ?>s</strong></div>
                <div class="metric"><small>Memory</small><strong><?= $e($formatBytes($status['memory']['currentBytes'] ?? 0)) ?></strong></div>
                <div class="metric"><small>Tick</small><strong><?= $e($status['tick']['count'] ?? 0) ?></strong></div>
                <div class="metric"><small>Drift</small><strong><?= number_format((float) ($status['tick']['driftMs'] ?? 0), 1) ?>ms</strong></div>
                <div class="metric"><small>Sockets</small><strong><?= $e($status['totals']['connectionsActive'] ?? 0) ?></strong></div>
            </section>
            <div class="grid">
                <section class="panel"><div class="topline"><div><h2>Rooms</h2><p>Retained players count toward offline grace.</p></div></div><div class="table-wrap"><table><thead><tr><th>Room</th><th>State</th><th>Players</th><th>Retained</th><th>Actors</th><th>Items</th><th>Tick</th></tr></thead><tbody><?php if (($status['rooms'] ?? []) === []): ?><tr><td class="empty" colspan="7">No active room</td></tr><?php endif; ?><?php foreach ($status['rooms'] as $room): ?><tr><td><strong><?= $e($room['name']) ?></strong><br><code><?= $e($room['code']) ?></code></td><td><?= $e($room['state']) ?></td><td><?= $e($room['connectedPlayers']) ?> / <?= $e($room['maxPlayers']) ?></td><td><?= $e($room['retainedPlayers']) ?></td><td><?= $e($room['actors']) ?></td><td><?= $e($room['items']) ?></td><td><?= $e($room['tick']) ?></td></tr><?php endforeach; ?></tbody></table></div></section>
                <section class="panel"><div class="topline"><div><h2>Network &amp; process</h2><p>Application-level payload counters.</p></div></div><div class="table-wrap"><table><tbody><tr><th>Peak memory</th><td><?= $e($formatBytes($status['memory']['peakBytes'] ?? 0)) ?></td></tr><tr><th>Inbound messages</th><td><?= $e($status['network']['inboundMessages'] ?? 0) ?></td></tr><tr><th>Inbound bytes</th><td><?= $e($status['network']['inboundBytes'] ?? 0) ?></td></tr><tr><th>Outbound bytes</th><td><?= $e($status['network']['outboundBytes'] ?? 0) ?></td></tr><tr><th>Accepted sockets</th><td><?= $e($status['totals']['connectionsAccepted'] ?? 0) ?></td></tr><tr><th>Rejected messages</th><td><?= $e($status['totals']['messagesRejected'] ?? 0) ?></td></tr><tr><th>Periodic writes</th><td><?= $e($status['totals']['persistenceWrites'] ?? 0) ?></td></tr><tr><th>Tick drift total</th><td><?= number_format((float) ($status['tick']['totalDriftMs'] ?? 0), 1) ?> ms</td></tr></tbody></table></div></section>
            </div>
            <section class="panel"><div class="topline"><div><h2>Connections</h2><p>Only trusted-proxy client addresses are shown.</p></div></div><div class="table-wrap"><table><thead><tr><th>Connection</th><th>Player</th><th>Forwarded IP</th><th>Room</th><th>Last seen</th><th>Inbound</th><th>Outbound</th><th>Action</th></tr></thead><tbody><?php if (($status['connections'] ?? []) === []): ?><tr><td class="empty" colspan="8">No connected clients</td></tr><?php endif; ?><?php foreach ($status['connections'] as $connection): ?><tr><td><code><?= $e(substr((string) $connection['connectionId'], 0, 8)) ?></code></td><td><?= $e($connection['playerName']) ?><br><small><?= $e($connection['playerId']) ?></small></td><td><code><?= $e($connection['forwardedIp']) ?></code></td><td><?= $e($connection['room']) ?></td><td><?= number_format((float) $connection['lastSeen'], 3) ?></td><td><?= $e($connection['inboundMessages']) ?> / <?= $e($connection['inboundBytes']) ?>B</td><td><?= $e($connection['outboundBytes']) ?>B</td><td><form method="post"><input type="hidden" name="csrf" value="<?= $e($csrf) ?>"><input type="hidden" name="action" value="disconnect"><input type="hidden" name="connectionId" value="<?= $e($connection['connectionId']) ?>"><button class="danger">Disconnect</button></form></td></tr><?php endforeach; ?></tbody></table></div></section>
        <?php endif; ?>
        <footer>Limits: <?= $e($status['limits']['connections'] ?? 0) ?> sockets · <?= $e($status['limits']['messageBytes'] ?? 0) ?> bytes/message · <?= $e($status['limits']['framesPerSecond'] ?? 0) ?> frames/s. Status updates atomically once per second.</footer>
    <?php endif; ?>
</main>
<?php if ($authenticated): ?><script>setTimeout(()=>location.reload(),2000)</script><?php endif; ?>
</body>
</html>
