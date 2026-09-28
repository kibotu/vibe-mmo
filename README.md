# Payon Forest

A Three.js/TypeScript 2.5D RPG slice with two independently deployable clients:

- **Singleplayer** — an offline client published to GitHub Pages.
- **Multiplayer** — an authoritative client served by the PHP/nginx backend.

The two clients share the rendering and simulation engine in `client/`, but have separate entrypoints and build pipelines. The singleplayer client never opens a socket. The multiplayer client boots a room session from the lobby and talks to the PHP game daemon.

## Repository layout

```text
client/         Shared engine, UI, simulation, and protocol code
singleplayer/   GitHub Pages entrypoint and offline build
multiplayer/    Backend entrypoint and multiplayer build
backend/        PHP daemon, lobby, admin, migrations, and Docker runtime
docs/           Protocol and deployment notes
deploy.sh       Repeatable FTPS sync and migration tool for this repository
```

## Singleplayer client

The singleplayer build is fully static and is deployed by `.github/workflows/deploy.yml` to GitHub Pages.

```bash
npm ci
npm run dev
npm run build:singleplayer
```

Open the local Vite URL. `?seed=N` and `?profile=preRenewal2008` remain available for deterministic testing.

The workflow uploads `singleplayer/dist/`; generated builds are not committed.

## Multiplayer backend

Requirements: Docker with Compose, PHP 8.5, Composer, and Node.js 22.

```bash
cp backend/secrets.yml.example backend/secrets.yml
# edit only this repository's backend/secrets.yml
docker compose up --build
```

Open:

- Lobby: <http://127.0.0.1:18081/>
- Multiplayer client: <http://127.0.0.1:18081/game/>
- Admin: <http://127.0.0.1:18081/admin.php>
- Health: <http://127.0.0.1:18081/api/health.php>

The local web port is `18081`; MariaDB 10.11 is exposed on loopback at `33081`. The PHP container runs the authoritative daemon under Supervisor. It does not receive the Docker socket.

The multiplayer client can also be run directly against a local backend:

```bash
npm run dev:multiplayer
```

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

MariaDB
    └── guest identity, room, HP, inventory, periodic position
```

The browser sends intents such as “move to this cell”, “target this actor”, or “use Apple”. It never sends authoritative position, damage, item ownership, cooldowns, or loot rolls. Socket loss leaves player state in RAM for a bounded reconnect window; MariaDB restores durable state after a daemon restart.

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

## Verification

Frontend:

```bash
npm run typecheck
npm test
npm run build:singleplayer
npm run build:multiplayer
```

Backend:

```bash
cd backend
composer install
composer lint
composer test
```

Build the multiplayer client beneath the PHP public root:

```bash
./scripts/build-backend.sh
```

The resulting static client is written to `backend/public/game/`.

## Deployment

### GitHub Pages

Pushes to `main` run the Pages workflow. It builds `singleplayer/` and publishes `singleplayer/dist/`. The Pages site cannot run PHP, WebSockets, or the admin dashboard.

### Docker VPS

The supported multiplayer runtime is the single Docker VPS described in [backend/DEPLOYMENT.md](backend/DEPLOYMENT.md):

```bash
docker compose -f compose.prod.yaml up -d --build
```

Terminate TLS at the VPS edge and proxy the public web port to the Compose web container.

### FTP page/static sync

`./deploy.sh` is a repeatable deployment tool modeled on the Trail workflow, but it reads **only this repository's `backend/secrets.yml`**. It never reads Trail's secrets.

```bash
./deploy.sh --dry-run --allow-dirty
./deploy.sh
```

FTP mode:

- builds and tests the multiplayer client;
- installs locked Composer dependencies;
- stages only production backend files and the multiplayer client;
- uses FTPS with certificate verification enabled by default;
- mirrors stale files safely while preserving remote secrets and runtime state;
- uploads a short-lived migration endpoint protected by a random request token;
- removes that endpoint in a cleanup trap;
- relies on the backend's checksum-based migration table, so reruns are idempotent.

The FTP host can publish PHP pages and the static multiplayer client, but it cannot run the authoritative WebSocket daemon. Use the Docker runtime for the daemon.

For a Docker VPS deployment that also runs migrations inside the PHP container:

```bash
./deploy.sh --mode docker
```

Useful options:

- `--dry-run` — preflight and build without remote changes.
- `--skip-migrations` — sync files without running migrations.
- `--no-delete` — do not remove stale remote files.
- `--allow-dirty` — intentionally deploy a modified worktree.
- `--allow-insecure-tls` — explicit certificate diagnostic escape hatch; do not use in production.

Never commit `backend/secrets.yml`, `.env` files, private keys, runtime state, or `vendor/`. `backend/secrets.yml.example` contains placeholders only.

The implementation uses procedural placeholder sprites and geometry. It does not ship extracted Ragnarok Online assets.
