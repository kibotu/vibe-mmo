# Technical Implementation Spec: Classic Ragnarok-Style Three.js Game

**Status:** Implemented — tracer bullet with consumable extension
**Target profile:** `classic2003`
**Primary platform:** Desktop browser
**Renderer:** Three.js with WebGL
**Audio:** Disabled by design

## 1. Purpose

Build a small single-player game that reproduces the visual language and control feel of classic Ragnarok Online:

- A 3D outdoor world with a 2.5D presentation.
- A perspective orbital camera focused on the player.
- 2D, eight-direction character sprites moving across a 3D terrain.
- Mouse-first movement and combat.
- A small Payon-inspired forest biome.
- Passive Porings that wander, can be attacked, and can die.
- Basic melee combat.
- Random floor loot and a small inventory.
- No sound effects, music, or audio API usage.

This is a vertical slice, not an MMO emulator. The first deliverable is deliberately narrow: one map, one player, one monster type, one loot path, and one inventory.

## 2. Assumptions and decisions

1. **Reference target:** The visual and control target is the original/pre-Renewal classic client, approximately 2003. The specification does not target Renewal, Ragnarok 2, or modern MMORPG camera conventions.
2. **Original assets:** The implementation must use original, licensed, or placeholder art. It must not ship extracted Ragnarok sprites, models, textures, sounds, or GRF data.
3. **World scale:** One walkable map cell equals one normalized Three.js world unit. The original renderer used a different raw scale; conversion is documented in the camera section.
4. **Viewport:** The reference presentation is 4:3. The first implementation should render a logical `800x600` viewport and scale it to the browser window rather than changing the composition independently at every aspect ratio.
5. **Movement:** Classic mouse movement is the default. Keyboard movement is not part of the canonical control scheme. An optional camera-relative keyboard mode may be added later without changing the default.
6. **Persistence:** Inventory and world state are session-only in the tracer bullet. Save/load is deferred.
7. **Combat scope:** Porings are passive and do not attack the player in the first slice. The combat model must nevertheless support damage, death, and future hostile actors.
8. **No audio:** Do not create an audio manifest, import `Audio`, instantiate `AudioContext`, or add sound-related event fields. Feedback is visual only.

## 3. Research findings and design consequences

### 3.1 Classic controls

The official and community control references agree on the core behavior:

- Left-click a walkable ground cell to move there.
- Hold the left mouse button for continuous movement.
- Left-click a monster to target and attack it.
- Hold the right mouse button and drag horizontally to rotate the camera around the character.
- Hold `Shift` plus the right mouse button and drag vertically to change camera height.
- Hold `Control` plus the right mouse button and drag vertically to zoom when a wheel is unavailable.
- Use the mouse wheel to zoom.
- Quickly right-click twice to reset the view toward north.
- Hold `Shift` and quickly right-click twice to reset the camera position/angle.
- The keyboard is primarily for shortcuts, windows, skills, and commands; it is not the default movement control.

The implementation must not attach stock `OrbitControls` with their default mouse mapping. The camera needs a custom controller because right-drag, `Shift`-right-drag, `Control`-right-drag, double-click reset, and camera smoothing are part of the game feel.

### 3.2 2.5D rendering

Ragnarok Research Lab and the roBrowser reconstruction both describe the original as a 3D world with 2D actors. The actor animation system uses eight directional views, while the environment supplies depth, height, props, lighting, and fog.

Implementation consequences:

- Terrain, water, trees, bridges, rocks, and other world objects are 3D.
- Player, Poring, NPC, and item actors are textured planes or sprites.
- Actor direction is represented by eight sprite directions, not by smoothly rotating a 3D character model.
- The camera remains a perspective camera. A conventional isometric orthographic camera will feel wrong because it removes the original perspective/scale cues.
- The renderer should use a low internal resolution, nearest texture filtering, and limited antialiasing to preserve the old image character.

### 3.3 Camera version profiles and the default distance

There is no single version-independent public constant for “the Ragnarok camera.” Client versions and reverse-engineering projects use different coordinate scales and defaults. The project must therefore select a profile instead of mixing constants from different versions.

Use the following profiles:

| Profile | Vertical FOV | Default radial distance | Default camera elevation | Classic zoom range | Intended use |
|---|---:|---:|---:|---:|---|
| `classic2003` | `15°` | `50` normalized units | `60°` above horizontal | `45–80` | Default for this project |
| `preRenewal2008` | `15°` | `62.5` normalized units | `50°` above horizontal | `32.5–82.5` | Optional comparison profile |

