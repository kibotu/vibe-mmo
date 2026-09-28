# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Touch controls in `client/src/input.ts`. One finger drags to rotate, two fingers pinch
  to zoom, and a two-finger vertical drag tilts. Taps and drags are separated by a 10px
  slop threshold and a 400ms hold limit, so a shaky tap still moves and a long press to
  look around does not issue a move command.
- Device-appropriate control hints. The help card teaches touch gestures on coarse
  pointers and mouse gestures elsewhere, from one markup tree.
- `viewport-fit=cover` plus `env(safe-area-inset-*)` padding on the HUD, so the readouts
  clear the notch and the home indicator without nudging each panel individually.

### Changed

- GitHub Actions now build on pushes to `main` only. The `pull_request` trigger and
  the non-main push trigger were removed, so branches no longer run CI and a broken
  push reaches `main` unverified.
- Release tags are named `0.1.0`, `0.2.0`, and `0.3.0` without a `v` prefix.
- The canvas backing store now follows the measured element size via `ResizeObserver`,
  with `devicePixelRatio` capped at 2, replacing a hardcoded 800x600 target rendered at
  `setPixelRatio(1)`. The camera aspect, screen-space picking, and damage-number
  projection all follow the live size. A HiDPI display is no longer upscaled from 1x.
- The 4:3 letterbox is gone and `#game-shell` fills the viewport on every device. This
  is a deliberate loss of the retro framing, and a large desktop window now rasterises
  roughly ten times the pixels the fixed 800x600 target did; 120fps median was measured
  at 5.0 megapixels, but that headroom is smaller on weaker GPUs.
- Camera drag sensitivity is normalised against fixed 800x600 and 600x600 reference
  dimensions instead of the live viewport, so a swipe covers the same angle on a phone
  and a desktop. Desktop feel is unchanged.
- Touch input runs on its own pointer path ahead of the mouse button checks, because
  touch reports `button: 0` and was previously being claimed by the mouse path. Hover
  raycasting is skipped for touch, which has no hover state to maintain.
- The hotbar grows to 76x60 and the inventory window to a 44px minimum target on coarse
  pointers, and the `F1`/`H`/`I` shortcut hints are hidden there.
- The welcome message and the inventory footer now say "click or tap" rather than
  "left-click", and the seed label is hidden on touch where it listed only key
  shortcuts.

### Added

- **Long-polling runtime**, for hosts that cannot run the WebSocket daemon. The
  shared hosting PHP has no `pcntl` or `sockets` extension and a 60-second
  execution limit, so a persistent process is impossible. `POST /api/poll.php`
  instead hydrates a room from the new `mmo_room_state` table, applies the
  caller's queued intents, steps the simulation by the elapsed wall-clock time in
  fixed 1/20s steps, and writes the room back. A `GET_LOCK` serialises concurrent
  pollers so two players cannot simulate one room at once, and a request may hold
  its response briefly so a quiet room costs no requests.
- `RoomConnection`, the small interface `Room` needs from a client connection.
  `ClientConnection` implements it unchanged, so the WebSocket path is untouched,
  and long polling supplies its own. Room, combat, loot, and inventory logic is
  shared between both transports rather than duplicated.
- `client/src/polling.ts`, a `WebSocketLike` transport backed by polling.
  `MultiplayerClient` is unchanged, so the same client drives either transport
  and `bootstrap.ts` only chooses the factory.
- `rooms.max_catch_up_seconds` bounds how much time one polled request may
  simulate, so a returning player never waits on a long backlog.
- `--upload-secrets` for `deploy.sh`, so a fresh server can be brought up in one
  run. Opt-in, and refused when `ftp.public_path` is empty or equal to
  `ftp.remote_path`, because that would put credentials in the document root.

### Changed

