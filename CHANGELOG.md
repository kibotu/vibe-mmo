# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0] - 2026-09-28

### Added

- Separate `singleplayer/` and `multiplayer/` entrypoints that share one engine in
  `client/`, so the renderer, simulation, UI, and protocol code cannot drift between
  the two builds.
- `deploy.sh`, a repeatable deployment tool with two modes: FTPS synchronization of the
  multiplayer client and PHP pages, and a Docker Compose mode for the production stack.
- `database.prefix` configuration. Every table, index, and foreign-key constraint this
  application owns is prefixed (`mmo_rooms`, `mmo_players`, `mmo_player_sessions`,
  `mmo_schema_migrations`), keeping the schema isolated on a shared database.
- `Database::table()`, which resolves a table name to its prefixed identifier and
  throws on an unknown name instead of emitting an invalid identifier.
- Per-client Vite configurations sharing a factory in `client/vite.config.shared.ts`.
- `singleplayer/README.md` and `multiplayer/README.md`.

### Changed

- The lobby link and `app.game_url` no longer use `?server=1`; the entrypoint
  determines offline versus multiplayer mode.
- The GitHub Pages workflow publishes `singleplayer/dist`, and backend CI builds both
  clients instead of a single root bundle.
- The Docker images build and serve the multiplayer entrypoint.
- `deploy.sh` installs locked production Composer dependencies into a staging tree, so
  running a deployment no longer strips dev packages from `backend/vendor`.

### Security

- FTPS certificate verification is enabled by default; `--allow-insecure-tls` is an
  explicit, documented escape hatch that only disables certificate checks.
- Remote `secrets.yml` and `runtime/` are excluded from mirror deletion, so a
  destructive sync cannot destroy live secrets or runtime state.
- The deploy migration endpoint is protected by a random request token of which only
  the hash is embedded in the file, and it is removed by a cleanup trap on success,
  failure, or interruption.
- `deploy.sh` fails closed on a dirty worktree, placeholder secrets, a missing secrets
  file, or a group- or world-readable secrets file.
- `node_modules` and secret-bearing files were removed from the Git index, and
  `.gitignore` was extended to cover dependencies, secrets, keys, runtime state, and
  generated client output.

## [0.2.0] - 2026-09-25

### Added

- Authoritative PHP 8.5 and MariaDB backend with a supervised WebSocket game daemon,
  room ownership, persistence, and reconnect windows.
- Lobby, guest session cookies, one-time WebSocket tickets, and an admin dashboard.
- Docker Compose stacks for local development and the production VPS.
- Prediction and snapshot interpolation in the browser.
- Backend CI and deployment documentation.

### Fixed

- PHP 8.5 compatibility for `Pdo\Mysql` attributes.
- Supervisor now runs the game daemon as `www-data`.
- Room listing query `GROUP BY` correctness.
- nginx routing for `/` and `/game/`, and Vite HMR/proxy routing.
- Reconnect input watermarks and local movement prediction/reconciliation.
- Interpolation clock used browser milliseconds against server seconds.
- HUD and inventory DOM update throttling.

## [0.1.0] - 2026-09-24

### Added

- Offline Three.js and TypeScript client rendering a procedural Payon Forest slice,
  with movement, targeting, combat, loot, inventory, and camera controls.
- GitHub Actions pipeline publishing the static client to GitHub Pages.

[Unreleased]: https://github.com/kibotu/vibe-mmo/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/kibotu/vibe-mmo/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/kibotu/vibe-mmo/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/kibotu/vibe-mmo/compare/5e0c524...v0.1.0
