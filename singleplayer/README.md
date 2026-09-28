# Singleplayer client

This is the offline Payon Forest client published to GitHub Pages.

It uses the shared engine in [`client/src`](../client/src) but never opens a WebSocket or calls the backend session API.

```bash
npm ci
npm run dev
npm run build:singleplayer
```

The deployable artifact is written to `singleplayer/dist/`. The `?seed=N` and `?profile=preRenewal2008` query parameters remain available for deterministic testing.
