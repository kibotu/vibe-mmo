#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

npm run build:backend
rm -rf backend/public/game
mkdir -p backend/public/game
cp -R dist/. backend/public/game/

printf 'Built frontend copied to backend/public/game/\n'