### The default answer for this project

For the requested approximately-2003 target, initialize the camera to:

```text
vertical FOV:       15 degrees
radial distance:    50 normalized world units
camera elevation:   60 degrees above the horizontal
minimum distance:   45 normalized world units
maximum distance:   80 normalized world units
wheel step:         5 normalized world units
reference viewport: 800 x 600
```

The `50` value is the default in the older roBrowser camera implementation and is consistent with the Ragnarok Research Lab’s 2004-era estimate of a classic zoom step of approximately five normalized units and a useful range around `45–80`. Ragnarok Research Lab also documents that one GAT tile is five raw RO world units, so `50` normalized units corresponds to approximately `250` raw RO world units.

The older roBrowser implementation stores its default pitch as `240°` in an inverted-Y coordinate convention. In the conventional Three.js convention used by this spec, that is a `60°` camera elevation. This conversion must be documented in code rather than copying the inverted angle directly.

The later `preRenewal2008` profile uses `62.5` normalized units and a different default pitch in newer roBrowser/Goro reconstructions. It is retained for comparison only. It must not be mixed into the `classic2003` constants.

### 3.4 Camera input sensitivity

The legacy roBrowser reconstruction uses a viewport-relative horizontal sensitivity of approximately `720°` per full viewport width. Use that as the initial value and tune only against reference captures:

```text
yaw delta   = -pointerDeltaX / viewportWidth  * 720°
pitch delta =  pointerDeltaY / viewportHeight * 300°
zoom delta  =  pointerDeltaY / viewportHeight * 300 / 10
```

The last expression is only a starting point for `Control`-right-drag. The mouse wheel uses the discrete five-unit step.

### 3.5 Poring and loot reference data

Classic database references describe Poring as a level-one, Water-element, medium-sized monster with roughly 50 HP and 6–7 attack. A database page lists common drops including Jellopy, Empty Bottle, Apple, Sticky Mucus, and a low-chance Knife/Poring Card. The exact rates vary by server and client version.

The first slice will use a simplified, explicit weighted table rather than pretending to reproduce every server drop rule:

| Item | Weight |
|---|---:|
| Jellopy | 65% |
| Empty Bottle | 15% |
| Apple | 10% |
| Sticky Mucus | 4% |
| Knife | 1% |
| Nothing | 5% |

The table is data, not hard-coded branching in the death handler.

## 4. Tracer bullet definition

The tracer bullet is a deliberately ugly but complete path through the game:

1. Load a small forest map.
2. Render one player sprite and several wandering Porings.
3. Left-click the ground and walk to the selected cell.
4. Rotate, tilt, and zoom the camera with the classic mouse controls.
5. Left-click a Poring.
6. Automatically walk into attack range if necessary.
7. Attack until the Poring dies.
8. Spawn one possible floor item.
9. Click the floor item to pick it up.
10. Open the inventory and see the item in a slot.

### Tracer-bullet acceptance criteria

The slice is complete when all of the following are observable in the browser:

- The player remains visually near the center of the view while the camera follows.
- The default camera uses a `15°` vertical FOV and a `50`-unit radial distance.
- A left-click on a valid cell produces a visible path and continuous movement.
- A left-click on a blocked cell does not move the player through the blocker.
- A left-click on a Poring changes the target state and eventually kills it.
- The Poring plays a death state, disappears, and respawns after a delay.
- A deterministic test seed produces a reproducible loot result.
- Clicking a floor item updates the inventory.
- A full inventory leaves the floor item in the world and shows a visual message.
- Right-drag, `Shift`-right-drag, `Control`-right-drag, wheel zoom, and double-right-click reset work without opening the browser context menu.
- No audio files, audio elements, or audio APIs are present.

Do not expand the scope until this path works end to end.

## 5. Recommended technical stack

Use a small, boring stack:

- **TypeScript** for simulation and rendering code.
- **Vite** for development and production bundling.
- **Three.js** for WebGL rendering.
- Vanilla DOM/CSS for the HUD and inventory. A frontend framework is unnecessary for this slice.
- **Vitest** for pure simulation tests.
- **Playwright** for one browser-level interaction test and screenshot capture.

Suggested scripts:

```text
dev       start Vite development server
build     type-check and create production bundle
test      run unit tests
test:e2e  run browser interaction tests
typecheck run tsc with no emit
```

Suggested source layout:

