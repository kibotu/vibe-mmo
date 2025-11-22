# Game Engine Technical Specification
## Ragnarok Online - Basic Game Part

**Version:** 1.0  
**Last Updated:** 2024  
**Status:** Specification for Minimal Prototype

---

## Table of Contents

1. [Overview](#overview)
2. [World Representation](#world-representation)
3. [Camera System](#camera-system)
4. [Controls & Input](#controls--input)
5. [Mouse Movement & Camera Handling](#mouse-movement--camera-handling)
6. [Rendering Pipeline](#rendering-pipeline)
7. [Entity System](#entity-system)
8. [Movement System](#movement-system)
9. [Collision Detection](#collision-detection)
10. [Network Synchronization](#network-synchronization)
11. [Minimal Prototype Requirements](#minimal-prototype-requirements)
12. [Technical Implementation Details](#technical-implementation-details)

---

## Overview

This specification defines the core game engine components for a **Ragnarok Online-style MMORPG**. The game features **2D sprite-based characters and monsters** rendered in a **3D world space** (isometric/top-down perspective), creating the classic 2.5D visual style.

### Core Principles

- **2D Sprites in 3D Space**: Characters and monsters are 2D sprites that can move in X, Y, Z coordinates
- **Isometric/Top-Down View**: Camera positioned at an angle looking down at the world
- **Tile-Based Movement**: Discrete movement on a grid system (can be smoothed visually)
- **Server-Authoritative**: Server validates all movement and game state
- **Optimistic Client Updates**: Client updates immediately for responsiveness, syncs with server

### Visual Style

- **Perspective**: Isometric or top-down (45-degree angle recommended)
- **Tile Size**: 32x32 pixels (configurable)
- **Sprite Size**: Typically 32x48 pixels for characters (1x1.5 tiles)
- **Depth Sorting**: Entities rendered based on Y position (lower Y = rendered first)

---

## World Representation

### Coordinate System

The world uses a **3D coordinate system** with discrete tile positions:

```
World Coordinates:
- X: Horizontal (left/right)
- Y: Vertical (up/down in world space, affects depth sorting)
- Z: Height/elevation (optional, for multi-level maps)

Screen Coordinates:
- Screen X: Horizontal pixel position
- Screen Y: Vertical pixel position
```

### Map Structure

```kotlin
data class MapData(
    val name: String,
    val width: Int,              // Number of tiles horizontally
    val height: Int,              // Number of tiles vertically
    val tileSize: Int = 32,       // Pixels per tile
    val collisionMap: CollisionMap,  // Collision data
    val spawnPoints: List<Position>,  // Player spawn locations
    val npcs: List<NPCData>,      // Static NPC definitions
    val monsterSpawns: List<MonsterSpawn>  // Monster spawn points
)
```

### Tile System

- **Ground Tiles**: Base layer (grass, dirt, stone, etc.)
- **Collision Layer**: Defines walkable/non-walkable areas
- **Object Layer**: Trees, buildings, decorations (optional for prototype)
- **Height Map**: For elevation changes (optional for prototype)

### Map Bounds

- **Minimum**: (0, 0)
- **Maximum**: (mapWidth - 1, mapHeight - 1)
- **Validation**: Server enforces bounds checking

---

## Camera System

### Camera Type

**Follow Camera** - Camera follows the player character, keeping them centered on screen.

### Camera Implementation

```kotlin
class Camera(
    var targetX: Float,      // Target X position (world coordinates)
    var targetY: Float,      // Target Y position (world coordinates)
    var viewportWidth: Float,   // Screen width in pixels
    var viewportHeight: Float,  // Screen height in pixels
    var tileSize: Float = 32f
) {
    // Calculate camera position (top-left corner of viewport)
    val cameraX: Float
        get() = targetX * tileSize - viewportWidth / 2
    
    val cameraY: Float
        get() = targetY * tileSize - viewportHeight / 2
    
    // World-to-screen coordinate conversion
    fun worldToScreen(worldX: Float, worldY: Float): Offset {
        return Offset(
            worldX * tileSize - cameraX,
            worldY * tileSize - cameraY
        )
    }
    
    // Screen-to-world coordinate conversion (for mouse clicks)
    fun screenToWorld(screenX: Float, screenY: Float): Position {
        return Position(
            ((screenX + cameraX) / tileSize).toInt(),
            ((screenY + cameraY) / tileSize).toInt()
        )
    }
}
```

### Camera Behavior

1. **Target**: Always follows player position
2. **Smoothing**: Optional smooth interpolation (not required for prototype)
3. **Bounds**: Camera clamped to map boundaries (don't show outside map)
4. **Update**: Camera updates every frame based on player position

### Camera Bounds Clamping

```kotlin
fun clampCamera(camera: Camera, mapWidth: Int, mapHeight: Int) {
    val maxCameraX = mapWidth * camera.tileSize - camera.viewportWidth
    val maxCameraY = mapHeight * camera.tileSize - camera.viewportHeight
    
    camera.cameraX = camera.cameraX.coerceIn(0f, maxCameraX)
    camera.cameraY = camera.cameraY.coerceIn(0f, maxCameraY)
}
```

### Viewport Culling

Only render entities and tiles within the camera viewport:

```kotlin
fun isInViewport(worldX: Float, worldY: Float, camera: Camera): Boolean {
    val screenPos = camera.worldToScreen(worldX, worldY)
    return screenPos.x >= -tileSize && 
           screenPos.x <= camera.viewportWidth + tileSize &&
           screenPos.y >= -tileSize && 
           screenPos.y <= camera.viewportHeight + tileSize
}
```

---

## Controls & Input

### Movement Controls

**Primary Controls:**
- **WASD Keys**: W (up), A (left), S (down), D (right)
- **Arrow Keys**: Up, Left, Down, Right (alternative)
- **Both supported**: Player can use either control scheme

### Input Handling

```kotlin
// Input state tracking
var keysPressed: Set<Key> = emptySet()

// On key down
onKeyEvent { event ->
    when {
        event.type == KeyEventType.KeyDown -> {
            when (event.key) {
                Key.W, Key.DirectionUp -> {
                    keysPressed += event.key
                    movePlayer("up")
                }
                Key.S, Key.DirectionDown -> {
                    keysPressed += event.key
                    movePlayer("down")
                }
                Key.A, Key.DirectionLeft -> {
                    keysPressed += event.key
                    movePlayer("left")
                }
                Key.D, Key.DirectionRight -> {
                    keysPressed += event.key
                    movePlayer("right")
                }
            }
        }
        event.type == KeyEventType.KeyUp -> {
            keysPressed -= event.key
        }
    }
}
```

### Continuous Movement

When a key is held down, movement continues at intervals:

- **Movement Rate**: 150ms per tile movement (configurable)
- **Implementation**: Coroutine loop checks `keysPressed` set every 150ms
- **Immediate Response**: First movement happens immediately on key press

### Future Controls (Not Required for Prototype)

- **Space**: Attack/interact
- **Enter**: Open chat
- **Tab**: Target selection
- **Number Keys**: Skill shortcuts

---

## Mouse Movement & Camera Handling

### Overview

The mouse provides multiple interaction modes for both player movement and camera control. This section defines how mouse input is processed for click-to-move, camera panning, and entity interaction.

### Mouse Input Modes

The game supports several mouse interaction modes:

1. **Click-to-Move**: Left-click on ground to move player
2. **Camera Panning**: Middle-click drag or edge scrolling to move camera
3. **Entity Interaction**: Right-click on entities (NPCs, monsters, players)
4. **Camera Zoom**: Mouse wheel for zoom in/out (optional)

### Click-to-Move System

#### Implementation

```kotlin
class MouseInputHandler(
    private val camera: Camera,
    private val pathfinder: Pathfinder,
    private val movementHandler: MovementHandler
) {
    private var lastClickTime = 0L
    private val doubleClickThreshold = 300L // milliseconds
    
    fun handleMouseClick(screenX: Float, screenY: Float, button: MouseButton) {
        when (button) {
            MouseButton.Left -> {
                val worldPos = camera.screenToWorld(screenX, screenY)
                handleLeftClick(worldPos)
            }
            MouseButton.Right -> {
                val worldPos = camera.screenToWorld(screenX, screenY)
                handleRightClick(worldPos)
            }
            MouseButton.Middle -> {
                // Start camera panning
                startCameraPan(screenX, screenY)
            }
        }
    }
    
    private fun handleLeftClick(worldPos: Position) {
        val currentTime = System.currentTimeMillis()
        val isDoubleClick = (currentTime - lastClickTime) < doubleClickThreshold
        lastClickTime = currentTime
        
        if (isDoubleClick) {
            // Double-click: Run to position
            movePlayerTo(worldPos, run = true)
        } else {
            // Single-click: Walk to position
            movePlayerTo(worldPos, run = false)
        }
    }
    
    private fun movePlayerTo(targetPos: Position, run: Boolean) {
        // Find path from current position to target
        val currentPos = getPlayerPosition()
        val path = pathfinder.findPath(currentPos, targetPos)
        
        if (path.isNotEmpty()) {
            // Send path to movement handler
            movementHandler.setPath(path, run)
        } else {
            // Invalid path or unreachable
            showMessage("Cannot reach that location")
        }
    }
}
```

#### Pathfinding

For click-to-move, the client calculates a path using A* pathfinding:

```kotlin
class Pathfinder(
    private val collisionMap: CollisionMap
) {
    fun findPath(start: Position, end: Position): List<Position> {
        // A* pathfinding algorithm
        val openSet = mutableListOf<Node>()
        val closedSet = mutableSetOf<Position>()
        val cameFrom = mutableMapOf<Position, Position>()
        
        val startNode = Node(start, 0f, heuristic(start, end))
        openSet.add(startNode)
        
        while (openSet.isNotEmpty()) {
            val current = openSet.minByOrNull { it.fCost } ?: break
            openSet.remove(current)
            closedSet.add(current.position)
            
            if (current.position == end) {
                // Reconstruct path
                return reconstructPath(cameFrom, start, end)
            }
            
            // Check neighbors
            val neighbors = getNeighbors(current.position)
            for (neighbor in neighbors) {
                if (neighbor in closedSet || !collisionMap.isWalkable(neighbor.x, neighbor.y)) {
                    continue
                }
                
                val tentativeG = current.gCost + 1f
                val existingNode = openSet.find { it.position == neighbor }
                
                if (existingNode == null) {
                    val newNode = Node(neighbor, tentativeG, heuristic(neighbor, end))
                    openSet.add(newNode)
                    cameFrom[neighbor] = current.position
                } else if (tentativeG < existingNode.gCost) {
                    existingNode.gCost = tentativeG
                    existingNode.fCost = tentativeG + existingNode.hCost
                    cameFrom[neighbor] = current.position
                }
            }
        }
        
        return emptyList() // No path found
    }
    
    private fun heuristic(a: Position, b: Position): Float {
        // Manhattan distance
        return (abs(a.x - b.x) + abs(a.y - b.y)).toFloat()
    }
    
    private fun getNeighbors(pos: Position): List<Position> {
        return listOf(
            Position(pos.x, pos.y - 1), // Up
            Position(pos.x, pos.y + 1), // Down
            Position(pos.x - 1, pos.y), // Left
            Position(pos.x + 1, pos.y)  // Right
        )
    }
}
```

#### Path Execution

```kotlin
class MovementHandler(
    private val viewModel: GameViewModel
) {
    private var currentPath: List<Position> = emptyList()
    private var pathIndex = 0
    private var isRunning = false
    
    fun setPath(path: List<Position>, run: Boolean) {
        currentPath = path
        pathIndex = 0
        isRunning = run
        executeNextStep()
    }
    
    private fun executeNextStep() {
        if (pathIndex >= currentPath.size) {
            currentPath = emptyList()
            return
        }
        
        val nextPos = currentPath[pathIndex]
        val currentPos = viewModel.getPlayerPosition()
        
        // Determine direction
        val direction = when {
            nextPos.y < currentPos.y -> "up"
            nextPos.y > currentPos.y -> "down"
            nextPos.x < currentPos.x -> "left"
            nextPos.x > currentPos.x -> "right"
            else -> return
        }
        
        // Move to next position
        viewModel.movePlayer(direction)
        
        // Wait for movement to complete, then move to next step
        val delay = if (isRunning) 100L else 150L // Faster when running
        CoroutineScope(Dispatchers.Default).launch {
            delay(delay)
            pathIndex++
            executeNextStep()
        }
    }
}
```

### Camera Panning

#### Middle-Click Drag

```kotlin
class CameraPanHandler(
    private val camera: Camera
) {
    private var isPanning = false
    private var panStartX = 0f
    private var panStartY = 0f
    private var cameraStartX = 0f
    private var cameraStartY = 0f
    
    fun startPan(screenX: Float, screenY: Float) {
        isPanning = true
        panStartX = screenX
        panStartY = screenY
        cameraStartX = camera.cameraX
        cameraStartY = camera.cameraY
    }
    
    fun updatePan(screenX: Float, screenY: Float) {
        if (!isPanning) return
        
        val deltaX = panStartX - screenX
        val deltaY = panStartY - screenY
        
        camera.cameraX = cameraStartX + deltaX
        camera.cameraY = cameraStartY + deltaY
        
        // Clamp camera to map bounds
        clampCameraToBounds()
    }
    
    fun endPan() {
        isPanning = false
    }
}
```

#### Edge Scrolling

Camera automatically pans when mouse is near screen edges:

```kotlin
class EdgeScrollHandler(
    private val camera: Camera,
    private val viewportWidth: Float,
    private val viewportHeight: Float
) {
    private val edgeScrollZone = 50f // pixels from edge
    private val scrollSpeed = 5f // pixels per frame
    
    fun update(mouseX: Float, mouseY: Float) {
        var deltaX = 0f
        var deltaY = 0f
        
        // Left edge
        if (mouseX < edgeScrollZone) {
            deltaX = scrollSpeed
        }
        // Right edge
        else if (mouseX > viewportWidth - edgeScrollZone) {
            deltaX = -scrollSpeed
        }
        
        // Top edge
        if (mouseY < edgeScrollZone) {
            deltaY = scrollSpeed
        }
        // Bottom edge
        else if (mouseY > viewportHeight - edgeScrollZone) {
            deltaY = -scrollSpeed
        }
        
        if (deltaX != 0f || deltaY != 0f) {
            camera.cameraX += deltaX
            camera.cameraY += deltaY
            clampCameraToBounds()
        }
    }
}
```

#### Camera Mode Toggle

Players can toggle between follow camera and free camera:

```kotlin
enum class CameraMode {
    FOLLOW_PLAYER,  // Camera follows player (default)
    FREE_CAMERA     // Camera can be panned freely
}

class CameraController(
    private val camera: Camera,
    private val playerPosition: Position?
) {
    private var mode = CameraMode.FOLLOW_PLAYER
    
    fun toggleMode() {
        mode = when (mode) {
            CameraMode.FOLLOW_PLAYER -> CameraMode.FREE_CAMERA
            CameraMode.FREE_CAMERA -> CameraMode.FOLLOW_PLAYER
        }
    }
    
    fun update() {
        when (mode) {
            CameraMode.FOLLOW_PLAYER -> {
                playerPosition?.let { pos ->
                    camera.targetX = pos.x.toFloat()
                    camera.targetY = pos.y.toFloat()
                }
            }
            CameraMode.FREE_CAMERA -> {
                // Camera position is controlled by panning/edge scrolling
            }
        }
    }
}
```

### Mouse Wheel Zoom

Optional camera zoom functionality:

```kotlin
class CameraZoomHandler(
    private val camera: Camera
) {
    private var zoomLevel = 1.0f
    private val minZoom = 0.5f  // Zoom out (see more)
    private val maxZoom = 2.0f  // Zoom in (see less)
    private val zoomStep = 0.1f
    
    fun handleWheelScroll(scrollAmount: Float) {
        val newZoom = zoomLevel + (scrollAmount * zoomStep)
        zoomLevel = newZoom.coerceIn(minZoom, maxZoom)
        
        // Adjust tile size based on zoom
        camera.effectiveTileSize = camera.baseTileSize * zoomLevel
    }
    
    fun resetZoom() {
        zoomLevel = 1.0f
        camera.effectiveTileSize = camera.baseTileSize
    }
}
```

### Entity Interaction

#### Right-Click on Entities

```kotlin
class EntityInteractionHandler(
    private val camera: Camera,
    private val entityManager: EntityManager
) {
    fun handleRightClick(screenX: Float, screenY: Float) {
        val worldPos = camera.screenToWorld(screenX, screenY)
        val entity = findEntityAtPosition(worldPos)
        
        when (entity) {
            is NPC -> {
                // Show NPC interaction menu
                showNPCInteractionMenu(entity)
            }
            is Monster -> {
                // Target monster for attack
                targetMonster(entity)
            }
            is Player -> {
                // Show player context menu
                showPlayerContextMenu(entity)
            }
            null -> {
                // No entity at position
            }
        }
    }
    
    private fun findEntityAtPosition(pos: Position): Entity? {
        // Check entities at or near the clicked position
        val entities = entityManager.getAllEntities()
        return entities.find { entity ->
            val distance = abs(entity.position.x - pos.x) + abs(entity.position.y - pos.y)
            distance <= 1 // Within 1 tile
        }
    }
}
```

### Mouse Cursor States

The cursor changes based on what the mouse is hovering over:

```kotlin
enum class CursorType {
    DEFAULT,        // Normal arrow
    MOVE,           // Hand/pointer (hovering over walkable ground)
    ATTACK,         // Sword/crosshair (hovering over attackable entity)
    INTERACT,       // Hand/interact icon (hovering over NPC)
    BLOCKED         // Blocked icon (hovering over non-walkable area)
}

class CursorHandler(
    private val camera: Camera,
    private val collisionMap: CollisionMap
) {
    fun updateCursor(mouseX: Float, mouseY: Float): CursorType {
        val worldPos = camera.screenToWorld(mouseX, mouseY)
        
        // Check if position is walkable
        if (!collisionMap.isWalkable(worldPos.x, worldPos.y)) {
            return CursorType.BLOCKED
        }
        
        // Check if hovering over entity
        val entity = findEntityAtPosition(worldPos)
        when (entity) {
            is Monster -> return CursorType.ATTACK
            is NPC -> return CursorType.INTERACT
            else -> return CursorType.MOVE
        }
    }
}
```

### Mouse Input Integration

Complete mouse input handling:

```kotlin
class MouseInputManager(
    private val camera: Camera,
    private val cameraController: CameraController,
    private val mouseInputHandler: MouseInputHandler,
    private val cameraPanHandler: CameraPanHandler,
    private val edgeScrollHandler: EdgeScrollHandler,
    private val zoomHandler: CameraZoomHandler,
    private val cursorHandler: CursorHandler
) {
    fun handleMouseDown(screenX: Float, screenY: Float, button: MouseButton) {
        when (button) {
            MouseButton.Left -> {
                if (cameraController.mode == CameraMode.FREE_CAMERA) {
                    cameraPanHandler.startPan(screenX, screenY)
                } else {
                    mouseInputHandler.handleMouseClick(screenX, screenY, button)
                }
            }
            MouseButton.Middle -> {
                cameraPanHandler.startPan(screenX, screenY)
            }
            MouseButton.Right -> {
                mouseInputHandler.handleMouseClick(screenX, screenY, button)
            }
        }
    }
    
    fun handleMouseMove(screenX: Float, screenY: Float) {
        // Update cursor
        val cursorType = cursorHandler.updateCursor(screenX, screenY)
        setCursor(cursorType)
        
        // Update camera panning
        cameraPanHandler.updatePan(screenX, screenY)
        
        // Edge scrolling (only in free camera mode)
        if (cameraController.mode == CameraMode.FREE_CAMERA) {
            edgeScrollHandler.update(screenX, screenY)
        }
    }
    
    fun handleMouseUp(screenX: Float, screenY: Float, button: MouseButton) {
        when (button) {
            MouseButton.Middle -> {
                cameraPanHandler.endPan()
            }
            MouseButton.Left -> {
                if (cameraPanHandler.isPanning) {
                    cameraPanHandler.endPan()
                }
            }
        }
    }
    
    fun handleWheelScroll(scrollAmount: Float) {
        zoomHandler.handleWheelScroll(scrollAmount)
    }
}
```

### Configuration Options

```kotlin
data class MouseSettings(
    val enableClickToMove: Boolean = true,
    val enableCameraPanning: Boolean = true,
    val enableEdgeScrolling: Boolean = true,
    val enableZoom: Boolean = false,  // Optional feature
    val edgeScrollZone: Float = 50f,
    val edgeScrollSpeed: Float = 5f,
    val doubleClickThreshold: Long = 300L,  // milliseconds
    val pathfindingEnabled: Boolean = true
)
```

### Prototype Requirements

**Minimum Implementation:**
- [ ] Left-click to move player (simple direct movement, no pathfinding)
- [ ] Middle-click drag for camera panning
- [ ] Camera bounds clamping

**Optional Features:**
- [ ] Click-to-move with pathfinding
- [ ] Edge scrolling
- [ ] Mouse wheel zoom
- [ ] Right-click entity interaction
- [ ] Cursor state changes
- [ ] Double-click to run

---

## Rendering Pipeline

### Rendering Order (Z-Order)

Entities must be rendered in the correct order for proper depth:

```
1. Ground tiles (bottom layer)
2. Entities sorted by Y position (lower Y = rendered first)
   - NPCs
   - Monsters
   - Players (other players first, then own player)
3. UI overlay (always on top)
```

### Rendering Steps

```kotlin
fun renderFrame(
    canvas: Canvas,
    camera: Camera,
    mapData: MapData,
    entities: List<Entity>
) {
    // 1. Calculate visible tile range
    val startX = (camera.cameraX / camera.tileSize).toInt() - 1
    val endX = ((camera.cameraX + camera.viewportWidth) / camera.tileSize).toInt() + 1
    val startY = (camera.cameraY / camera.tileSize).toInt() - 1
    val endY = ((camera.cameraY + camera.viewportHeight) / camera.tileSize).toInt() + 1
    
    // 2. Render ground tiles
    for (x in startX..endX) {
        for (y in startY..endY) {
            if (x >= 0 && x < mapData.width && y >= 0 && y < mapData.height) {
                renderTile(canvas, camera, x, y, mapData.getTile(x, y))
            }
        }
    }
    
    // 3. Sort entities by Y position (depth sorting)
    val sortedEntities = entities.sortedBy { it.position.y }
    
    // 4. Render entities
    sortedEntities.forEach { entity ->
        if (isInViewport(entity.position.x, entity.position.y, camera)) {
            renderEntity(canvas, camera, entity)
        }
    }
    
    // 5. Render UI overlay
    renderUI(canvas)
}
```

### Entity Rendering

```kotlin
fun renderEntity(canvas: Canvas, camera: Camera, entity: Entity) {
    val screenPos = camera.worldToScreen(
        entity.position.x.toFloat(),
        entity.position.y.toFloat()
    )
    
    // Draw sprite at screen position
    // For prototype: Use colored circles or simple shapes
    // Future: Load sprite sheets and animate
    
    when (entity) {
        is Player -> {
            // Yellow circle for own player, green for others
            canvas.drawCircle(
                color = if (entity.isOwnPlayer) Color.Yellow else Color.Green,
                radius = camera.tileSize / 2,
                center = Offset(screenPos.x + camera.tileSize / 2, screenPos.y + camera.tileSize / 2)
            )
        }
        is Monster -> {
            // Red circle for monsters
            canvas.drawCircle(
                color = Color.Red,
                radius = camera.tileSize / 2 - 2,
                center = Offset(screenPos.x + camera.tileSize / 2, screenPos.y + camera.tileSize / 2)
            )
        }
        is NPC -> {
            // Blue circle for NPCs
            canvas.drawCircle(
                color = Color.Blue,
                radius = camera.tileSize / 2 - 2,
                center = Offset(screenPos.x + camera.tileSize / 2, screenPos.y + camera.tileSize / 2)
            )
        }
    }
}
```

### Tile Rendering

```kotlin
fun renderTile(canvas: Canvas, camera: Camera, x: Int, y: Int, tile: Tile) {
    val screenPos = camera.worldToScreen(x.toFloat(), y.toFloat())
    
    // Draw tile background
    canvas.drawRect(
        color = tile.groundColor,
        topLeft = screenPos,
        size = Size(camera.tileSize, camera.tileSize)
    )
    
    // Draw grid lines (optional, for debugging)
    if (showGrid) {
        canvas.drawLine(
            color = Color.Gray,
            start = screenPos,
            end = Offset(screenPos.x + camera.tileSize, screenPos.y),
            strokeWidth = 1f
        )
        canvas.drawLine(
            color = Color.Gray,
            start = screenPos,
            end = Offset(screenPos.x, screenPos.y + camera.tileSize),
            strokeWidth = 1f
        )
    }
}
```

---

## Entity System

### Entity Base Class

```kotlin
abstract class Entity(
    val id: String,
    var position: Position,      // World position (tile coordinates)
    var direction: String = "down"  // "up", "down", "left", "right"
)

data class Position(
    val x: Int,
    val y: Int
)
```

### Entity Types

#### Player

```kotlin
data class Player(
    override val id: String,
    val characterId: String,
    val name: String,
    override var position: Position,
    override var direction: String = "down",
    val isOwnPlayer: Boolean = false  // True for local player
) : Entity(id, position, direction)
```

#### Monster

```kotlin
data class Monster(
    override val id: String,
    val type: String,           // "poring", "lunatic", etc.
    override var position: Position,
    override var direction: String = "down",
    var health: Int = 100,
    var maxHealth: Int = 100
) : Entity(id, position, direction)
```

#### NPC

```kotlin
data class NPC(
    override val id: String,
    val name: String,
    override var position: Position,
    override var direction: String = "down",
    val type: String            // "merchant", "quest", etc.
) : Entity(id, position, direction)
```

### Entity Management

```kotlin
class EntityManager {
    private val players = mutableMapOf<String, Player>()
    private val monsters = mutableMapOf<String, Monster>()
    private val npcs = mutableMapOf<String, NPC>()
    
    fun getAllEntities(): List<Entity> {
        return (players.values + monsters.values + npcs.values)
            .sortedBy { it.position.y }  // Depth sort
    }
    
    fun updatePlayer(id: String, position: Position, direction: String) {
        players[id]?.let {
            it.position = position
            it.direction = direction
        }
    }
    
    fun addPlayer(player: Player) {
        players[player.id] = player
    }
    
    fun removePlayer(id: String) {
        players.remove(id)
    }
    
    // Similar methods for monsters and NPCs
}
```

---

## Movement System

### Movement Model

**Tile-Based Discrete Movement:**
- Player moves one tile at a time
- Movement is instant (no interpolation for prototype)
- Server validates each movement

### Movement Directions

```kotlin
enum class Direction(val value: String) {
    UP("up"),
    DOWN("down"),
    LEFT("left"),
    RIGHT("right");
    
    fun getOffset(): Position {
        return when (this) {
            UP -> Position(0, -1)
            DOWN -> Position(0, 1)
            LEFT -> Position(-1, 0)
            RIGHT -> Position(1, 0)
        }
    }
}
```

### Client-Side Movement

```kotlin
fun movePlayer(direction: String) {
    val currentPosition = playerPosition ?: return
    val offset = Direction.valueOf(direction.uppercase()).getOffset()
    val newPosition = Position(
        currentPosition.x + offset.x,
        currentPosition.y + offset.y
    )
    
    // Optimistic update: update immediately
    playerPosition = newPosition
    playerDirection = direction
    
    // Send to server for validation
    sendMovementToServer(direction, newPosition)
}
```

### Server-Side Validation

```kotlin
fun validateMovement(
    player: Player,
    newPosition: Position,
    mapData: MapData
): Boolean {
    // Check bounds
    if (newPosition.x < 0 || newPosition.x >= mapData.width ||
        newPosition.y < 0 || newPosition.y >= mapData.height) {
        return false
    }
    
    // Check collision
    if (!mapData.collisionMap.isWalkable(newPosition.x, newPosition.y)) {
        return false
    }
    
    // Check entity collision (optional for prototype)
    // ...
    
    return true
}
```

### Movement Rate Limiting

- **Client**: Limits continuous movement to 150ms intervals
- **Server**: Validates movement rate (prevents speed hacking)
- **Maximum Speed**: ~6.67 tiles per second (150ms per tile)

### Future Enhancements (Not Required for Prototype)

- **Smooth Interpolation**: Animate movement between tiles
- **Pathfinding**: Click-to-move with A* pathfinding
- **Running**: Hold Shift for faster movement
- **Diagonal Movement**: Support 8-directional movement

---

## Collision Detection

### Collision Map

```kotlin
class CollisionMap(
    val width: Int,
    val height: Int,
    private val collisionData: BooleanArray  // true = walkable, false = blocked
) {
    fun isWalkable(x: Int, y: Int): Boolean {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false
        }
        val index = y * width + x
        return collisionData[index]
    }
    
    fun setWalkable(x: Int, y: Int, walkable: Boolean) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            val index = y * width + x
            collisionData[index] = walkable
        }
    }
}
```

### Collision Types

1. **Map Collision**: Static obstacles (walls, trees, water)
2. **Entity Collision**: Other players, monsters, NPCs (optional for prototype)
3. **Boundary Collision**: Map edges

### Collision Detection Algorithm

```kotlin
fun checkCollision(
    position: Position,
    mapData: MapData,
    entityManager: EntityManager
): CollisionResult {
    // Check map bounds
    if (position.x < 0 || position.x >= mapData.width ||
        position.y < 0 || position.y >= mapData.height) {
        return CollisionResult.Blocked("Out of bounds")
    }
    
    // Check map collision
    if (!mapData.collisionMap.isWalkable(position.x, position.y)) {
        return CollisionResult.Blocked("Map collision")
    }
    
    // Check entity collision (optional)
    val entitiesAtPosition = entityManager.getEntitiesAt(position)
    if (entitiesAtPosition.isNotEmpty()) {
        return CollisionResult.Blocked("Entity collision")
    }
    
    return CollisionResult.Walkable
}
```

### Collision Map Format

For prototype, use a simple bitmap:
- **0** = Walkable
- **1** = Blocked

Future: Support multiple collision layers (ground, objects, etc.)

---

## Network Synchronization

### Synchronization Model

**Server-Authoritative with Optimistic Updates:**

1. Client sends movement request
2. Client immediately updates local state (optimistic)
3. Server validates movement
4. Server broadcasts update to all clients
5. Client syncs authoritative position from server

### Movement Messages

#### Client → Server

```kotlin
@Serializable
data class MoveMessage(
    val type: String = "move",
    val direction: String,      // "up", "down", "left", "right"
    val position: Position      // New position after movement
)
```

#### Server → Client

```kotlin
@Serializable
data class MovementUpdate(
    val type: String = "movement",
    val characterId: String,
    val position: Position,
    val direction: String
)
```

### State Synchronization

**Full State Sync** (on join):
```kotlin
@Serializable
data class ZoneState(
    val type: String = "zone_state",
    val players: List<PlayerDTO>,
    val npcs: List<NPCDTO>,
    val monsters: List<MonsterDTO>
)
```

**Delta Updates** (during gameplay):
- Player movement updates
- Player join/leave events
- Monster spawn/despawn events

### Network Protocol

- **Transport**: WebSocket (real-time bidirectional)
- **Format**: JSON (kotlinx.serialization)
- **Update Rate**: As needed (movement events, not fixed tick rate)

### Client Prediction & Reconciliation

For prototype:
- Simple optimistic updates
- Server correction on invalid movement

Future:
- Client-side prediction
- Server reconciliation
- Lag compensation

---

## Minimal Prototype Requirements

### Core Features (Must Have)

1. **World Rendering**
   - [x] Tile-based map rendering
   - [x] Camera follows player
   - [x] Viewport culling

2. **Player Movement**
   - [x] WASD/Arrow key controls
   - [x] Tile-based movement
   - [x] Continuous movement while key held
   - [x] Server validation
   - [ ] Left-click to move (basic implementation)

3. **Mouse & Camera Controls**
   - [ ] Left-click to move player
   - [ ] Middle-click drag for camera panning
   - [ ] Camera bounds clamping
   - [ ] Screen-to-world coordinate conversion

4. **Entity Rendering**
   - [x] Own player (distinct visual)
   - [x] Other players
   - [x] NPCs
   - [x] Monsters
   - [x] Depth sorting (Y-order)

5. **Camera System**
   - [x] Follow camera
   - [x] World-to-screen conversion
   - [x] Bounds clamping
   - [ ] Free camera mode (panning)

6. **Network**
   - [x] Movement synchronization
   - [x] Player join/leave
   - [x] Zone state sync

7. **Collision**
   - [x] Map bounds checking
   - [x] Basic collision map (walkable/non-walkable)

### Visual Requirements (Prototype)

- **Simple Shapes**: Colored circles for entities (sufficient for prototype)
- **Grid Tiles**: Colored rectangles for ground tiles
- **Grid Lines**: Optional, for debugging
- **UI Overlay**: Character name, position, FPS, ping

### Future Enhancements (Not Required)

- Sprite sheets and animations
- Smooth movement interpolation
- Click-to-move pathfinding
- Combat system
- Chat system
- Inventory system
- Skill system

---

## Technical Implementation Details

### Coordinate Systems

**World Coordinates (Tile Space):**
- Integer coordinates (0, 1, 2, ...)
- One unit = one tile
- Used for game logic, collision, network

**Screen Coordinates (Pixel Space):**
- Float coordinates (0.0, 32.0, 64.0, ...)
- One unit = one pixel
- Used for rendering

**Conversion:**
```kotlin
// World to Screen
screenX = worldX * tileSize - cameraX
screenY = worldY * tileSize - cameraY

// Screen to World
worldX = (screenX + cameraX) / tileSize
worldY = (screenY + cameraY) / tileSize
```

### Frame Rate & Performance

- **Target FPS**: 60 FPS
- **Movement Rate**: 150ms per tile (~6.67 tiles/sec)
- **Rendering**: Only render visible tiles and entities
- **Network**: Event-based (not fixed tick rate)

### Data Structures

```kotlin
// Game State
data class GameState(
    val playerPosition: Position?,
    val playerDirection: String,
    val players: Map<String, PlayerDTO>,
    val npcs: Map<String, NPCDTO>,
    val monsters: Map<String, MonsterDTO>,
    val fps: Int,
    val ping: Long
)

// Map Data
data class MapData(
    val name: String,
    val width: Int,
    val height: Int,
    val tileSize: Int = 32,
    val collisionMap: CollisionMap,
    val spawnPoints: List<Position>
)
```

### Thread Safety

- **Client**: Single-threaded UI (Compose main thread)
- **Server**: Coroutines with Mutex for shared state
- **Network**: Async coroutines, StateFlow for reactive updates

### Error Handling

- **Invalid Movement**: Server rejects, client reverts optimistic update
- **Network Errors**: Log and attempt reconnection
- **Deserialization Errors**: Log and skip malformed messages

---

## Implementation Checklist

### Phase 1: Basic Rendering
- [x] Tile-based map rendering
- [x] Camera system
- [x] Entity rendering (simple shapes)
- [x] Depth sorting

### Phase 2: Movement
- [x] Keyboard input handling
- [x] Client-side movement
- [x] Server validation
- [x] Network synchronization

### Phase 3: Entities
- [x] Player entities
- [x] NPC entities
- [x] Monster entities
- [x] Entity management

### Phase 4: Collision
- [x] Map bounds checking
- [x] Collision map implementation
- [ ] Entity collision (optional)

### Phase 5: Polish
- [x] FPS display
- [x] Position display
- [x] UI overlay
- [ ] Smooth camera following (optional)

---

## Appendix

### Default Values

- **Tile Size**: 32 pixels
- **Movement Rate**: 150ms per tile
- **Map Size**: 1000x1000 tiles (configurable)
- **Viewport**: Full screen (client window size)

### Coordinate Examples

```
World Position: (10, 15)
Tile Size: 32
Camera Position: (0, 0)
Screen Position: (10 * 32 - 0, 15 * 32 - 0) = (320, 480)

If camera follows player at (10, 15) and viewport is 800x600:
Camera X: 10 * 32 - 800/2 = 320 - 400 = -80 (clamped to 0)
Camera Y: 15 * 32 - 600/2 = 480 - 300 = 180
```

### Performance Targets

- **Rendering**: 60 FPS
- **Network Latency**: < 100ms (ping)
- **Movement Response**: < 50ms (client-side)
- **Server Validation**: < 10ms

---

**Document Version:** 1.0  
**Last Updated:** 2024  
**Maintainer:** Development Team

