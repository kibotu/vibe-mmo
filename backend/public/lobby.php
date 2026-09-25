<?php declare(strict_types=1);

use Mmo\Auth\GuestName;
use Mmo\Http\Application;
use Mmo\Http\SecurityHeaders;

require dirname(__DIR__) . '/vendor/autoload.php';

SecurityHeaders::apply(true);
$e = static fn (mixed $value): string => htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
$appName = 'Payon Forest';
$rooms = [];
$identity = null;
$csrf = '';
$error = null;
$submittedName = '';
$gameUrl = '/game/?server=1';

try {
    $application = Application::boot();
    $appName = $application->config->string('app.name', 'Payon Forest');
    $gameUrl = $application->config->string('app.game_url', '/game/?server=1');
    $csrf = $application->csrf->issue();
    $rooms = $application->rooms->publicRooms();
    $identity = $application->guests->authenticate($application->cookies->guestCredentials($_COOKIE));

    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
        if (!$application->csrf->validate($_POST['csrf'] ?? null)) {
            $error = 'Your form expired. Please try again.';
        } else {
            $submittedName = is_string($_POST['name'] ?? null) ? $_POST['name'] : '';
            $roomCode = is_string($_POST['room'] ?? null) ? $_POST['room'] : '';
            $created = $application->guests->create($submittedName, $roomCode);
            if ($created === null) {
                $name = GuestName::normalize($submittedName);
                $error = $name === null
                    ? 'Choose a name between 2 and 24 letters or numbers.'
                    : 'That room is no longer available.';
            } else {
                $application->guests->setCookie($created->selector, $created->validator);
                header('Location: lobby.php?joined=1', true, 303);
                exit;
            }
        }
    }
} catch (Throwable) {
    http_response_code(503);
    $error = 'The lobby is temporarily unavailable. Please try again shortly.';
}