```text
src/
  main.ts
  game/
    Game.ts
    GameLoop.ts
    GameState.ts
    Random.ts
  camera/
    ROCamera.ts
    CameraProfile.ts
  input/
    InputController.ts
    PointerPicker.ts
  world/
    WorldGrid.ts
    Terrain.ts
    PayonBiome.ts
    PropRenderer.ts
  actors/
    Actor.ts
    ActorManager.ts
    SpriteActor.ts
    SpriteAnimation.ts
  movement/
    GridPathfinder.ts
    MovementSystem.ts
  combat/
    CombatSystem.ts
    Damage.ts
  items/
    Inventory.ts
    LootSystem.ts
    FloorItem.ts
  ui/
    Hud.ts
    InventoryWindow.ts
    MessageLog.ts
  data/
    actors.ts
    items.ts
    loot.ts
  styles.css

public/
  assets/
    sprites/
    textures/
    icons/
```

This is a starting layout, not a mandate to create every file before the tracer bullet works. Keep files cohesive and delete unused scaffolding rather than building an abstract engine around one map.

## 6. Simulation and rendering architecture

Keep simulation state separate from Three.js objects.

```text
InputController -> GameState -> simulation systems -> render adapters -> renderer
```

Recommended update order:

1. Drain pointer/keyboard commands.
2. Update player and actor movement.
3. Update monster AI.
4. Update combat timers and resolve deaths.
5. Create floor loot and process pickup commands.
6. Update inventory and HUD state.
7. Update camera smoothing.
8. Synchronize Three.js objects and render.

### Core state sketch

```ts
type Cell = {
  x: number;
  z: number;
  height: number;
  walkable: boolean;
  propId?: string;
};

type ActorState = {
  id: string;
  kind: 'player' | 'poring';
  position: { x: number; y: number; z: number };
  facing: number;
  state: 'idle' | 'walk' | 'attack' | 'hurt' | 'dead';
  hp: number;
  maxHp: number;
  path: Cell[];
  targetId?: string;
  nextThinkAt: number;
  spawnCell: Cell;
};

type ItemStack = {
  itemId: string;
  quantity: number;
};

type GameState = {
  time: number;
  seed: number;
  player: ActorState;
  actors: ActorState[];
  floorItems: FloorItem[];
  inventory: (ItemStack | null)[];
  messages: string[];
};
```

The simulation must not depend on `THREE.Object3D`, DOM elements, or browser events. This keeps combat, pathfinding, loot, and inventory testable without a WebGL context.

## 7. Camera technical contract

### 7.1 Camera model

Use a `THREE.PerspectiveCamera` with a custom orbital controller.

```ts
const camera = new THREE.PerspectiveCamera(
  profile.fov,
  viewportWidth / viewportHeight,
  profile.near,
  profile.far,
);
```

Initial `classic2003` values:

```ts
const classic2003 = {
  fov: 15,
  near: 2,
  far: 300,
  minDistance: 45,
  defaultDistance: 50,
  maxDistance: 80,
  wheelStep: 5,
  defaultElevation: 60,
  minElevation: 15,
  maxElevation: 75,
  defaultYaw: 0,
};
```

The `15–75°` elevation range is a practical Three.js constraint around the broad legacy range. The default and zoom distance are the important reference values. If a 4:3 reference capture shows that the original permits a lower camera, widen the range only after checking sprite readability and terrain occlusion.

### 7.2 Coordinate convention

Use standard Three.js coordinates:

- `x`: map east/west.
- `y`: up.
- `z`: map north/south.
- North is positive `z`.
- A cell center is `(x + 0.5, height, z + 0.5)`.
- Default yaw `0°` places the camera south of the target, looking north.

Given a target `T`, distance `d`, yaw `y`, and elevation `e`, calculate the camera position as follows. The angle variables are converted from degrees to radians before calling the trigonometric functions:

```ts
const yawRadians = THREE.MathUtils.degToRad(y);
const elevationRadians = THREE.MathUtils.degToRad(e);
const horizontal = d * Math.cos(elevationRadians);
camera.position.set(
  T.x + Math.sin(yawRadians) * horizontal,
  T.y + Math.sin(elevationRadians) * d,
  T.z - Math.cos(yawRadians) * horizontal,
);
camera.lookAt(T);
```

At the classic default (`d=50`, `e=60°`), the camera is approximately `43.3` units above the target and `25` units behind it horizontally. This is the expected classic-style elevated view.

### 7.3 Follow behavior

