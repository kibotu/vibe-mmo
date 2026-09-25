# Payon Forest

A Three.js/TypeScript 2.5D RPG slice with an optional authoritative PHP multiplayer backend.

The static build remains a self-contained single-player study for GitHub Pages. The Docker backend adds a lobby, reconnectable guest identities, shared rooms, an authoritative PHP world, and an operator page.

## Multiplayer development stack

Requirements: Docker with Compose and Node.js 22 for local frontend tooling.

```bash
cp backend/secrets.yml.example backend/secrets.yml
docker compose up --build
```

Open:

- Lobby: <http://127.0.0.1:18081/>
- Admin: <http://127.0.0.1:18081/admin.php>
- Health: <http://127.0.0.1:18081/api/health.php>

The web port is `18081`; MariaDB 10.11 is exposed on loopback at `33081`. Both avoid the ports already used by the Trail backend. PHP 8.5.9 matches the supplied deployment-server runtime.

Vite is served through nginx inside Compose, so TypeScript edits refresh through the same `http://127.0.0.1:18081/game/?server=1` origin. The daemon is supervised inside the PHP container and does not receive the Docker socket.

## Architecture

```text
browser / Three.js
    │ sequenced intents
    ▼
nginx /ws
    ▼
long-running PHP WebSocket daemon
    │ owns rooms, entities, combat, loot, inventory
    ├── 20 Hz fixed simulation
    └── 10 Hz JSON snapshots
             │
             ├── client prediction for own movement
             └── interpolation for remote actors

MySQL
    └── guest identity, room, HP, inventory, periodic position
```

The browser sends intents such as “move to this cell”, “target this actor”, or “use Apple”. It does not send authoritative position, damage, item ownership, cooldowns, or loot rolls. Socket loss leaves player state in RAM for a bounded reconnect window; MySQL restores durable state after a daemon restart.

See [docs/MULTIPLAYER.md](docs/MULTIPLAYER.md) for the wire protocol and lifecycle rules.

## Controls

- Left-click ground: move.
- Left-click an actor: target and attack.
- Click a floor item: pick it up.
- Right-drag: rotate camera.
- `Shift` + right-drag: change camera elevation.
- `Control` + right-drag: zoom.
- Mouse wheel: zoom.
- Double right-click: reset camera direction.
- `Shift` + double right-click: reset the full camera view.
- `F1`: attack the current target.
- `H`: eat an Apple when HP is below maximum.
- `I` or `Alt+E`: inventory.
- `Escape`: close the inventory.

The game intentionally has no audio.

## Offline frontend development

The existing single-player build remains available:

```bash
npm ci
npm run dev
```

Append `?profile=preRenewal2008` to compare the later camera profile or `?seed=N` for a deterministic offline world. Multiplayer is enabled by the lobby through `?server=1`; ordinary static/GitHub Pages launches stay offline.

## Verification

Frontend:

```bash
npm run typecheck
npm test
npm run build
```

Backend:

```bash
cd backend
composer install
composer lint
composer test
```

Build the deployable frontend beneath the PHP public root with:

```bash
./scripts/build-backend.sh
```

The resulting static game is written to `backend/public/game/`.

## Deployment

The supported production multiplayer deployment is the single Docker VPS described in [backend/DEPLOYMENT.md](backend/DEPLOYMENT.md):

```bash
docker compose -f compose.prod.yaml up -d --build
```

The existing FTP/FPM host can optionally host page-only PHP files, but it cannot sustain the authoritative WebSocket daemon; secrets and Composer dependencies belong in the private backend parent if that separate page deployment is used.

GitHub Pages can continue to publish the static `dist/` build. It cannot execute PHP, run the WebSocket daemon, or provide the lobby/admin pages.

The implementation uses procedural placeholder sprites and geometry. It does not ship extracted Ragnarok Online assets.
