# Payon Forest — Classic RO-Style Three.js Slice

A small single-player 2.5D game study built with Three.js and TypeScript.

## Run

```bash
npm install
npm run dev
```

Open the local Vite URL in a desktop browser. The default camera is `classic2003`; append `?profile=preRenewal2008` to compare the later profile, or `?seed=N` for a deterministic world.

## Controls

- Left-click ground: walk.
- Left-click a Poring: target and attack.
- Hold left mouse: continue movement toward the current ground target.
- Right-drag: rotate camera.
- `Shift` + right-drag: change camera elevation.
- `Control` + right-drag: zoom.
- Mouse wheel: zoom.
- Double right-click: reset camera direction.
- `Shift` + double right-click: reset the full camera view.
- `F1`: attack current target.
- `H`: eat an Apple when HP is below maximum.
- `I` or `Alt+E`: inventory.
- `Escape`: close the inventory.

The game intentionally has no audio. Apples are the first usable inventory item: press `H` or click an Apple slot to recover 15 HP.

## Verification

```bash
npm run typecheck
npm test
npm run build
```

With the dev server running, the browser smoke test uses the installed system Chrome:

```bash
npm run smoke
```

Set `CHROME_PATH` if Chrome is installed elsewhere.

The implementation uses procedural placeholder sprites and geometry. It does not ship extracted Ragnarok Online assets.

## GitHub Pages

Pushing to `main` runs the test suite and publishes the Vite `dist/` build to GitHub Pages. Set **Settings → Pages → Build and deployment → Source** to **GitHub Actions** once for the repository. The site is then available at [kibotu.github.io/vibe-mmo](https://kibotu.github.io/vibe-mmo/).