The camera follows the rendered player position, not a snapped cell center. This preserves continuous movement and matches the legacy reconstruction’s smoothed target behavior.

Use a frame-rate-independent approximation of the legacy smoothing:

```ts
const followAlpha = Math.min(deltaMs * 0.006, 1);
const viewAlpha = Math.min(followAlpha * 2, 1);

cameraTarget.lerp(desiredTarget, followAlpha);
currentDistance = lerp(currentDistance, targetDistance, viewAlpha);
currentYaw = lerpAngle(currentYaw, targetYaw, viewAlpha);
currentElevation = lerp(currentElevation, targetElevation, viewAlpha);
```

The camera must not snap when the player crosses a cell boundary. The target height should be sampled from the terrain and smoothed with the horizontal target.

### 7.4 Control mapping

| Input | Result |
|---|---|
| Left-click ground | Set walk destination |
| Hold left mouse | Continue issuing movement to the current ground target |
| Left-click monster | Select target and begin auto-attack/path-to-range behavior |
| Left-click floor item | Pick up item |
| Right-drag horizontal | Change yaw around player |
| `Shift` + right-drag vertical | Change elevation |
| `Control` + right-drag vertical | Change zoom distance |
| Mouse wheel | Change zoom by five normalized units per notch |
| `Shift` + mouse wheel vertical | Optional alias for elevation |
| Double right-click | Reset yaw and elevation to the default north-facing view |
| `Shift` + double right-click | Reset yaw, elevation, and distance |
| `F1` | Use basic attack on current target |
| `I` or `Alt+E` | Toggle inventory |
| `Escape` | Close the focused window |

The right mouse button must call `preventDefault()` on the game canvas. The input layer must not use the browser context menu as a camera reset mechanism.

### 7.5 Picking

Use `THREE.Raycaster` with normalized device coordinates:

```ts
const rect = canvas.getBoundingClientRect();
const ndc = new THREE.Vector2(
  ((clientX - rect.left) / rect.width) * 2 - 1,
  -((clientY - rect.top) / rect.height) * 2 + 1,
);
raycaster.setFromCamera(ndc, camera);
```

Picking priority:

1. UI hit test; if consumed, stop.
2. Raycast actor sprites and floor items; choose the nearest valid hit.
3. Raycast the terrain mesh.
4. Snap the terrain hit to the containing grid cell.
5. Reject blocked/out-of-bounds cells.

Three.js `Sprite` supports raycasting, but an invisible or low-opacity hit proxy may be needed if the visible frame has large transparent regions. The proxy must resolve to the same actor ID as the visible sprite.

### 7.6 Why not stock OrbitControls?

`OrbitControls` is useful as a reference, but its default mapping conflicts with the game:

- Left drag normally rotates; RO left drag is movement/attack.
- Right drag normally pans; RO right drag rotates.
- Default zoom and damping behavior differ.
- RO has specific reset and modifier semantics.

Use a small custom `ROCamera` controller. Do not spend the first milestone configuring a general-purpose controller to impersonate a game-specific one.

## 8. Movement and pathfinding

### 8.1 Grid movement

Use an eight-direction grid pathfinder:

- A* with a Manhattan/Octile heuristic.
- Eight-way movement allowed.
- No diagonal corner cutting: both orthogonal neighbors must be walkable.
- Props, water, cliffs, and map boundaries are blockers.
- The player snaps its logical position to cell centers but renders an interpolated position between cells.
- Default walking speed: approximately `4.0` normalized cells per second; tune against animation and map scale.

The visual movement should be continuous. A click should not teleport the player.

### 8.2 Click behavior

On a left-click:

```text
if UI consumes pointer:
    do nothing
else if actor is hit:
    set target
    if target is in attack range:
        attack loop
    else:
        path to nearest valid attack cell
else if floor item is hit:
    pickup item
else if terrain cell is valid:
    cancel current target
    set path to cell
else:
    show invalid/blocked cursor feedback
```

When the left button is held, re-raycast the current cursor at a controlled interval, such as 100 ms. Do not enqueue a new path every animation frame.

### 8.3 Actor facing

Store facing as a world-space angle derived from the movement delta. During an attack, face the target. If the actor is stationary, retain the last facing direction.

The sprite frame is selected from the actor heading and camera yaw. The original system uses eight directional animation views; do not smoothly rotate a billboard mesh and call that equivalent.

### 8.4 Keyboard movement

