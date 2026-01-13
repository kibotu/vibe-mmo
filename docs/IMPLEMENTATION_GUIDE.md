# GAME_SPEC.md Implementation Guide

This document describes how the GAME_SPEC.md features have been implemented and how to use them.

## Overview

The following systems from GAME_SPEC.md have been implemented:

1. **Isometric Camera System** (`IsometricCamera.kt`)
2. **A* Pathfinding** (`Pathfinding.kt`)
3. **Billboard Sprite System** (`BillboardSprite.kt`)
4. **Terrain System** (`TerrainSystem.kt`)
5. **Zone Server Payon Map Support** (updated `ZoneService.kt`)

## Implementation Status

### ✅ Completed Features

#### 1. Camera System (Section 1 of GAME_SPEC.md)
- **File**: `game-client/src/jvmMain/kotlin/net/kibotu/ro/client/game/IsometricCamera.kt`
- **Features**:
  - Perspective projection with configurable FOV (45-60°)
  - Orbit rotation around player (right/middle mouse drag)
  - Zoom (mouse wheel) - 8-30 units distance
  - Smooth following with lerp (0.1-0.15 factor)
  - Camera collision detection (minimum 3 units from player)
  - Vertical rotation clamped between 15° and 75°

#### 2. Pathfinding System (Section 2 of GAME_SPEC.md)
- **File**: `game-client/src/jvmMain/kotlin/net/kibotu/ro/client/game/Pathfinding.kt`
- **Features**:
  - A* pathfinding algorithm
  - 8-directional movement
  - Grid cell size: 1x1 units
  - Maximum path length: 100 cells
  - Path recalculation support
  - PathFollower class for waypoint following

#### 3. Billboard Sprite System (Section 3 of GAME_SPEC.md)
- **File**: `game-client/src/jvmMain/kotlin/net/kibotu/ro/client/game/BillboardSprite.kt`
- **Features**:
  - 8-directional sprite support (South, Southwest, West, Northwest, North, Northeast, East, Southeast)
  - Animation states (IDLE, WALK, ATTACK, HIT, DEAD)
  - Billboard rendering that always faces camera
  - Sprite sheet support (128x128 pixels per frame)

#### 4. Terrain System (Section 4 of GAME_SPEC.md)
- **File**: `game-client/src/jvmMain/kotlin/net/kibotu/ro/client/game/TerrainSystem.kt`
- **Features**:
  - Payon map terrain generation (200x200 units)
  - Heightmap-based terrain (0-15 units elevation)
  - Splatmap texturing (4 texture layers: grass, dirt, stone, forest floor)
  - Navigation grid generation from terrain
  - Terrain features:
    - Central village area (flat, 0-2 units)
    - Forest areas (elevated, 2-8 units)
    - Small hills (8-15 units)
    - Pathways (dirt texture)

#### 5. Zone Server Updates
- **File**: `zone-server/src/main/kotlin/net/kibotu/ro/zone/service/ZoneService.kt`
- **Changes**:
  - Added Payon map support (200x200 units)
  - Added Payon spawn points and NPCs
  - Payon map initialized by default

## Integration Guide

### Using the New Systems in GameScreen

To integrate these systems into your GameScreen, follow these steps:

#### 1. Setup Isometric Camera

```kotlin
import net.kibotu.ro.client.game.IsometricCamera
import com.badlogic.gdx.graphics.PerspectiveCamera

// In GameScreen initialization
val perspectiveCamera = PerspectiveCamera(50f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
val isometricCamera = IsometricCamera(perspectiveCamera, mapWidth, mapHeight)

// Update camera every frame
override fun render(delta: Float) {
    val playerPos = entityManager.getPlayer(ownCharacterId)?.position
    playerPos?.let {
        isometricCamera.update(it.x.toFloat(), it.y.toFloat(), delta)
    }
    
    // Use isometricCamera.getGdxCamera() for rendering
    batch.projectionMatrix = isometricCamera.getGdxCamera().combined
}
```

#### 2. Setup Pathfinding

