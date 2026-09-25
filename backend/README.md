# Payon Forest PHP backend

PHP 8.5.9 and MariaDB 10.11 power the lobby, persistence, and authoritative WebSocket world. The frontend remains TypeScript/Three.js; no Node.js service runs in the game loop.

## Deployment runtime contract

The supplied `phpinfo()` is from PHP-FPM 8.5.9. It confirms the web runtime has PDO MySQL, OpenSSL, JSON, cURL, mbstring, sodium, sessions, and the standard stream API. It does **not** show `pcntl` or `sockets` as loaded modules.

The application therefore:

- targets PHP `>=8.5 <8.6`;
- uses `stream_socket_*` through Amp rather than requiring `ext-sockets`;
- treats signal handling as optional and runs without `ext-pcntl`;
- uses Symfony YAML and therefore does not require a YAML extension;
- does not use Redis even though the host happens to load it.

`phpinfo()` describes FPM only. Before deploying the long-running daemon, run `php -m`, `composer check-platform-reqs --no-dev`, and a short `stream_socket_server()` check from the actual CLI binary. A host that does not permit persistent CLI processes cannot run the multiplayer server regardless of its PHP extensions.

## Local stack

```bash
cp backend/secrets.yml.example backend/secrets.yml
docker compose up --build
```

Open:

- Lobby: <http://127.0.0.1:18081/>
- Admin: <http://127.0.0.1:18081/admin.php>
- Health: <http://127.0.0.1:18081/api/health.php>
- MariaDB: `127.0.0.1:33081`

The uncommon web and database ports avoid Trail's `18000` and `33071` development listeners. MariaDB is bound to loopback only.

`secrets.yml` is the local source of truth. The one-shot Compose `config` service converts its database fields into root-only files for MariaDB's `_FILE` mechanism; PHP reads the YAML directly. No database password is duplicated in `compose.yaml`.

## Runtime model

- nginx serves the public directory, proxies PHP to PHP-FPM, and proxies `/ws` to the game daemon.
- One supervised PHP daemon owns active rooms and their worlds in RAM.
- The world advances at 20 Hz and sends snapshots at 10 Hz.
- Browsers send sequenced intents, never positions or authoritative results.
- MySQL stores guest identity, room assignment, HP, inventory, and periodic position snapshots.
- Socket loss does not delete a player. The room pauses while empty, retains player state for the configured grace period, and is discarded after the empty-room TTL.
- A daemon restart closes sockets but reloads durable player state. Clients fetch a new one-time ticket and reconnect.

The process survives HTTP requests because Supervisor keeps `bin/game-server.php` alive. PHP-FPM never runs the WebSocket listener; nginx sends WebSocket upgrades to the separate long-running process.

For production, run the complete stack on the Docker VPS with:

```bash
docker compose -f compose.prod.yaml up -d --build
```

The FTP/FPM host is not a suitable game-server host.

## Administration

`admin.php` reads the daemon's atomically written status file. It shows process state, room/tick data, memory, connection metadata, and traffic counters. It never reads secrets for display.

The Docker stack configures a Supervisor control socket owned by the PHP-FPM user. The page can start, stop, restart, pause, resume, or disconnect clients through fixed commands. It does not mount or call the Docker API. A production systemd template is provided in `docker/systemd/`; deployments where PHP-FPM lacks a safe control identity remain read-only.

## Backend verification

```bash
cd backend
composer install
composer test
composer lint
```

The FTP/public-root deployment layout, TLS reverse proxy, and persistent-process requirements are documented in [DEPLOYMENT.md](DEPLOYMENT.md).
