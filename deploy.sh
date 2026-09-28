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
                          Permit a group/world-readable secrets.yml.
  --secrets PATH          Use PATH instead of backend/secrets.yml.
  --remote-path PATH      Override ftp.remote_path for this run.
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

if [[ "$DRY_RUN" -eq 0 && "$ALLOW_INSECURE_FILE_MODE" -eq 0 ]]; then
  if stat -f '%Lp' "$SECRETS_FILE" >/dev/null 2>&1; then
    SECRET_MODE="$(stat -f '%Lp' "$SECRETS_FILE")"
  else
    SECRET_MODE="$(stat -c '%a' "$SECRETS_FILE")"
  fi
  if (( (8#$SECRET_MODE & 077) != 0 )); then
    die "Secrets file is readable by group/other (mode $SECRET_MODE). Use chmod 0600 or 0640, or pass --allow-insecure-file-mode."
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

# Install production dependencies into the staging tree instead of the working
# copy, so a deploy never strips a developer's dev packages from backend/vendor.
log "Installing backend dependencies from the committed lock file into staging"
mkdir -p "$STAGING_DIR/composer"
cp "$BACKEND_DIR/composer.json" "$BACKEND_DIR/composer.lock" "$STAGING_DIR/composer/"
cp -R "$BACKEND_DIR/src" "$STAGING_DIR/composer/src"
(
  cd "$STAGING_DIR/composer"
  composer install --no-dev --classmap-authoritative --no-interaction --prefer-dist
  composer check-platform-reqs --no-dev >/dev/null
)

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

  case "$FTP_HOST" in
    *example.com*|*change-this*|*your-*) die "ftp.host still contains an example value" ;;
  esac
  case "$FTP_USER$FTP_PASSWORD" in
    *change-this*|*your-*|*example*) die "FTP credentials still contain example values" ;;
  esac
  [[ "$FTP_TLS" == "true" ]] || die "ftp.tls must be true; use --allow-insecure-tls only for certificate diagnostics, not to disable TLS"
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
  cp -R "$BACKEND_DIR/bin/." "$STAGING_DIR/release/bin/"
  cp -R "$BACKEND_DIR/database/." "$STAGING_DIR/release/database/"
  cp -R "$BACKEND_DIR/src/." "$STAGING_DIR/release/src/"
  cp -R "$BACKEND_DIR/public/." "$STAGING_DIR/release/public/"
  rm -rf "$STAGING_DIR/release/public/game"
  mkdir -p "$STAGING_DIR/release/public/game"
  cp -R "$REPO_ROOT/multiplayer/dist/." "$STAGING_DIR/release/public/game/"
  cp -R "$STAGING_DIR/composer/vendor/." "$STAGING_DIR/release/vendor/"
  cp "$STAGING_DIR/composer/composer.json" "$STAGING_DIR/composer/composer.lock" "$STAGING_DIR/release/"

  # Never place secrets, runtime state, tests, Docker files, or development
  # metadata in the upload tree.
  find "$STAGING_DIR/release" -name '.DS_Store' -delete
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

  cat > "$LFTP_SCRIPT" <<EOF
set ftp:ssl-allow yes
set ftp:ssl-force yes
set ftp:ssl-protect-data yes
set ssl:verify-certificate $verify
set xfer:clobber yes
set xfer:binary yes
open -u "$(lftp_quote "$FTP_USER")","$(lftp_quote "$FTP_PASSWORD")" "$(lftp_quote "$FTP_HOST")"
cd "$(lftp_quote "$FTP_REMOTE_PATH")"
lcd "$(lftp_quote "$STAGING_DIR/release")"
mirror --reverse $delete_args --verbose \\
  --exclude-glob 'secrets.yml' \\
  --exclude-glob 'secrets*.yml' \\
  --exclude-glob 'secrets.*.yml' \\
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
  MIGRATION_NAME="deploy-migrate-$(date +%s)-$RANDOM.php"
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

  log "Running protected, idempotent database migrations"
  local response
  if ! response="$(curl --silent --show-error --fail-with-body --connect-timeout 10 --max-time 180 \
    -H "X-Deploy-Token: $token" "$APP_BASE_URL/$MIGRATION_NAME")"; then
    printf '%s\n' "$response" >&2 || true
    die "Migration endpoint failed"
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
    printf 'Would run protected migrations at %s/<temporary endpoint>\n' "$APP_BASE_URL"
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
printf '  Public URL: %s\n' "$APP_BASE_URL"
printf '  Git commit: %s\n' "$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
