# Backend deployment

## Recommended deployment: one Docker VPS

The multiplayer runtime needs a persistent PHP CLI process. The FTP/FPM host used by Trail is not capable of providing that. Deploy the complete game backend on an always-on Docker VPS instead:

```text
mmo.kibotu.net
        │
        ▼
Docker web (nginx + built Vite game)
        ├── /lobby.php, /admin.php, /api/* → php-fpm
        └── /ws                         → PHP game daemon
                                             │
                                             ▼
                                          MariaDB
```

On the VPS:

```bash
cp backend/secrets.yml.example backend/secrets.yml
# edit secrets.yml with production values
chmod 0640 backend/secrets.yml
# ensure the PHP-FPM worker can read it (official Debian images use group 33/www-data)
chgrp 33 backend/secrets.yml
docker compose -f compose.prod.yaml up -d --build
docker compose -f compose.prod.yaml ps
```

The production web image builds the Vite frontend during `docker build`; no `vendor/` directory or `.env` file needs to be uploaded through FTP. Composer dependencies are installed while the PHP image is built. The web container binds to loopback port `18081`; put the VPS's TLS reverse proxy in front of it, or change that published port deliberately.

The only public document root in the PHP container is `backend/public/`. `secrets.yml`, `src/`, `bin/`, `database/`, Composer files, and runtime state remain outside the web root.

### Production lifecycle

The PHP container runs Supervisor with:

- `php-fpm` for ordinary web requests;
- `game-server` for the long-running WebSocket process.

`admin.php` controls the `game-server` Supervisor program through a narrowly scoped local socket. The Docker socket is never mounted into PHP. Put `/admin.php` behind the VPS edge proxy's VPN or IP allowlist in addition to its password and CSRF protection; the application route is not an administrative network boundary.

The game daemon listens only on the private Compose network. nginx is the only public WebSocket endpoint. Terminate TLS at the VPS edge and preserve the `/ws` upgrade headers.

## FTP/FPM host: optional page-only target

The existing FTP host can still host a normal PHP page deployment, but it cannot host the game daemon. If that is intentionally separate from the Docker VPS:

```text
FTP root /                         Private
├── secrets.yml
├── composer.json
├── composer.lock
├── bin/
├── database/
├── runtime/                       Writable status/control state
├── src/
├── vendor/                        Installed/uploaded here
└── public/                        Public document root
    ├── admin.php
    ├── lobby.php
    ├── api/
    └── game/
```

A diagnostic placed in `backend/public/` must load Composer from the private parent directory:

```php
$autoload = dirname(__DIR__) . '/vendor/autoload.php';
```

The expected location is `backend/vendor/autoload.php`, never `backend/public/vendor/autoload.php`. Public entry points use `dirname(__DIR__)` (or `dirname(__DIR__, 2)` from `public/api/`) for this reason.

To prepare a page-only upload:

```bash
cd backend
composer install --no-dev --classmap-authoritative
```

Upload the resulting private-root `vendor/`, `src/`, `bin/`, `database/`, and `public/` directories. This can serve lobby/admin pages, but it will not provide WebSocket multiplayer without a separate always-on runtime. A one-request FPM script, cron tick, or long-lived HTTP request is not an acceptable substitute for the daemon.

Use FTPS with certificate verification. Do not copy Trail's old deployment script unchanged: it disabled TLS verification, used destructive mirror mode, and briefly exposed an unauthenticated migration endpoint.

## Configuration and migrations

`secrets.yml` is the source of truth for application environment, database credentials, allowed origins, room limits, and FTP settings. The production Docker `config` service converts the database fields into root-only files for MariaDB's `_FILE` mechanism; the PHP application reads the YAML directly.

Before starting a new production database:

1. Set `app.environment: production`.
2. Replace every `change-this-*` value, including the admin and database passwords.
3. Set `app.public_url`, `server.allowed_origins`, and session cookie security deliberately.
4. Keep MariaDB and the game port private.
5. Review `server.max_connections` and room limits against the VPS capacity.
6. Confirm the database volume backup policy.

The PHP container runs `php bin/migrate.php` before Supervisor starts. Do not expose a public migration endpoint.

MariaDB `_FILE` values are applied only when its data volume is initialized. Changing `database.password` in YAML does not rotate an existing database user. Use a controlled SQL rotation while the service is stopped, update the secret, and then restart; never use `docker compose down -v` as a password-rotation procedure because it destroys the database volume.

## Non-Docker process alternative

A systemd host may run `bin/game-server.php` directly, but it still needs a PHP CLI binary, a writable `runtime/`, a private listener, and a reverse proxy. Copy `docker/systemd/mmo-game.service.example` to `/etc/systemd/system/mmo-game.service`, adjust the user and paths, enable it with `systemctl enable --now mmo-game.service`, and verify:

```bash
php -v
php -m
composer check-platform-reqs --no-dev
php -r '$s=stream_socket_server("tcp://127.0.0.1:0", $e, $m); if (!$s) { fwrite(STDERR, "$m\n"); exit(1); } fclose($s); echo "stream sockets: ok\n";'
```

The application does not require `ext-sockets` or `ext-pcntl`. If PCNTL is unavailable, Supervisor/systemd termination is a hard stop; periodic persistence limits state loss to the configured persistence interval.

## Recovery semantics

A game server restart closes all sockets. Browsers fetch a fresh one-time ticket and reconnect. MySQL restores durable player identity, room assignment, HP, inventory, and periodic position; active room objects and monsters are rebuilt in RAM.