$selectedRoom = $_POST['room'] ?? ($identity?->roomCode ?? $rooms[0]['code'] ?? '');
$justJoined = ($_GET['joined'] ?? '') === '1';
?><!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="icon" href="/favicon.svg" type="image/svg+xml">
    <title><?= $e($appName) ?> · Lobby</title>
    <style>
        :root{color-scheme:dark;--ink:#f5f0df;--muted:#b8c2aa;--panel:rgba(13,29,23,.88);--line:rgba(226,210,145,.24);--gold:#e7ca77;--green:#79b56b;--danger:#e18a71}
        *{box-sizing:border-box}body{margin:0;min-height:100vh;font:16px/1.5 system-ui,sans-serif;color:var(--ink);background:radial-gradient(circle at 50% 15%,#315d45 0,#173426 38%,#08130f 100%);overflow-x:hidden}
        body:before{content:"";position:fixed;inset:0;pointer-events:none;background:linear-gradient(115deg,transparent 0 48%,rgba(255,255,255,.035) 49% 51%,transparent 52%),linear-gradient(0deg,rgba(4,12,8,.5),transparent 45%);z-index:-1}
        .shell{width:min(1080px,calc(100% - 32px));margin:auto;padding:54px 0 70px}.brand{display:flex;align-items:center;gap:14px;letter-spacing:.14em;text-transform:uppercase;font-size:.78rem;color:var(--gold)}
        .sigil{display:grid;place-items:center;width:44px;height:44px;border:1px solid var(--gold);border-radius:50%;font-size:1.4rem;box-shadow:inset 0 0 0 5px rgba(231,202,119,.08)}
        .hero{display:grid;grid-template-columns:1.15fr .85fr;gap:48px;align-items:center;margin:48px 0}.eyebrow{color:var(--green);font-weight:700;letter-spacing:.16em;text-transform:uppercase;font-size:.74rem}.hero h1{font:clamp(3rem,8vw,6.5rem)/.88 Georgia,serif;letter-spacing:-.055em;margin:14px 0 24px;max-width:760px}.hero h1 em{display:block;color:var(--gold);font-weight:400}.lede{color:var(--muted);font-size:1.08rem;max-width:620px}
        .panel{background:var(--panel);border:1px solid var(--line);border-radius:22px;box-shadow:0 24px 80px rgba(0,0,0,.28);backdrop-filter:blur(12px)}.join{padding:30px}.join h2,.rooms h2{margin:0 0 5px;font:1.55rem Georgia,serif}.hint{color:var(--muted);font-size:.88rem;margin:0 0 22px}.field{display:grid;gap:8px;margin:16px 0}.field label{font-size:.75rem;font-weight:700;letter-spacing:.1em;text-transform:uppercase;color:#d9dec9}input,select{width:100%;border:1px solid rgba(231,236,218,.18);border-radius:12px;background:rgba(3,12,8,.64);color:var(--ink);padding:13px 14px;font:inherit}input:focus,select:focus{outline:2px solid rgba(121,181,107,.75);outline-offset:1px}.button{display:inline-flex;justify-content:center;border:0;border-radius:12px;padding:14px 20px;font:700 .95rem system-ui;background:linear-gradient(135deg,#e7ca77,#c8a957);color:#172019;cursor:pointer;text-decoration:none;box-shadow:0 8px 28px rgba(199,169,87,.18)}.button:hover{filter:brightness(1.06)}.button.secondary{background:#284b39;color:var(--ink);border:1px solid rgba(231,236,218,.16);box-shadow:none}.button:disabled{opacity:.45;cursor:not-allowed}.full{width:100%}.notice,.alert{padding:13px 15px;border-radius:11px;margin:0 0 18px;font-size:.9rem}.notice{background:rgba(121,181,107,.12);border:1px solid rgba(121,181,107,.35)}.alert{background:rgba(225,138,113,.1);border:1px solid rgba(225,138,113,.35);color:#ffc7b7}
        .rooms{margin-top:24px;padding:30px}.room-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px}.room-card{padding:17px;border-radius:14px;background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.08)}.room-card strong{display:block;margin-bottom:5px}.room-meta{display:flex;justify-content:space-between;color:var(--muted);font-size:.8rem}.dot{display:inline-block;width:7px;height:7px;border-radius:50%;background:var(--green);margin-right:7px;box-shadow:0 0 10px var(--green)}.dot.paused{background:var(--gold);box-shadow:0 0 10px var(--gold)}
        .rules{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:24px}.rule{padding:18px;border-top:1px solid var(--line);color:var(--muted);font-size:.88rem}.rule b{display:block;color:var(--ink);font:1.08rem Georgia,serif;margin-bottom:5px}@media(max-width:780px){.hero{grid-template-columns:1fr;margin-top:32px}.rules{grid-template-columns:1fr}.shell{padding-top:30px}}
    </style>
</head>
<body>
<main class="shell">
    <div class="brand"><span class="sigil">P</span><span>Private Forest Server</span></div>
    <section class="hero">
        <div>
            <div class="eyebrow">Authoritative multiplayer</div>
            <h1>Payon<em>Forest</em></h1>
            <p class="lede">A quiet woodland where every step, swing, and pickup is decided by the server. Choose a name, enter an open room, and hunt at your own pace.</p>
        </div>
        <section class="panel join" aria-labelledby="join-title">
            <?php if ($identity !== null): ?>
                <h2 id="join-title">Welcome back, <?= $e($identity->name) ?></h2>
                <p class="hint">Your guest identity is secured in an HttpOnly cookie. Reconnect any time to retain your character.</p>
                <?php if ($justJoined): ?><div class="notice">Identity created. Your adventure is ready.</div><?php endif; ?>
                <a class="button full" href="<?= $e($gameUrl) ?>">Enter the forest</a>
            <?php elseif ($error !== null): ?>
                <h2 id="join-title">Create your traveler</h2>
                <div class="alert" role="alert"><?= $e($error) ?></div>
                <form method="post" action="lobby.php">
                    <input type="hidden" name="csrf" value="<?= $e($csrf) ?>">
                    <div class="field"><label for="name">Traveler name</label><input id="name" name="name" value="<?= $e($submittedName) ?>" minlength="2" maxlength="24" autocomplete="nickname" required autofocus></div>
                    <div class="field"><label for="room">Destination</label><select id="room" name="room" required><?php foreach ($rooms as $room): ?><option value="<?= $e($room['code']) ?>"<?= $selectedRoom === $room['code'] ? ' selected' : '' ?>><?= $e($room['name']) ?> · <?= $e($room['status']) ?></option><?php endforeach; ?></select></div>
                    <button class="button full" type="submit"<?= $rooms === [] ? ' disabled' : '' ?>>Create guest &amp; join</button>
                </form>
            <?php else: ?>
                <h2 id="join-title">Create your traveler</h2>
                <p class="hint">No account or password. Your browser receives a signed, HttpOnly guest session.</p>
                <form method="post" action="lobby.php">
                    <input type="hidden" name="csrf" value="<?= $e($csrf) ?>">
                    <div class="field"><label for="name">Traveler name</label><input id="name" name="name" minlength="2" maxlength="24" autocomplete="nickname" placeholder="e.g. Maple" required autofocus></div>
                    <div class="field"><label for="room">Destination</label><select id="room" name="room" required><?php foreach ($rooms as $room): ?><option value="<?= $e($room['code']) ?>"<?= $selectedRoom === $room['code'] ? ' selected' : '' ?>><?= $e($room['name']) ?> · <?= $e($room['status']) ?></option><?php endforeach; ?></select></div>
                    <button class="button full" type="submit"<?= $rooms === [] ? ' disabled' : '' ?>>Create guest &amp; join</button>
                </form>
            <?php endif; ?>
        </section>
    </section>
    <section class="panel rooms">
        <h2>Woodland status</h2>
        <p class="hint">Room information is sanitized and contains no direct database or process details.</p>
        <div class="room-grid">
            <?php if ($rooms === []): ?><div class="alert">No rooms are available.</div><?php endif; ?>
            <?php foreach ($rooms as $room): ?>
                <article class="room-card"><strong><span class="dot<?= $room['status'] === 'paused' ? ' paused' : '' ?>"></span><?= $e($room['name']) ?></strong><div class="room-meta"><span><?= $e($room['code']) ?></span><span><?= $e($room['playerCount']) ?> / <?= $e($room['maxPlayers']) ?></span></div></article>
            <?php endforeach; ?>
        </div>
    </section>
    <section class="rules" aria-label="Game rules">
        <div class="rule"><b>Server-led movement</b>Send a destination cell. The server validates every path and all coordinates.</div>
        <div class="rule"><b>Small, useful loot</b>Collect Jellopy, bottles, Apples, mucus, and the occasional knife.</div>
        <div class="rule"><b>Come back later</b>Your player state is retained briefly when the socket closes and saved periodically.</div>
    </section>
</main>
</body>
</html>
