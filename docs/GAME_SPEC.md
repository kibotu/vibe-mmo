# Ragnarok Online Prototype - Technical Specification

## 1. Camera System

### 1.1 Camera Type
- **Perspective**: Isometric-style perspective camera with customizable angle
- **Projection**: Perspective projection with configurable FOV (recommended: 45-60°)

### 1.2 Camera Properties
```
Position: Offset from player character
- Default Height: 15-20 units above terrain
- Default Distance: 12-18 units from player
- Default Angle: 30-45° elevation from horizontal
```

### 1.3 Camera Controls
**Horizontal Rotation:**
- Right mouse button hold + drag left/right to rotate camera
- Full 360° rotation around the character
- Character remains at center while camera orbits
- Rotation speed: 120-180°/second while dragging
- Double right-click to reset camera to face north (default orientation)

**Vertical Tilt (Elevation Angle):**
- Hold Shift + mouse wheel up/down to adjust camera elevation
- Or: Hold Shift + Ctrl + mouse wheel for finer control
- Angle range: 40° to 65° from horizontal
- Default angle: ~65° (high isometric view)
- 5° increment per scroll step
- Double right-click while holding Shift to reset to default elevation

**Zoom:**
- Mouse wheel up/down for zoom in/out
- Or: Hold Ctrl + right mouse button + drag mouse up/down
- Min distance: 8 units from player
- Max distance: 25 units from player
- Zoom speed: 2 units per scroll tick
- Default distance: 15 units
- Hold Ctrl + double right-click to reset zoom to default

**Follow:**
- Camera position is rigidly locked to player position
- Camera moves instantly with player (no lag/interpolation)
- Camera offset updates based on current rotation, tilt, and zoom
- Offset calculation: 
  ```
  // Spherical to Cartesian coordinates
  horizontalAngle = currentHorizontalRotation (0-360°)
  verticalAngle = currentElevation (40-65°)
  distance = currentZoom (8-25 units)
  
  x = distance * cos(verticalAngle) * sin(horizontalAngle)
  y = distance * sin(verticalAngle)
  z = distance * cos(verticalAngle) * cos(horizontalAngle)
  
  cameraPos = playerPos + (x, y, z)
  cameraLookAt = playerPos
  ```

**Special Map Behavior:**
- Some indoor maps disable horizontal rotation (fixed viewpoint)
- Data files control per-map camera restrictions:
  - `indoorRswTable.txt` - lists maps with disabled rotation
  - `viewpointtable.txt` - defines custom angle ranges per map

### 1.4 Camera Collision
- Raycast from player to camera position
- If terrain/object intersects, move camera forward along ray
- Minimum camera distance: 3 units from player
- Smoothly return to default distance when obstruction clears

### 1.5 Camera Shake (Optional for prototype)
- Light shake on hit/critical hit
- Amplitude: 0.1-0.3 units
- Duration: 0.2-0.5 seconds

---

## 2. Player Movement System

### 2.1 Movement Model
- **Type**: Point-and-click pathfinding with optional WASD support
- **Movement Speed**: 150 units/second (base speed)
- **Running Speed**: Not implemented in prototype (all movement is walk speed)

### 2.2 Click-to-Move Mechanics
**Input:**
- Left mouse click on terrain sets destination
- Visual feedback: Ground marker at click position

**Pathfinding:**
- A* pathfinding algorithm on navigation grid
- Grid cell size: 1x1 units
- Maximum path length: 100 cells (recalculate if exceeded)
- Path recalculation: On collision or every 2 seconds if moving

**Path Following:**
- Follow waypoints sequentially
- Waypoint reached threshold: 0.5 units
- If waypoint unreachable within 3 seconds, recalculate or stop

### 2.3 WASD Movement (Optional Secondary Control)
- W/A/S/D keys for directional movement
- Movement relative to camera orientation
- Speed: Same as click-to-move (150 units/second)
- Overrides click-to-move when active

### 2.4 Character Rotation
- Instant rotation to face movement direction
- Smooth rotation interpolation: 360°/second
- Face target when stationary and target selected

### 2.5 Movement States
```
IDLE: Standing still, playing idle animation
MOVING: Walking to destination
ATTACKING: Movement locked, facing target
DEAD: No movement, ragdoll or death pose
```

### 2.6 Collision Detection
- Character cylinder collider
  - Radius: 0.4 units
  - Height: 1.8 units
- Collision layers: Terrain, objects, other characters
- Slide along obstacles if movement path obstructed

### 2.7 Terrain Interaction
- Character stays grounded using terrain raycast
- Raycast downward from character center
- Max step height: 0.5 units
- Character Y position = terrain height + 0.05 units offset

---

## 3. Billboard Sprite System

### 3.1 Billboard Concept
Billboards are 2D sprites rendered in 3D space that always face the camera, mimicking the classic RO aesthetic.

### 3.2 Player Billboard Specification