Do not make WASD or arrow keys part of the default tracer bullet. Classic RO movement is mouse-driven, and adding camera-relative keyboard movement would obscure whether the core control feel is correct.

If added later, implement it as an explicit accessibility profile:

- `WASD`/arrows move relative to camera yaw.
- Keyboard input generates the same grid destinations as a click.
- It is disabled by default and documented as a deviation from the reference.

## 9. 2D actor rendering

### 9.1 Sprite representation

Use one `SpriteActor` per visible actor for the first slice:

```ts
const material = new THREE.SpriteMaterial({
  map: frameTexture,
  transparent: true,
  alphaTest: 0.5,
  depthTest: true,
  depthWrite: false,
  sizeAttenuation: true,
});
```

Three.js `Sprite` always faces the camera and supports perspective size attenuation. This is a good match for the 2.5D actor presentation. A custom camera-facing quad can replace it later if exact legacy anchoring or render ordering proves necessary.

Required actor properties:

- 8 directional views.
- `idle` and `walk` animations for the player and Poring.
- `attack` and `hurt` states for the player/Poring.
- `dead` state for the Poring.
- Explicit frame duration or movement-distance-based playback.
- Bottom-center or asset-defined anchor.
- Separate blob shadow; Three.js sprites do not cast shadows.

### 9.2 Texture rules

- Use `NearestFilter` for pixel-art frames.
- Disable mipmaps for the actor atlas unless a separate higher-resolution asset pipeline is introduced.
- Set color textures to `SRGBColorSpace`.
- Use `alphaTest` to avoid transparent pixels writing depth.
- Keep the texture atlas immutable and change UVs per actor rather than baking a new texture for every frame.
- Use a small fixed palette where possible.

### 9.3 Direction selection

For each actor:

```text
relative heading = actor facing - camera yaw
direction index  = quantize(relative heading, 8)
```

Normalize the angle into `[0, 2π)` before quantizing. The precise sign depends on the sprite asset’s north frame and must be verified with a four-direction test:

1. Face north.
2. Face east.
3. Face south.
4. Face west.
5. Rotate the camera 90° and confirm the apparent frame changes correctly.

### 9.4 Poring movement

Porings should feel bouncy rather than like rigid humanoids:

- Wander to a random nearby walkable cell.
- Pause for a short random interval.
- Use a 2–4 frame hop/bob animation.
- Keep a small vertical visual offset during the hop while retaining the logical ground position.
- Do not chase the player in the tracer bullet.

## 10. Payon-inspired world

The map should evoke Payon Forest without copying a specific Ragnarok map or using its assets.

### 10.1 Layout

Create one bounded `64x64` cell map with:

- A central grassy clearing for the first combat test.
- Dense tree clusters around the edges.
- A narrow stream or pond crossing one side.
- A small wooden bridge over the stream.
- Dirt paths connecting the clearing and bridge.
- Rocks, shrubs, flowers, logs, and mushrooms as 3D props.
- Several open spawn pockets for Porings.

### 10.2 Terrain and props

Terrain:

- Generate a `BufferGeometry` from the grid heights.
- Use flat-shaded or deliberately quantized colors.
- Keep walkability separate from visual geometry.
- Use a muted grass/dirt/stone palette.
- Add low-amplitude height variation; this is a walking map, not a platformer.

Props:

- Use simple low-poly meshes for trunks, rocks, mushrooms, and bridge geometry.
- Use instancing for repeated trees, rocks, and flowers after the first working map.
- Mark prop cells as blocked in the grid.
- Do not add a general-purpose physics engine. Grid collision and height sampling are sufficient.

### 10.3 Lighting and atmosphere

Use:

- Hemisphere or ambient fill light.
- One directional light with a warm, soft tint.
- Linear fog beginning around `90` units and ending around `220` units.
- Flat background/sky color appropriate to the forest.
- No bloom, depth of field, or physically based effects in the tracer bullet.

The first pass should optimize for silhouette, color blocks, and readable sprite contrast rather than realistic rendering.

## 11. Combat system

### 11.1 Player attack

Initial player values:

```text
basic attack: 12
attack interval: 800 ms
attack range: 1.5 cells
damage variance: ±10%
critical hits: disabled in tracer bullet
```

Initial Poring values based on classic references:

```text
level: 1
max HP: 50
attack: 6
defense: 0
element: Water
size: Medium
aggro: false
```

The Poring attack value is retained in the data model even though the first slice does not use it against the player.

### 11.2 Damage function

Keep the formula in a pure function:

