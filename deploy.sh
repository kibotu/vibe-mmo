#!/usr/bin/env bash
# Payon Forest deployment tool.
#
# This script only reads this repository's backend/secrets.yml. It never reads
# Trail's configuration or credentials.
#
# FTP mode publishes the multiplayer static client and PHP pages to the
# configured private FTP root, then runs a short-lived, token-protected,
# idempotent migration endpoint. The authoritative game daemon must still be
# run by the Docker runtime; see README.md and backend/DEPLOYMENT.md.

set -Eeuo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"
BACKEND_DIR="$REPO_ROOT/backend"
SECRETS_FILE="${MMO_SECRETS_FILE:-$BACKEND_DIR/secrets.yml}"
MODE="ftp"
DRY_RUN=0
SKIP_BUILD=0
SKIP_MIGRATIONS=0
ALLOW_DIRTY=0
ALLOW_INSECURE_TLS=0
ALLOW_INSECURE_FILE_MODE=0
NO_DELETE=0
REMOTE_PATH_OVERRIDE=""
UPLOAD_SECRETS=0

STAGING_DIR=""
LFTP_SCRIPT=""
MIGRATION_CLEANUP_SCRIPT=""
MIGRATION_UPLOADED=0
MIGRATION_NAME=""

if [[ -t 1 ]]; then
  RED='\033[0;31m'
  GREEN='\033[0;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[0;34m'
  NC='\033[0m'
else
  RED='' GREEN='' YELLOW='' BLUE='' NC=''
fi

log() { printf '%s[deploy]%s %s\n' "$BLUE" "$NC" "$*"; }
ok() { printf '%s[ok]%s %s\n' "$GREEN" "$NC" "$*"; }
warn() { printf '%s[warn]%s %s\n' "$YELLOW" "$NC" "$*" >&2; }
die() { printf '%s[fail]%s %s\n' "$RED" "$NC" "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: ./deploy.sh [options]

Modes:
  --mode ftp              Sync the multiplayer client/PHP pages over FTPS,
                          then run protected database migrations (default).
  --mode docker           Build/start the Docker VPS stack and run migrations
                          inside the PHP container.

Options:
  --dry-run               Run preflight/build and print actions without
                          uploading, deleting, or migrating.
  --skip-build            Reuse the existing multiplayer/dist build.
  --skip-migrations       Do not run database migrations.
  --no-delete             Do not remove stale remote files during mirroring.
  --allow-dirty           Permit deployment from a modified Git worktree.
  --allow-insecure-tls    Explicitly disable remote TLS certificate checks.
                          Never use this for production without a reason.
  --allow-insecure-file-mode
                          Deploy without tightening the secrets file. Normally
                          a group/world-readable secrets.yml is chmod'ed to 0600
                          automatically.
  --secrets PATH          Use PATH instead of backend/secrets.yml.
  --remote-path PATH      Override ftp.remote_path for this run.
  --upload-secrets        Also upload secrets.yml to the FTP root. Off by
                          default so a deploy can never overwrite production
                          credentials. Safe here only because ftp.public_path
                          is the document root and secrets.yml sits above it;
                          the script verifies that before uploading.
  -h, --help              Show this help.

Environment:
  MMO_SECRETS_FILE        Alternate secrets file, same as --secrets.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      [[ $# -ge 2 ]] || die "--mode requires ftp or docker"
      MODE="$2"
      shift 2
      ;;
    --secrets)
      [[ $# -ge 2 ]] || die "--secrets requires a path"
      SECRETS_FILE="$2"
      shift 2
      ;;
    --remote-path)
      [[ $# -ge 2 ]] || die "--remote-path requires a path"
      REMOTE_PATH_OVERRIDE="$2"
      shift 2
      ;;
    --dry-run) DRY_RUN=1; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --skip-migrations) SKIP_MIGRATIONS=1; shift ;;
    --no-delete) NO_DELETE=1; shift ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    --allow-insecure-tls) ALLOW_INSECURE_TLS=1; shift ;;
    --allow-insecure-file-mode) ALLOW_INSECURE_FILE_MODE=1; shift ;;
    --upload-secrets) UPLOAD_SECRETS=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "Unknown option: $1" ;;
  esac