**Sprite Sheet:**
- Format: PNG with alpha transparency
- Resolution: 128x128 pixels per frame (minimum)
- Layout: 8 directions × N animation frames
- Directions: South, Southwest, West, Northwest, North, Northeast, East, Southeast
- Pivot point: Bottom center of sprite (feet position)

**Animations Required:**
```
IDLE: 1-3 frames, looping, 0.5s duration
WALK: 6-8 frames, looping, 0.6s duration
ATTACK: 4-6 frames, non-looping, 0.4s duration
HIT: 1-2 frames, non-looping, 0.2s duration
DEAD: 1 frame, static
```

**Billboard Properties:**
- Always face camera (Y-axis rotation only)
- Scale: 1.5-2.0 units height in world space
- Render queue: Transparent
- Z-test: Enabled (billboards occlude each other properly)

**Direction Calculation:**
- Calculate angle between character forward and camera forward
- Snap to nearest 45° increment
- Select appropriate sprite direction index

### 3.3 NPC Billboard Specification

**Sprite Sheet:**
- Same format as player (128x128 per frame)
- Minimum required: 1 direction (South-facing)
- Preferred: 4 directions for major NPCs

**Animations Required:**
```
IDLE: 1-3 frames, looping
```

**Interaction:**
- Display name plate above sprite
- Name plate height: Sprite height + 0.5 units
- Hover effect: Slight color tint or outline
- Clickable radius: 2 units from NPC center

### 3.4 Monster Billboard Specification

**Sprite Sheet:**
- Same format as player (128x128 per frame)
- 8 directions for smooth appearance
- 4 directions acceptable for prototype

**Animations Required:**
```
IDLE: 2-4 frames, looping, 0.8s duration
WALK: 4-6 frames, looping, 0.6s duration
ATTACK: 3-5 frames, non-looping, 0.5s duration
HIT: 1-2 frames, non-looping, 0.15s duration
DEAD: 3-4 frames, non-looping, 0.8s duration (then fade)
```

**Additional Properties:**
- Health bar above sprite
  - Size: 1.0 × 0.15 units
  - Height offset: Sprite height + 0.3 units
  - Colors: Red (health), Black (background)
- Aggro radius visualization (debug mode): Red circle on ground

### 3.5 Billboard Rendering Technical Details

**Material:**
- Shader: Unlit/Transparent or custom billboard shader
- Texture filtering: Point (nearest neighbor) for pixel-art look, or Bilinear for smooth
- Alpha cutoff: 0.1 (for transparency)