```ts
damage = max(
  1,
  round((attacker.attack - target.defense * 0.5) * variance),
);
```

The random variance must come from the injected game RNG. Rendering code must never roll damage or loot.

### 11.3 Attack state machine

```text
idle
  -> target selected
  -> move toward target if out of range
  -> face target
  -> attack animation
  -> damage event
  -> return to idle or repeat after cooldown
```

When a Poring dies:

1. Set HP to zero.
2. Enter `dead` animation state.
3. Disable targeting and collision after the death animation begins.
4. Roll loot exactly once.
5. Create a floor item if the roll produced one.
6. Remove the Poring after the death animation.
7. Schedule respawn at its spawn cell after a fixed delay.

### 11.4 Visual feedback without sound

Use:

- Brief white/red tint on the target.
- Floating damage number.
- Target health bar.
- Attack-ready indicator on the player cursor.
- Small non-audio hit particles or a one-frame sprite flash if needed.
- Message log text for loot, full inventory, invalid target, and respawn events.

Do not add placeholder sound calls. A silent build is a requirement, not a temporary omission.

## 12. Loot and inventory

### 12.1 Loot system

A Poring death calls:

```ts
const itemId = lootTable.roll(rng, 'poring');
```

The roll happens once. Pickup must not reroll it.

The first table:

```ts
const poringLoot = [
  { itemId: 'jellopy', weight: 0.65 },
  { itemId: 'empty_bottle', weight: 0.15 },
  { itemId: 'apple', weight: 0.10 },
  { itemId: 'sticky_mucus', weight: 0.04 },
  { itemId: 'knife', weight: 0.01 },
  { itemId: null, weight: 0.05 },
];
```

### 12.2 Floor items

A floor item has:

- Stable item ID.
- Stack quantity.
- World position.
- Spawn time.
- Optional small icon sprite.
- Pickup radius or click target.

For the tracer bullet, clicking the item picks it up. Walking over it may be added later if it does not interfere with click-to-move.

### 12.3 Inventory

Start with a `5x4` grid and 20 slots.

Rules:

- Materials and consumables stack to 99.
- Equipment is deferred.
- A pickup first fills an existing compatible stack, then the first empty slot.
- If no slot is available, the item remains on the ground.
- The inventory window shows item name, quantity, and a simple icon.
- Inventory state is authoritative in `GameState`, not in the DOM.

Keyboard aliases:

- `I`: toggle inventory.
- `Alt+E`: classic-style alias where browser behavior permits it.

The first consumable extension is an Apple. `H` or clicking an Apple inventory slot consumes one Apple and restores up to 15 HP; the item is not consumed at full health.

## 13. UI and presentation

The first HUD contains only what the tracer bullet needs:

- Player HP bar.
- Current target name and HP bar.
- F1 attack-slot indicator.
- Small message log.
- Inventory toggle button.
- Optional map name, `Payon Forest`, in a small corner label.

Use DOM/CSS for crisp UI at the logical viewport resolution. Style it with small borders, compact typography, and muted parchment/wood colors inspired by the period, without copying the original UI artwork.

## 14. Milestones and verification

### Milestone 0: Reference and asset contract

**Deliverables**

- Select `classic2003` as the active camera profile.
- Create a reference sheet containing:
  - 4:3 default camera view.
  - Camera at minimum and maximum zoom.
  - Camera rotated 90°.
  - Player and Poring at eight directions.
  - Ground, prop, sprite, and fog colors.
- Confirm original/licensed asset sources.
- Create placeholder atlases if final art is not ready.

**Verify**

- A reviewer can identify the target client/version and profile.
- No extracted Ragnarok assets are present.

### Milestone 1: End-to-end tracer bullet

**Deliverables**

- Vite/TypeScript/Three.js bootstrap.
- Fixed 4:3 render viewport.
- Flat test terrain and one player sprite.
- One Poring sprite.
- Custom camera controller.
- Click-to-move without full art polish.
- Click-to-attack, death, one loot item, and inventory panel.
- No audio code.

**Verify**

- Run the complete scenario in the browser.
- Record a short screen capture of the full loop.
- All acceptance criteria in Section 4 pass.

Do not proceed with elaborate art before this path is stable.

### Milestone 2: Camera and input parity hardening

**Deliverables**

- Exact `classic2003` camera profile.
- Smooth target following.
- Yaw, elevation, wheel zoom, modifier drags, and reset gestures.
- Proper pointer capture and context-menu suppression.
- Actor and ground picking priority.
- Optional camera-relative keyboard mode behind a flag, not enabled by default.