done

[[ "$MODE" == "ftp" || "$MODE" == "docker" ]] || die "Unsupported mode: $MODE"
[[ -f "$SECRETS_FILE" ]] || die "Secrets file not found: $SECRETS_FILE (use --secrets or MMO_SECRETS_FILE)"
[[ -f "$BACKEND_DIR/composer.json" ]] || die "Backend directory is incomplete"
[[ -f "$REPO_ROOT/package.json" ]] || die "Frontend package.json is missing"

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

require_command php
require_command git
require_command composer

if [[ "$MODE" == "ftp" ]]; then
  require_command lftp
  require_command curl
  require_command node
  require_command npm
else
  require_command docker
fi

php -r 'exit(PHP_VERSION_ID >= 80500 && PHP_VERSION_ID < 80600 ? 0 : 1);' \
  || die "PHP 8.5.x is required (found $(php -r 'echo PHP_VERSION;'))"
php -r 'foreach (["json", "openssl", "pdo", "pdo_mysql"] as $extension) { if (!extension_loaded($extension)) { fwrite(STDERR, "Missing PHP extension: {$extension}\n"); exit(1); } }'

if [[ "$ALLOW_DIRTY" -eq 0 && -n "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=all)" ]]; then
  die "Git worktree is dirty. Commit changes or pass --allow-dirty for an intentional test deployment."
fi