**Sorting:**
- Sort billboards by distance from camera (painter's algorithm)
- Or use Y-position for ground-relative sorting
- Update sorting every frame or on position change

**Performance Optimization:**
- Use texture atlas for all sprites
- Batch billboards using same material/atlas
- Occlusion culling: Frustum culling minimum
- LOD: Not required for prototype

---

## 4. Terrain System - Payon Map

### 4.1 Terrain Overview
Payon is a small forest village map, ideal for prototype with manageable size and variety.

**Map Dimensions:**
- Size: 200 × 200 units (40,000 sq units)
- Grid resolution: 1 unit per cell = 200 × 200 grid
- Height range: 0-15 units elevation variance

### 4.2 Terrain Mesh

**Generation Method:**
- Heightmap-based terrain generation
- Heightmap resolution: 512 × 512 grayscale image
- Each pixel maps to terrain grid cell height
- Height calculation: `height = (pixelValue / 255.0) * maxHeight`

**Mesh Properties:**
- Vertices: Grid corners with height values
- Triangles: Two triangles per grid square
- Normal calculation: Average face normals for vertex normals
- UVs: Tiled texture coordinates based on world position

### 4.3 Terrain Texturing

**Texture Layers (Splatmap approach):**
1. **Base Grass**: Green grass texture (default)
2. **Dirt Paths**: Brown dirt for walkways
3. **Stone**: Gray stone for village areas
4. **Forest Floor**: Dark soil with leaves

**Texture Specifications:**
- Resolution: 1024 × 1024 per texture (tileable)
- Format: Compressed texture (DXT5/BC3 or equivalent)
- Tiling: 0.5 units per texture repeat (adjust for visual quality)

**Splatmap:**
- RGBA texture (512 × 512 resolution)
- R channel: Grass weight
- G channel: Dirt weight
- B channel: Stone weight
- A channel: Forest floor weight
- Shader blends textures based on channel weights

### 4.4 Terrain Features for Payon

**Required Elements:**
1. **Central Village Area**
   - Flat terrain (height: 0-2 units variance)
   - Stone texture dominance
   - Area: ~30 × 30 units in map center

2. **Pathways**
   - Dirt paths connecting village to edges
   - Width: 3-5 units
   - Slight depression (0.2 units below adjacent terrain)

3. **Forest Areas**
   - Elevated terrain (2-8 units)
   - Forest floor texture
   - Perimeter of map (60% coverage)

4. **Small Hills**
   - 2-3 gentle hills (height: 8-15 units)
   - Grass texture
   - Smooth slopes (max 45° angle)

### 4.5 Navigation Grid

**Grid Generation:**
- Overlay navigation grid on terrain mesh
- Grid cell: 1 × 1 unit
- Cell walkability determined by:
  - Slope < 45°
  - No building/object collision
  - Not marked as unwalkable in data

**Data Structure:**
```
NavigationCell {
    x: int
    y: int
    height: float
    walkable: bool
    cost: float (default 1.0)
}
```

**Grid Serialization:**
- Store as 2D boolean array for walkability
- Store height values in separate array
- File format: Binary or JSON for prototype

### 4.6 Terrain Collision

**Collision Mesh:**
- Simplified mesh derived from terrain mesh
- Same vertex positions as visual mesh
- Physics material: Friction 0.6, Bounciness 0.0

**Raycasting:**
- Use terrain collision for character grounding
- Ray origin: Character position + 1 unit up
- Ray direction: Down (0, -1, 0)
- Max ray distance: 5 units

### 4.7 Terrain Decorations (Simplified for Prototype)

**Static Objects:**
1. **Trees**: 10-15 tree models/billboards placed in forest areas
2. **Buildings**: 3-5 simple house models in village
3. **Props**: Barrels, crates, signs (5-10 total)

**Placement Rules:**
- Objects placed on terrain surface (raycast to find height)
- Minimum spacing: 2 units between objects
- Mark grid cells beneath objects as unwalkable

### 4.8 Map Boundaries

**Boundary Handling:**
- Invisible collision walls at map edges
- Position: x=0, x=200, y=0, y=200
- Height: 0-30 units (taller than max terrain + character)
- Prevent character from walking out of bounds

**Visual Boundaries:**
- Fog/mist effect at edges (optional)
- Terrain slopes downward at boundaries
- Dense tree placement at edges

---

## 5. Prototype Implementation Priority

### Phase 1: Core Systems
1. Terrain generation and rendering
2. Camera system with basic controls
3. Player billboard rendering
4. Click-to-move on flat terrain

### Phase 2: Movement & Navigation
1. Navigation grid generation
2. A* pathfinding implementation
3. Character collision
4. Terrain height following

### Phase 3: Entities
1. NPC billboards (static)
2. Monster billboards (with idle animation)
3. Basic interaction system (click NPC to display name)

### Phase 4: Polish
1. Camera collision with terrain
2. Animation state machine for player
3. Health bars for monsters
4. Terrain texturing and splatmap

---

## 6. Technical Constraints & Assumptions

**Target Platform:**
- Desktop PC (Windows/Mac/Linux)
- Minimum specs: 4GB RAM, OpenGL 3.3+ or DirectX 11

**Framework/Engine:**
- Unity, Unreal, or custom engine with 3D rendering
- Asset pipeline supports PNG textures and OBJ/FBX models

**Performance Targets:**
- 60 FPS minimum with 1 player, 10 NPCs, 20 monsters on screen
- Terrain render time: < 2ms per frame
- Pathfinding: < 5ms for path calculation

**Prototype Scope Limitations:**
- Single map only (Payon)
- No networking/multiplayer
- No combat system (attack animations only)
- No item/inventory system
- Simplified AI (monsters don't move in prototype)

---

## 7. Data Formats & File Structure

### 7.1 Sprite Data
```
sprites/
├── player/
│   ├── player_idle.png
│   ├── player_walk.png
│   └── player_attack.png
├── npcs/
│   └── merchant_idle.png
└── monsters/
    ├── poring_idle.png
    ├── poring_walk.png
    └── poring_attack.png
```

### 7.2 Map Data
```
maps/
└── payon/
    ├── heightmap.png (512x512 grayscale)
    ├── splatmap.png (512x512 RGBA)
    ├── navigation.dat (binary grid data)
    └── objects.json (decoration placement)
```

### 7.3 Configuration Files
```json
// camera_config.json
{
    "defaultDistance": 15,
    "defaultAngle": 35,
    "minDistance": 8,
    "maxDistance": 30,
    "rotationSpeed": 120,
    "zoomSpeed": 2
}

// movement_config.json
{
    "walkSpeed": 150,
    "rotationSpeed": 360,
    "collisionRadius": 0.4,
    "groundOffset": 0.05
}
```

---

## 8. Testing Checklist

- [ ] Camera rotates smoothly 360° around player
- [ ] Camera zoom works within defined limits
- [ ] Camera follows player with appropriate lag
- [ ] Click-to-move sets destination and player walks to it
- [ ] Player navigates around obstacles using pathfinding
- [ ] Player billboard faces camera at all times
- [ ] Billboard animations play correctly (idle, walk)
- [ ] NPCs display and are clickable
- [ ] Monsters display with health bars
- [ ] Terrain renders with correct textures
- [ ] Character stays grounded on terrain at all heights
- [ ] Character cannot walk outside map boundaries
- [ ] Navigation grid accurately represents walkable areas

---

**Document Version:** 1.0  
**Last Updated:** 2025-11-17  
**Status:** Ready for Implementation