**Verify**

- Unit tests cover camera distance, clamping, reset, and smoothing.
- Browser test covers every documented control.
- Default screenshot has the player at the expected scale and position.
- A 90° camera rotation does not cause sprite stretching or terrain popping.

### Milestone 3: Grid movement and sprite actors

**Deliverables**

- A* pathfinding.
- Eight-direction interpolation.
- Player walk/idle/attack animation.
- Poring wander animation.
- Directional frame selection.
- Blob shadows and actor depth sorting.

**Verify**

- Player cannot cross blocked cells or diagonal corners.
- Clicking a valid cell moves to the cell center.
- Actor frame changes correctly for all eight directions.
- Camera remains centered and stable while moving.

### Milestone 4: Payon biome

**Deliverables**

- `64x64` terrain grid.
- Stream, bridge, paths, trees, mushrooms, rocks, and flowers.
- Fog and forest palette.
- Prop collision and spawn zones.
- Repeated props moved to instancing if draw calls are high.

**Verify**

- The scene reads as a forest at a glance.
- The bridge and paths are walkable.
- Props do not visibly float or sink into sloped terrain.
- The player and Porings remain readable against the background.

### Milestone 5: Combat and death polish

**Deliverables**

- Attack range and cooldown.
- Damage numbers and target HP.
- Hurt/death animations.
- Poring respawn scheduling.
- Deterministic RNG injection.
- Passive Poring behavior locked to the data file.

**Verify**

- Fixed-seed tests produce the same damage and loot sequence.
- A Poring cannot be killed twice or emit two loot rolls.
- Clicking a dead Poring does not target it.
- No hostile behavior appears accidentally.

### Milestone 6: Loot and inventory hardening

**Deliverables**

- Weighted loot table.
- Floor item pickup.
- 20-slot inventory.
- Stack limits and full-inventory behavior.
- Item tooltips or labels.
- Optional local persistence only if it does not expand the tracer slice.

**Verify**

- Unit tests cover stack placement, capacity, and full-inventory behavior.
- A floor item cannot be picked up twice.
- Loot remains stable between death and pickup.

### Milestone 7: Performance and visual calibration

**Deliverables**

- Internal resolution/pixel filtering settings.
- Draw-call and texture-memory review.
- Fog/light balance.
- Camera calibration against the reference sheet.
- Basic browser smoke test.

**Verify**

- Target 60 FPS at `800x600` with at least 16 Porings and the initial prop set on a mid-range laptop.
- No visible sprite shimmer during camera movement.
- No transparent sorting errors at the camera’s min/max zoom.
- The final build contains no audio assets or audio API calls.

## 15. Testing strategy

### Unit tests

Test pure functions and systems without Three.js:

- Camera profile constants and spherical position math.
- Camera smoothing and clamping.
- Pointer-to-NDC conversion.
- A* pathfinding and diagonal corner rules.
- Attack cooldown and damage.
- Death exactly once.
- Weighted loot selection with a seeded RNG.
- Inventory stacking and capacity.

### Browser tests

Use a deterministic URL or test hook:

```text
/?seed=1337&profile=classic2003
```

The test should:

1. Wait for the map-ready signal.
2. Click a ground coordinate.
3. Wait for the player to arrive.
4. Right-drag and assert the camera yaw changed.
5. Wheel and assert distance changed within bounds.
6. Click a known Poring.
7. Wait for death and floor loot.
8. Click the floor item.
9. Open inventory and assert the item quantity.

Use fixed viewport dimensions in the test. Do not make the test depend on the current browser window size.

### Visual checks

Maintain a small set of reference screenshots:

- Default camera.
- Close zoom.
- Far zoom.
- Rotated camera.
- Combat hit.
- Poring death.
- Inventory open.

The goal is not pixel-perfect asset matching. The goal is matching composition, scale, camera behavior, color density, and motion cadence.

## 16. Performance and rendering budgets

Initial budgets:

- 16–20 Poring actors.
- Fewer than 200 visible draw calls before instancing.
- One terrain mesh plus a small number of prop batches.
- One sprite material instance per actor initially; optimize only if profiling requires it.
- No post-processing chain in the tracer bullet.
- No real-time shadow maps in the tracer bullet; use blob shadows and baked/vertex-color darkening.

Three.js `SpriteMaterial` supports `sizeAttenuation`; leave it enabled for the classic perspective behavior. Do not replace it with an orthographic material merely to make sprites appear uniform.