- The Docker runtime and the FTP host no longer share one hand-edited
  `secrets.yml`. `secrets.yml` holds the shared values and is the file
  `deploy.sh` uploads; `secrets.docker.yml` holds the Compose-only override
  (`database.host: db`); `docker/merge-secrets.php` merges them into the file the
  containers read. A deployment no longer rewrites the file Docker bind-mounts,
  which previously broke the local stack.
- The README documents both runtimes, why the choice is forced by the host, and
  what long polling costs: one FPM worker per waiting player.

### Fixed

- A failed poll discarded the player's queued intents, so a "move" could be
  silently lost. Both exit paths now return the batch to the queue, and the retry
  is the very next request.
- The poll loop had no rate limit. With a fast server it spun flat out, pinning a
  worker on the host and starving the browser's event loop. Every iteration now
  yields for at least `minIntervalMs`.
- The polling transport forwarded the raw client frame to the server, which
  ignored anything that was not a structured entry, so no intent was ever
  applied. Frames are now decoded into `{seq, intent, payload}`.
- `bootstrap.ts` built the poll endpoint from the session's `wss://` address,
  which `fetch` cannot use. It is built from the page origin instead.
- A migration endpoint is now named from 256 bits of random token rather than
  `<epoch>-$RANDOM`, which was guessable, and its exception is returned to the
  token holder with the database password masked, so a failed deploy is
  diagnosable without shell access.
- The brand panel, HP panel, and map label overlapped one another below roughly 530px of
  width. The HP panel now drops to its own line under 600px.
- The help card overlapped the message log on coarse pointers.
- `/lobby` returned 404 under nginx, which only rewrote `/`. The extensionless form
  the lobby is actually served at now resolves, matching Apache.
- Placing two fingers and lifting them without moving issued two move commands. Any
  second finger now marks every tracked pointer as dragged, so a multi-finger gesture
  is never a tap.
- `deploy.sh` re-uploaded the entire 886-file tree on every FTP run. lftp `mirror`
  decides what to transfer by comparing mtimes, but staging copied each file without
  `-p`, so every staged file carried the current timestamp and looked newer than its
  remote twin. Staging now preserves mtimes.
- Every FTP run also re-uploaded all ~800 `vendor/` files, because `composer install`
  stamps freshly extracted files with the current time. Dependencies are now
  installed once into a cache directory keyed by the lock file's hash, so the vendor
  tree is only re-uploaded when `composer.lock` actually changes.
- `deploy.sh` no longer aborts on a group/world-readable `secrets.yml`. It tightens the
  file to `0600` and continues, since a fresh checkout landing at `0644` is the normal
  case. Permissions are only ever reduced, never widened, and the script still fails
  if the `chmod` cannot succeed.
- Removed `set xfer:binary yes` from the generated lftp script. lftp has no such
  setting, so it printed `xfer:binary: no such variable` on every run. Binary
  transfer is already the default and ASCII is opt-in via `-a`.

### Testing

- `client/src/input.test.ts` covers the touch and mouse gesture rules directly, using a
  fake canvas and a mocked clock rather than a DOM implementation. jsdom does not
  implement `PointerEvent`, so the events are plain objects carrying only the fields
  the controller reads, which keeps the suite dependency-free and deterministic.
- `client/src/polling.test.ts` covers the long-poll transport, including the paths
  that lose player input: a failed request, a non-ok status, and a response older
  than one already delivered. The transport's `sleep` is injected, so the loop is
  driven by a manual clock and no test can be outlived by a poll. Each guarantee
  was confirmed to fail by mutating the source: removing the input-loss protection
  fails the retry test, removing the `seq >= 1` guard fails the malformed-frame
  test, and removing the per-iteration yield reproduces the busy loop.

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

[Unreleased]: https://github.com/kibotu/vibe-mmo/compare/0.3.0...HEAD
[0.3.0]: https://github.com/kibotu/vibe-mmo/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/kibotu/vibe-mmo/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/kibotu/vibe-mmo/compare/5e0c524...0.1.0
