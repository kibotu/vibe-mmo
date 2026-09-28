# Multiplayer client

This client is served by the PHP/nginx backend at `/game/`. It boots the authoritative room session, renders remote actors, predicts local movement, and reconnects through one-time WebSocket tickets.

It uses the shared engine in [`client/src`](../client/src) and expects the backend lobby at `/`.

```bash
npm ci
npm run dev:multiplayer
npm run build:multiplayer
```

The deployable artifact is written to `multiplayer/dist/`. The backend web image or `scripts/build-backend.sh` copies it to `backend/public/game/`.

The authoritative game daemon and MariaDB must run on the Docker runtime. This static client alone is not a multiplayer server.