## 17. Risks and decisions to revisit

1. **Exact 2003 constants:** The public evidence is reconstructed rather than an official specification. Keep camera values in a named profile and validate against a captured reference before calling the look final.
2. **Sprite anchoring:** Transparent padding and foot anchors vary by artist. Store anchor metadata per frame instead of hard-coding a single center for all actors.
3. **Transparent sorting:** Trees, water, shadows, and sprites can sort incorrectly. Solve the actual case with a small render-order policy; do not build a general sorting engine preemptively.
4. **Terrain click ambiguity:** A prop may intercept a ground ray. Define whether clicking a prop walks behind it or does nothing; the first slice may simply reject the click.
5. **Loot authenticity:** Server drop tables differ. Keep the simplified table data-driven and document that it is not a promise of exact RO rates.
6. **Asset licensing:** The visual goal can be achieved with original pixel art. Do not let a missing asset push the project toward extracting client data.
7. **Keyboard movement:** Adding it by default would make the game feel unlike the reference. Keep it optional until the mouse-first loop is accepted.

## 18. Definition of done

The requested slice is done when a new user can:

- Start the game without setup instructions beyond launching the dev server.
- See a Payon-inspired 3D forest from the classic camera.
- Move with left-click and use the classic camera controls.
- See 2D directional player and Poring sprites moving over the 3D ground.
- Click a Poring, watch the player approach and attack it, and see it die.
- Receive deterministic random loot.
- Pick up loot and inspect it in the inventory.
- Complete the loop with no sound enabled or required.
- Run the unit and browser smoke tests successfully.

## 19. Research sources

- [WarpPortal: Ragnarok Online Basic Controls](https://support.warpportal.com/kb/a630/ragnarok-online-basic-controls.aspx) — official movement, attack, shortcuts, and inventory behavior.
- [Official Ragnarok camera controls](https://renewal.playragnarok.com/gameguide/howtoplay_interface05.aspx) — right-drag rotation, `Shift` height, `Control` zoom.
- [iRO Wiki: Basic Game Control](https://irowiki.org/wiki/Basic_Game_Control) — detailed classic mouse/keyboard mappings and camera reset behavior.
- [Ragnarok Research Lab: Camera Controls](https://ragnarokresearchlab.github.io/rendering/camera-controls/) — perspective projection, narrow FOV, camera angles, coordinate normalization, and classic zoom estimates.
- [Ragnarok Research Lab: Coordinate Systems](https://ragnarokresearchlab.github.io/rendering/coordinate-systems/) — GAT tile scale and 3D/2D coordinate relationships.
- [Ragnarok File Formats research](https://github.com/rdw-archive/RagnarokFileFormats) — 3D map data, 2D actor data, and eight-direction animation organization.
- [roBrowser legacy camera source](https://github.com/vthibault/roBrowser/blob/master/src/Renderer/Camera.js) and [renderer source](https://github.com/vthibault/roBrowser/blob/master/src/Renderer/Renderer.js) — reconstruction of classic movement/camera smoothing and a 15-degree projection.
- [roBrowser legacy camera preferences](https://github.com/vthibault/roBrowser/blob/master/src/Preferences/Camera.js) — older default zoom value used for the `classic2003` profile.
- [roBrowserLegacy camera source](https://github.com/MrAntares/roBrowserLegacy/blob/master/src/Renderer/Camera.js) — later pre-Renewal profile and comparison constants.
- [Goro scene projection source](https://github.com/kivutar/goro/blob/main/game/scene_projection.go) and [camera source](https://github.com/kivutar/goro/blob/main/game/camera.go) — independent pre-Renewal reconstruction used to identify version drift.
- [Three.js PerspectiveCamera](https://threejs.org/docs/pages/PerspectiveCamera.html), [Sprite](https://threejs.org/docs/pages/Sprite.html), [SpriteMaterial](https://threejs.org/docs/pages/SpriteMaterial.html), and [Raycaster](https://threejs.org/docs/pages/Raycaster.html) — renderer APIs used by the implementation.
- [Poring database reference](https://db.irowiki.org/db/monster-info/1002) and [drop system reference](https://irowiki.org/wiki/Drop_System) — baseline Poring statistics and common loot categories.
- [Payon field spawn reference](https://www.dodsrv.com/ragnarok/db/regions/Payon_Fields.html) — example of Poring-containing Payon fields; used as inspiration, not as copied level data.
