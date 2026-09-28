#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

npm run build:multiplayer
rm -rf backend/public/game
mkdir -p backend/public/game
cp -R multiplayer/dist/. backend/public/game/

printf 'Built multiplayer client copied to backend/public/game/\n'