# Tighten the secrets file rather than refusing to run. A checkout on a fresh
# machine routinely lands at 0644, and making the operator chmod by hand before
# every deploy is a papercut, not a safeguard. Permissions are only ever reduced,
# never widened, so this cannot make a file less private than it already was.
if [[ "$DRY_RUN" -eq 0 && "$ALLOW_INSECURE_FILE_MODE" -eq 0 ]]; then
  if stat -f '%Lp' "$SECRETS_FILE" >/dev/null 2>&1; then
    SECRET_MODE="$(stat -f '%Lp' "$SECRETS_FILE")"
  else
    SECRET_MODE="$(stat -c '%a' "$SECRETS_FILE")"
  fi
  if (( (8#$SECRET_MODE & 077) != 0 )); then
    if ! chmod 600 "$SECRETS_FILE" 2>/dev/null; then
      die "Secrets file is group/other-readable (mode $SECRET_MODE) and could not be tightened to 0600. Fix its ownership, or pass --allow-insecure-file-mode."
    fi
    ok "Tightened $SECRETS_FILE from $SECRET_MODE to 600"
  fi
fi

log "Using secrets from $SECRETS_FILE (this repository only)"

STAGING_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mmo-deploy.XXXXXX")"
LFTP_SCRIPT="$STAGING_DIR/upload.lftp"
MIGRATION_CLEANUP_SCRIPT="$STAGING_DIR/cleanup.lftp"

cleanup() {
  local rc=$?
  if [[ "$MIGRATION_UPLOADED" -eq 1 && -n "$MIGRATION_CLEANUP_SCRIPT" && -f "$MIGRATION_CLEANUP_SCRIPT" ]]; then
    lftp -f "$MIGRATION_CLEANUP_SCRIPT" >/dev/null 2>&1 || warn "Could not remove temporary migration endpoint; remove $MIGRATION_NAME manually"
  fi
  [[ -n "$STAGING_DIR" && -d "$STAGING_DIR" ]] && rm -rf "$STAGING_DIR"
  return "$rc"
}
trap 'rc=$?; cleanup || true; exit "$rc"' EXIT
trap 'exit 130' INT TERM

# Install production dependencies into a cache keyed by the lock file rather
# than into the working copy, so a deploy never strips a developer's dev packages
# from backend/vendor.
#
# The cache is also what keeps vendor/ stable across deploys. Composer stamps
# freshly extracted files with the current time, so installing into a throwaway
# directory makes every dependency look newer than its remote copy and lftp
# re-uploads all ~800 of them every run. Reusing one directory per lock file
# keeps those timestamps stable, and the vendor tree is only re-uploaded when the
# lock file itself actually changes.
VENDOR_CACHE_ROOT="${MMO_VENDOR_CACHE:-${XDG_CACHE_HOME:-$HOME/.cache}/mmo-deploy}"
VENDOR_CACHE_DIR="$VENDOR_CACHE_ROOT/vendor-$(php -r 'echo substr(hash("sha256", file_get_contents($argv[1])), 0, 16);' "$BACKEND_DIR/composer.lock")"

if [[ -f "$VENDOR_CACHE_DIR/vendor/autoload.php" ]]; then
  log "Reusing cached backend dependencies for this lock file"
else
  log "Installing backend dependencies from the committed lock file"
  rm -rf "$VENDOR_CACHE_DIR"
  mkdir -p "$VENDOR_CACHE_DIR"
  cp "$BACKEND_DIR/composer.json" "$BACKEND_DIR/composer.lock" "$VENDOR_CACHE_DIR/"
  cp -R "$BACKEND_DIR/src" "$VENDOR_CACHE_DIR/src"
  (
    cd "$VENDOR_CACHE_DIR"
    composer install --no-dev --classmap-authoritative --no-interaction --prefer-dist
    composer check-platform-reqs --no-dev >/dev/null
  )
fi
mkdir -p "$STAGING_DIR/composer"
cp -Rp "$VENDOR_CACHE_DIR/." "$STAGING_DIR/composer/"

yaml_get() {
  php -r '
    require $argv[1];
    $data = Symfony\Component\Yaml\Yaml::parseFile($argv[2]);
    foreach (explode(".", $argv[3]) as $segment) {
      if (!is_array($data) || !array_key_exists($segment, $data)) { exit(2); }
      $data = $data[$segment];
    }
    if (is_bool($data)) { echo $data ? "true" : "false"; exit(0); }
    if (is_scalar($data)) { echo (string) $data; exit(0); }
    exit(3);
  ' "$STAGING_DIR/composer/vendor/autoload.php" "$SECRETS_FILE" "$1"
}

get_required() {
  local value
  value="$(yaml_get "$1" || true)"
  [[ -n "$value" ]] || die "Missing $1 in $SECRETS_FILE"
  printf '%s' "$value"
}

APP_BASE_URL="$(get_required app.public_url)"
APP_BASE_URL="${APP_BASE_URL%/}"
[[ "$APP_BASE_URL" =~ ^https?://[^[:space:]]+$ ]] || die "app.public_url must be an absolute HTTP(S) URL"

if [[ "$MODE" == "ftp" ]]; then
  FTP_HOST="$(get_required ftp.host)"
  FTP_PORT="$(get_required ftp.port)"
  FTP_USER="$(get_required ftp.username)"
  FTP_PASSWORD="$(get_required ftp.password)"
  FTP_REMOTE_PATH="${REMOTE_PATH_OVERRIDE:-$(get_required ftp.remote_path)}"
  FTP_PUBLIC_PATH="$(get_required ftp.public_path)"
  FTP_TLS="$(yaml_get ftp.tls || true)"

  [[ "$FTP_PORT" =~ ^[0-9]+$ ]] || die "ftp.port must be numeric"
  [[ "$FTP_REMOTE_PATH" != *".."* && "$FTP_PUBLIC_PATH" != *".."* ]] || die "FTP paths may not contain .."
  FTP_REMOTE_PATH="${FTP_REMOTE_PATH:-/}"
  FTP_REMOTE_PATH="${FTP_REMOTE_PATH%/}"
  FTP_PUBLIC_PATH="${FTP_PUBLIC_PATH#/}"
  FTP_PUBLIC_PATH="${FTP_PUBLIC_PATH%/}"
  [[ "$FTP_PUBLIC_PATH" != "." && -n "$FTP_PUBLIC_PATH" ]] || die "ftp.public_path must name a directory"

  # secrets.yml is only safe to upload while it lands above the document root.
  # If public_path were empty, the FTP root would be the docroot and the file
  # would be downloadable. Refuse rather than trust the configuration.
  if [[ "$UPLOAD_SECRETS" -eq 1 ]]; then
    if [[ -z "$FTP_PUBLIC_PATH" || "$FTP_REMOTE_PATH" == "$FTP_PUBLIC_PATH" ]]; then
      die "--upload-secrets requires ftp.public_path to be a directory inside ftp.remote_path, so secrets.yml is written above the document root. Refusing to upload credentials into a web-served directory."
    fi
  fi

  case "$FTP_HOST" in
    *example.com*|*change-this*|*your-*) die "ftp.host still contains an example value" ;;
  esac
  case "$FTP_USER$FTP_PASSWORD" in
    *change-this*|*your-*|*example*) die "FTP credentials still contain example values" ;;
  esac
  [[ "$FTP_TLS" == "true" ]] || die "ftp.tls must be true; use --allow-insecure-tls only for certificate diagnostics, not to disable TLS"

  # The migration endpoint is uploaded over FTP and then requested over HTTP.
  # That HTTP request must go to the FTP host, not to app.public_url, which
  # describes the application and is typically a local Docker port. Requesting
  # the local address fetches this machine instead of the server, so the endpoint
  # is never found. ftp.public_url is the public web address of the FTP root.
  DEPLOY_BASE_URL="$(yaml_get ftp.public_url || true)"
  if [[ -z "$DEPLOY_BASE_URL" ]]; then
    DEPLOY_BASE_URL="$(yaml_get ftp.deploy_url || true)"
  fi
  if [[ -z "$DEPLOY_BASE_URL" ]]; then
    die "Missing ftp.public_url in $SECRETS_FILE. It must be the public HTTP(S) address of the FTP root, for example https://example.com, so migrations can be requested on the server rather than on this machine."
  fi
  DEPLOY_BASE_URL="${DEPLOY_BASE_URL%/}"
  [[ "$DEPLOY_BASE_URL" =~ ^https?://[^[:space:]]+$ ]] || die "ftp.public_url must be an absolute HTTP(S) URL"
  case "$DEPLOY_BASE_URL" in
    http://127.0.0.1*|http://localhost*|http://0.0.0.0*|http://\[::1\]*)
      die "ftp.public_url points at a loopback address ($DEPLOY_BASE_URL). Migrations are requested over HTTP on the server; this address would query your own machine and always 404. Use the FTP host's public address."
      ;;
  esac
fi

LFTP_SCRIPT="$STAGING_DIR/upload.lftp"
MIGRATION_CLEANUP_SCRIPT="$STAGING_DIR/cleanup.lftp"

build_multiplayer() {
  if [[ "$SKIP_BUILD" -eq 1 ]]; then
    [[ -f "$REPO_ROOT/multiplayer/dist/index.html" ]] || die "--skip-build requested but multiplayer/dist/index.html is missing"
    ok "Reusing multiplayer/dist"
    return
  fi
  log "Building multiplayer client"
  (cd "$REPO_ROOT" && npm ci --no-audit --no-fund >/dev/null && npm run typecheck && npm test && npm run build:multiplayer)
  [[ -f "$REPO_ROOT/multiplayer/dist/index.html" ]] || die "Multiplayer build did not produce multiplayer/dist/index.html"
}

stage_backend() {
  log "Staging private backend and public multiplayer client"
  mkdir -p "$STAGING_DIR/release"/{bin,database,src,public,vendor}
  # -p preserves mtimes. Mirror compares them to decide what to transfer, so
  # without this every staged file looks newly modified and the entire tree is
  # re-uploaded each run.
  cp -Rp "$BACKEND_DIR/bin/." "$STAGING_DIR/release/bin/"
  cp -Rp "$BACKEND_DIR/database/." "$STAGING_DIR/release/database/"
  cp -Rp "$BACKEND_DIR/src/." "$STAGING_DIR/release/src/"
  cp -Rp "$BACKEND_DIR/public/." "$STAGING_DIR/release/public/"
  rm -rf "$STAGING_DIR/release/public/game"
  mkdir -p "$STAGING_DIR/release/public/game"
  cp -Rp "$REPO_ROOT/multiplayer/dist/." "$STAGING_DIR/release/public/game/"
  cp -Rp "$STAGING_DIR/composer/vendor/." "$STAGING_DIR/release/vendor/"
  cp "$STAGING_DIR/composer/composer.json" "$STAGING_DIR/composer/composer.lock" "$STAGING_DIR/release/"

  # Never place secrets, runtime state, tests, Docker files, or development
  # metadata in the upload tree.
  find "$STAGING_DIR/release" -name '.DS_Store' -delete

  # secrets.yml is excluded by name in the mirror globs, so it is placed here
  # only when explicitly requested. It goes to the FTP root, above public/.
  if [[ "$UPLOAD_SECRETS" -eq 1 ]]; then
    cp -p "$SECRETS_FILE" "$STAGING_DIR/release/secrets.yml"
    chmod 600 "$STAGING_DIR/release/secrets.yml"
    warn "Uploading $SECRETS_FILE to $FTP_HOST$FTP_REMOTE_PATH/secrets.yml. It must stay above $FTP_PUBLIC_PATH to remain unreachable over HTTP."
  fi

  ok "Staged $(find "$STAGING_DIR/release" -type f | wc -l | tr -d ' ') files"
}

lftp_quote() {
  printf '%s' "$1" | sed 's/[\\"$]/\\&/g'
}

write_lftp_upload_script() {
  local verify="yes"
  [[ "$ALLOW_INSECURE_TLS" -eq 1 ]] && verify="no"
  local delete_args=""
  [[ "$NO_DELETE" -eq 0 ]] && delete_args="--delete"

  # Sibling secret files are always excluded so a per-developer or backup copy
  # can never be published by accident. Note that 'secrets*.yml' would also match
  # secrets.yml itself, so the exact name is excluded by default and dropped only
  # on explicit request.
  local secret_excludes="--exclude-glob 'secrets.*.yml' --exclude-glob 'secrets-*.yml'"
  [[ "$UPLOAD_SECRETS" -eq 1 ]] || secret_excludes="--exclude-glob 'secrets.yml' $secret_excludes"

  cat > "$LFTP_SCRIPT" <<EOF
set ftp:ssl-allow yes
set ftp:ssl-force yes
set ftp:ssl-protect-data yes
set ssl:verify-certificate $verify
set xfer:clobber yes
open -u "$(lftp_quote "$FTP_USER")","$(lftp_quote "$FTP_PASSWORD")" "$(lftp_quote "$FTP_HOST")"
cd "$(lftp_quote "$FTP_REMOTE_PATH")"
lcd "$(lftp_quote "$STAGING_DIR/release")"
mirror --reverse $delete_args --verbose \\
  $secret_excludes \\
  --exclude-glob 'runtime' \\
  --exclude-glob 'runtime/*' \\
  --exclude-glob '*.key' \\
  --exclude-glob '*.pem' \\
  --exclude-glob '*.p12' \\
  --exclude-glob '*.pfx' \\
  --exclude-glob '.env' \\
  --exclude-glob '.env.*' \\
  --exclude-glob '.git*' \\
  --exclude-glob '*.log' \\
  --exclude-glob '*.tmp'
bye
EOF
  chmod 600 "$LFTP_SCRIPT"
}

probe_ftp() {
  local probe="$STAGING_DIR/probe.lftp"
  local verify="yes"
  [[ "$ALLOW_INSECURE_TLS" -eq 1 ]] && verify="no"
  cat > "$probe" <<EOF
set ftp:ssl-allow yes
set ftp:ssl-force yes
set ftp:ssl-protect-data yes
set ssl:verify-certificate $verify
open -u "$(lftp_quote "$FTP_USER")","$(lftp_quote "$FTP_PASSWORD")" "$(lftp_quote "$FTP_HOST")"
cd "$(lftp_quote "$FTP_REMOTE_PATH")"
pwd
cls -1
bye
EOF
  chmod 600 "$probe"
  if [[ "$DRY_RUN" -eq 0 ]]; then
    lftp -f "$probe" >/dev/null || die "FTP preflight failed"
  fi
}

write_migration_cleanup_script() {
  local verify="yes"
  [[ "$ALLOW_INSECURE_TLS" -eq 1 ]] && verify="no"
  cat > "$MIGRATION_CLEANUP_SCRIPT" <<EOF
set ftp:ssl-allow yes
set ftp:ssl-force yes
set ftp:ssl-protect-data yes
set ssl:verify-certificate $verify
open -u "$(lftp_quote "$FTP_USER")","$(lftp_quote "$FTP_PASSWORD")" "$(lftp_quote "$FTP_HOST")"
cd "$(lftp_quote "$FTP_REMOTE_PATH/$FTP_PUBLIC_PATH")"
rm -f "$(lftp_quote "$MIGRATION_NAME")"
bye
EOF
  chmod 600 "$MIGRATION_CLEANUP_SCRIPT"
}

upload_migration_runner() {
  local token token_hash runner_file
  token="$(php -r 'echo bin2hex(random_bytes(32));')"
  token_hash="$(php -r 'echo hash("sha256", $argv[1]);' "$token")"

  # The endpoint has to live in the document root for the web server to execute
  # it, so its filename is the only thing keeping a stranger from reaching the
  # migrator. A name built from date and $RANDOM is guessable: the epoch is
  # knowable and $RANDOM is 15 bits. Deriving the name from 256 bits of the
  # token instead means the URL itself is the secret, and the token header
  # remains a second, independent check.
  MIGRATION_NAME="deploy-migrate-$(php -r 'echo substr(hash("sha256", random_bytes(32)), 0, 32);').php"
  runner_file="$STAGING_DIR/$MIGRATION_NAME"
  cat > "$runner_file" <<EOF
<?php
declare(strict_types=1);

require dirname(__DIR__) . '/vendor/autoload.php';

\$expected = '$token_hash';
\$provided = (string) (\$_SERVER['HTTP_X_DEPLOY_TOKEN'] ?? '');
if (!hash_equals(\$expected, hash('sha256', \$provided))) {
    http_response_code(403);
    echo "MMO_DEPLOY_MIGRATIONS_DENIED\n";
    exit(1);
}

try {
    \$application = Mmo\\Http\\Application::boot();
    \$migrator = new Mmo\\Database\\Migrator(\$application->database, \$application->config);
    \$applied = \$migrator->migrate(dirname(__DIR__) . '/database/migrations');
    echo "MMO_DEPLOY_MIGRATIONS_OK\n";
    echo json_encode(['applied' => \$applied], JSON_THROW_ON_ERROR) . "\n";
} catch (Throwable \$exception) {
    http_response_code(500);
    echo "MMO_DEPLOY_MIGRATIONS_FAILED\n";
    // Returned to the token holder so a failed deploy is diagnosable without
    // shell access to the server's PHP error log. Credentials are masked, so
    // this cannot disclose the database password.
    \$message = \$exception->getMessage();
    \$password = '';
    try {
        \$password = (string) Mmo\\Config\\Config::fromFile(dirname(__DIR__) . '/secrets.yml')->string('database.password');
    } catch (Throwable) {
    }
    if (\$password !== '') {
        \$message = str_replace(\$password, '***', \$message);
    }
    echo get_class(\$exception) . ': ' . \$message . "\n";
    error_log(\$exception->getMessage());
    exit(1);
}
EOF

  local upload="$STAGING_DIR/migration-upload.lftp"
  local verify="yes"
  [[ "$ALLOW_INSECURE_TLS" -eq 1 ]] && verify="no"
  cat > "$upload" <<EOF
set ftp:ssl-allow yes
set ftp:ssl-force yes
set ftp:ssl-protect-data yes
set ssl:verify-certificate $verify
open -u "$(lftp_quote "$FTP_USER")","$(lftp_quote "$FTP_PASSWORD")" "$(lftp_quote "$FTP_HOST")"
cd "$(lftp_quote "$FTP_REMOTE_PATH/$FTP_PUBLIC_PATH")"
rm -f deploy-migrate-*.php
put "$(lftp_quote "$runner_file")" -o "$(lftp_quote "$MIGRATION_NAME")"
bye
EOF
  chmod 600 "$upload"
  lftp -f "$upload" >/dev/null || die "Could not upload the temporary migration endpoint"
  MIGRATION_UPLOADED=1
  write_migration_cleanup_script

  log "Running protected, idempotent database migrations on $DEPLOY_BASE_URL"
  local response
  if ! response="$(curl --silent --show-error --fail-with-body --connect-timeout 10 --max-time 180 \
    -H "X-Deploy-Token: $token" "$DEPLOY_BASE_URL/$MIGRATION_NAME")"; then
    if grep -q 'MMO_DEPLOY_MIGRATIONS_FAILED' <<<"$response"; then
      # The endpoint ran and the migrator threw, so report its reason rather than
      # a generic transport failure.
      printf '%s\n' "$response" >&2
      die "Migrations failed on the server. The endpoint was uploaded to $FTP_HOST$FTP_REMOTE_PATH/$FTP_PUBLIC_PATH/$MIGRATION_NAME and reached successfully; the exception above came from the server."
    fi
    printf '%s\n' "$response" >&2 || true
    die "Migration endpoint could not be reached. It was uploaded to $FTP_HOST$FTP_REMOTE_PATH/$FTP_PUBLIC_PATH/$MIGRATION_NAME but requesting $DEPLOY_BASE_URL/$MIGRATION_NAME did not succeed. A 404 means the web server is not serving the FTP root, or ftp.remote_path/public_path does not match the document root. MMO_DEPLOY_MIGRATIONS_DENIED means the request reached a different or stale copy of the endpoint."
  fi
  printf '%s\n' "$response"
  grep -q 'MMO_DEPLOY_MIGRATIONS_OK' <<<"$response" || die "Migration endpoint did not report success"
  ok "Database migrations are current"
}

if [[ "$MODE" == "docker" ]]; then
  log "Docker mode: building and starting the production stack"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    printf 'Would run: docker compose -f %s up -d --build\n' "$REPO_ROOT/compose.prod.yaml"
    printf 'Would run: docker compose -f %s exec -T php php bin/migrate.php\n' "$REPO_ROOT/compose.prod.yaml"
    exit 0
  fi
  docker compose -f "$REPO_ROOT/compose.prod.yaml" up -d --build
  docker compose -f "$REPO_ROOT/compose.prod.yaml" exec -T php php bin/migrate.php
  ok "Docker deployment and migrations completed"
  exit 0
fi

log "FTP mode: this repository's secrets only"
warn "FTP sync publishes pages and the multiplayer client; the WebSocket daemon must run on the Docker runtime."
build_multiplayer
stage_backend

if [[ "$DRY_RUN" -eq 1 ]]; then
  log "Dry run: would sync $(find "$STAGING_DIR/release" -type f | wc -l | tr -d ' ') files to $FTP_HOST:$FTP_PORT$FTP_REMOTE_PATH"
  if [[ "$SKIP_MIGRATIONS" -eq 0 ]]; then
    printf 'Would run protected migrations at %s/<temporary endpoint>\n' "$DEPLOY_BASE_URL"
  fi
  exit 0
fi

probe_ftp
write_lftp_upload_script
log "Mirroring production files to $FTP_HOST:$FTP_PORT$FTP_REMOTE_PATH"
lftp -f "$LFTP_SCRIPT"
ok "FTP sync completed"

if [[ "$SKIP_MIGRATIONS" -eq 0 ]]; then
  upload_migration_runner
else
  ok "Migrations skipped by request"
fi

ok "Deployment completed"
printf '  Application URL: %s\n' "$APP_BASE_URL"
printf '  Deploy URL:      %s\n' "$DEPLOY_BASE_URL"
printf '  Git commit:      %s\n' "$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