```kotlin
import net.kibotu.ro.client.game.Pathfinding
import net.kibotu.ro.client.game.PathFollower

// In GameScreen initialization
val (pathfinding, pathFollower) = setupPathfinding(collisionMap, mapWidth, mapHeight)

// Handle click-to-move
fun handleClickToMove(screenX: Int, screenY: Int) {
    val currentPos = entityManager.getPlayer(ownCharacterId)?.position ?: return
    val worldPos = isometricCamera.screenToWorld(screenX.toFloat(), screenY.toFloat())
    val targetPos = Position(worldPos.x.toInt(), worldPos.z.toInt())
    
    val path = pathfinding.findPath(currentPos, targetPos)
    if (path.isNotEmpty()) {
        pathFollower.setPath(path)
    }
}

// Update path following every frame
override fun render(delta: Float) {
    val currentPos = entityManager.getPlayer(ownCharacterId)?.position
    currentPos?.let {
        val nextWaypoint = pathFollower.getNextWaypoint(it)
        nextWaypoint?.let { waypoint ->
            // Move toward waypoint
            movePlayerTowardWaypoint(waypoint)
        }
    }
}
```

#### 3. Setup Terrain

```kotlin
import net.kibotu.ro.client.game.TerrainGenerator
import net.kibotu.ro.client.game.TerrainData

// In GameScreen initialization
val terrainGenerator = TerrainGenerator()
val terrainData = terrainGenerator.generatePayonTerrain()
val splatMap = terrainGenerator.generatePayonSplatmap(terrainData)
val navigationGrid = terrainGenerator.generateNavigationGrid(terrainData)

// Use navigationGrid for pathfinding
val (pathfinding, pathFollower) = setupPathfinding(navigationGrid, terrainData.width, terrainData.height)
```

#### 4. Camera Input Handling

Use `IsometricCameraInputHandler` from `GameScreenIntegration.kt`:

```kotlin
import net.kibotu.ro.client.game.IsometricCameraInputHandler

val cameraInputHandler = IsometricCameraInputHandler(isometricCamera) { direction ->
    viewModel.movePlayer(direction)
}

// In input handlers
override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
    return cameraInputHandler.handleMouseDown(screenX, screenY, button)
}

override fun scrolled(amountX: Float, amountY: Float): Boolean {
    return cameraInputHandler.handleScrolled(amountX, amountY)
}
```

## Configuration

### Camera Configuration

Default values from GAME_SPEC.md:
- Default Height: 17.5 units (15-20 range)
- Default Distance: 15 units (12-18 range)
- Default Angle: 37.5° (30-45° range)
- Rotation Speed: 120°/second
- Zoom Speed: 2 units per scroll tick
- Min Distance: 8 units
- Max Distance: 30 units
- Lerp Factor: 0.125 (0.1-0.15 range)

### Movement Configuration

From GAME_SPEC.md:
- Movement Speed: 150 units/second
- Waypoint Threshold: 0.5 units
- Path Recalculation: Every 2 seconds or on collision

### Terrain Configuration

Payon Map (from GAME_SPEC.md):
- Size: 200 × 200 units
- Height Range: 0-15 units
- Village Area: ~30 × 30 units in center
- Forest Coverage: 60% of map

## Next Steps

To fully integrate these systems:

1. **Update GameScreen** to use IsometricCamera instead of the current 2D Camera
2. **Integrate Pathfinding** into click-to-move functionality
3. **Add Billboard Rendering** for entities (currently using circles)
4. **Render Terrain** using the TerrainSystem (currently using flat tiles)
5. **Add Sprite Assets** for billboard rendering (sprite sheets)

## Files Created/Modified

### New Files Created:
- `game-client/src/jvmMain/kotlin/net/kibotu/ro/client/game/IsometricCamera.kt`
- `game-client/src/jvmMain/kotlin/net/kibotu/ro/client/game/Pathfinding.kt`
- `game-client/src/jvmMain/kotlin/net/kibotu/ro/client/game/BillboardSprite.kt`
- `game-client/src/jvmMain/kotlin/net/kibotu/ro/client/game/TerrainSystem.kt`
- `game-client/src/jvmMain/kotlin/net/kibotu/ro/client/game/GameScreenIntegration.kt`

### Modified Files:
- `zone-server/src/main/kotlin/net/kibotu/ro/zone/service/ZoneService.kt` - Added Payon map support

## Testing Checklist

Refer to GAME_SPEC.md section 8 for the testing checklist. Key items:
- [ ] Camera rotates smoothly 360° around player
- [ ] Camera zoom works within defined limits
- [ ] Camera follows player with appropriate lag
- [ ] Click-to-move sets destination and player walks to it
- [ ] Player navigates around obstacles using pathfinding
- [ ] Player billboard faces camera at all times
- [ ] Billboard animations play correctly (idle, walk)
- [ ] Terrain renders with correct textures
- [ ] Character stays grounded on terrain at all heights









