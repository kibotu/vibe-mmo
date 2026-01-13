package net.kibotu.ro.client.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger

/**
 * Integration helper for GameScreen to use the new systems from GAME_SPEC.md
 * This file demonstrates how to integrate:
 * - IsometricCamera
 * - Pathfinding
 * - BillboardSprite
 * - TerrainSystem
 */
class GameScreenIntegrationHelper {
    
    /**
     * Setup isometric camera for GameScreen
     */
    fun setupIsometricCamera(
        mapWidth: Int,
        mapHeight: Int,
        initialPosition: Position
    ): IsometricCamera {
        val perspectiveCamera = PerspectiveCamera(50f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        val isometricCamera = IsometricCamera(perspectiveCamera, mapWidth, mapHeight)
        
        // Initialize camera at player position
        isometricCamera.update(initialPosition.x.toFloat(), initialPosition.y.toFloat())
        
        return isometricCamera
    }
    
    /**
     * Setup pathfinding system
     */
    fun setupPathfinding(
        collisionMap: CollisionMap,
        mapWidth: Int,
        mapHeight: Int
    ): Pair<Pathfinding, PathFollower> {
        val pathfinding = Pathfinding(collisionMap, mapWidth, mapHeight)
        val pathFollower = PathFollower(pathfinding)
        return Pair(pathfinding, pathFollower)
    }
    
    /**
     * Setup terrain system for Payon map
     */
    fun setupPayonTerrain(): TerrainData {
        val generator = TerrainGenerator()
        return generator.generatePayonTerrain()
    }
    
    /**
     * Generate flat terrain matching map dimensions (for visual purposes)
     * Server provides collision data but not heightmap, so we generate flat terrain
     */
    fun generateFlatTerrain(width: Int, height: Int): TerrainData {
        val heightMap = FloatArray(width * height) { 0f } // Flat terrain at height 0
        return TerrainData(width, height, heightMap)
    }
    
    /**
     * Setup navigation grid from terrain
     */
    fun setupNavigationGrid(terrainData: TerrainData): CollisionMap {
        val generator = TerrainGenerator()
        return generator.generateNavigationGrid(terrainData)
    }
}

/**
 * Camera input handler for isometric camera controls according to GAME_SPEC.md section 1.3
 * Handles:
 * - Right mouse button hold + drag left/right: Horizontal rotation (120-180°/second)
 * - Hold Shift + mouse wheel up/down: Vertical tilt (5° increment, 40-65° range)
 * - Mouse wheel up/down: Zoom (2 units per tick, 8-25 units range)
 * - Double right-click: Reset camera to face north
 * - Double right-click + Shift: Reset elevation to default
 * - Hold Ctrl + double right-click: Reset zoom to default
 */
class IsometricCameraInputHandler(
    private val isometricCamera: IsometricCamera,
    private val onMovePlayer: (String) -> Unit
) {
    private var lastMouseX: Int = 0
    private var lastMouseY: Int = 0
    private var isRotating: Boolean = false
    private var rotationButton: Int = -1
    
    // Double-click detection
    private var lastRightClickTime: Long = 0
    private val doubleClickThreshold: Long = 300 // milliseconds
    
    /**
     * Handle mouse button down
     */
    fun handleMouseDown(screenX: Int, screenY: Int, button: Int, deltaTime: Float): Boolean {
        when (button) {
            Input.Buttons.RIGHT -> {
                val currentTime = System.currentTimeMillis()
                val isDoubleClick = (currentTime - lastRightClickTime) < doubleClickThreshold
                lastRightClickTime = currentTime
                
                // Check for double-click resets
                val shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                val ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
                
                if (isDoubleClick) {
                    when {
                        ctrlPressed -> {
                            // Hold Ctrl + double right-click: Reset zoom
                            isometricCamera.resetZoom()
                            ClientLogger.info("Camera zoom reset to default", "IsometricCameraInputHandler")
                            return true
                        }
                        shiftPressed -> {
                            // Double right-click + Shift: Reset elevation
                            isometricCamera.resetElevation()
                            ClientLogger.info("Camera elevation reset to default", "IsometricCameraInputHandler")
                            return true
                        }
                        else -> {
                            // Double right-click: Reset camera to face north
                            isometricCamera.resetToNorth()
                            ClientLogger.info("Camera reset to face north", "IsometricCameraInputHandler")
                            return true
                        }
                    }
                } else {
                    // Start rotation drag
                    isRotating = true
                    rotationButton = button
                    lastMouseX = screenX
                    lastMouseY = screenY
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * Handle mouse button up
     */
    fun handleMouseUp(screenX: Int, screenY: Int, button: Int): Boolean {
        if (button == rotationButton) {
            isRotating = false
            rotationButton = -1
            return true
        }
        return false
    }
    
    /**
     * Handle mouse dragged
     * According to GAME_SPEC.md: Right mouse button hold + drag left/right to rotate camera
     */
    fun handleMouseDragged(screenX: Int, screenY: Int, deltaTime: Float): Boolean {
        if (isRotating && rotationButton == Input.Buttons.RIGHT) {
            val deltaX = screenX - lastMouseX
            
            // Horizontal rotation only (orbit around player)
            // Rotation speed: 120-180°/second while dragging
            isometricCamera.rotateHorizontalByPixels(deltaX.toFloat(), deltaTime)
            
            lastMouseX = screenX
            lastMouseY = screenY
            return true
        }
        return false
    }
    
    /**
     * Handle mouse scrolled
     * According to GAME_SPEC.md section 1.3:
     * - Mouse wheel up/down: Zoom
     * - Hold Shift + mouse wheel up/down: Vertical tilt (elevation)
     */
    fun handleScrolled(amountX: Float, amountY: Float): Boolean {
        val shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
        
        if (shiftPressed) {
            // Hold Shift + mouse wheel: Adjust elevation (vertical tilt)
            // amountY > 0 means scroll up (increase elevation), < 0 means scroll down (decrease elevation)
            isometricCamera.adjustElevation(amountY)
            ClientLogger.debug("Camera elevation adjusted: ${isometricCamera.getVerticalAngle()}°", "IsometricCameraInputHandler")
        } else {
            // Mouse wheel: Zoom
            // amountY > 0 means scroll up (zoom in), < 0 means scroll down (zoom out)
            isometricCamera.zoom(-amountY) // Negative because zoom() expects positive to zoom out
            ClientLogger.debug("Camera zoom: ${isometricCamera.getDistance()} units", "IsometricCameraInputHandler")
        }
        return true
    }
    
    /**
     * Handle keyboard input for WASD movement
     * According to GAME_SPEC.md section 2.3: Movement relative to camera orientation
     */
    fun handleKeyDown(keycode: Int): Boolean {
        // Get camera horizontal angle to calculate relative movement direction
        val cameraAngle = isometricCamera.getHorizontalAngle()
        
        when (keycode) {
            Input.Keys.W, Input.Keys.UP -> {
                // W/UP: Move forward relative to camera
                // Camera angle 0° = facing north, so forward is north
                val direction = calculateDirectionRelativeToCamera(cameraAngle, "forward")
                onMovePlayer(direction)
                return true
            }
            Input.Keys.S, Input.Keys.DOWN -> {
                // S/DOWN: Move backward relative to camera
                val direction = calculateDirectionRelativeToCamera(cameraAngle, "backward")
                onMovePlayer(direction)
                return true
            }
            Input.Keys.A, Input.Keys.LEFT -> {
                // A/LEFT: Move left relative to camera
                val direction = calculateDirectionRelativeToCamera(cameraAngle, "left")
                onMovePlayer(direction)
                return true
            }
            Input.Keys.D, Input.Keys.RIGHT -> {
                // D/RIGHT: Move right relative to camera
                val direction = calculateDirectionRelativeToCamera(cameraAngle, "right")
                onMovePlayer(direction)
                return true
            }
        }
        return false
    }
    
    /**
     * Calculate movement direction relative to camera orientation
     * Camera angle: 0° = north, 90° = east, 180° = south, 270° = west
     */
    private fun calculateDirectionRelativeToCamera(cameraAngle: Float, relativeDirection: String): String {
        // Convert camera angle to direction index (0=north, 1=east, 2=south, 3=west)
        val cameraDir = ((cameraAngle / 90f).toInt() % 4)
        
        // Map relative direction to absolute direction based on camera orientation
        return when (relativeDirection) {
            "forward" -> {
                // Forward relative to camera
                when (cameraDir) {
                    0 -> "up"    // Camera facing north, forward is north
                    1 -> "right"  // Camera facing east, forward is east
                    2 -> "down"   // Camera facing south, forward is south
                    3 -> "left"   // Camera facing west, forward is west
                    else -> "up"
                }
            }
            "backward" -> {
                // Backward relative to camera
                when (cameraDir) {
                    0 -> "down"  // Camera facing north, backward is south
                    1 -> "left"  // Camera facing east, backward is west
                    2 -> "up"    // Camera facing south, backward is north
                    3 -> "right" // Camera facing west, backward is east
                    else -> "down"
                }
            }
            "left" -> {
                // Left relative to camera
                when (cameraDir) {
                    0 -> "left"  // Camera facing north, left is west
                    1 -> "up"    // Camera facing east, left is north
                    2 -> "right" // Camera facing south, left is east
                    3 -> "down"  // Camera facing west, left is south
                    else -> "left"
                }
            }
            "right" -> {
                // Right relative to camera
                when (cameraDir) {
                    0 -> "right" // Camera facing north, right is east
                    1 -> "down"  // Camera facing east, right is south
                    2 -> "left"  // Camera facing south, right is west
                    3 -> "up"    // Camera facing west, right is north
                    else -> "right"
                }
            }
            else -> "up"
        }
    }
}

/**
 * Click-to-move handler with pathfinding
 */
class ClickToMoveHandler(
    private val pathfinding: Pathfinding,
    private val pathFollower: PathFollower,
    private val onPathFound: (List<Position>) -> Unit,
    private val onMoveToWaypoint: (Position) -> Unit
) {
    /**
     * Handle click-to-move
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param camera Camera for screen-to-world conversion (OrthographicCamera for top-down view)
     * @param currentPosition Current player position
     */
    fun handleClick(
        screenX: Int,
        screenY: Int,
        camera: com.badlogic.gdx.graphics.Camera,
        currentPosition: Position
    ): Boolean {
        // Convert screen coordinates to world coordinates
        // For orthographic top-down camera, unproject to get world X and Z coordinates
        val worldPos = Vector3()
        camera.unproject(worldPos.set(screenX.toFloat(), screenY.toFloat(), 0f))
        
        // For top-down camera: X is horizontal, Z is vertical (depth)
        // Convert to grid position (divide by tile size)
        val tileSize = 32f // TODO: Get from map data
        val targetPosition = Position(
            (worldPos.x / tileSize).toInt(),
            (worldPos.z / tileSize).toInt()
        )
        
        // Find path using A*
        val path = pathfinding.findPath(currentPosition, targetPosition)
        
        if (path.isNotEmpty()) {
            ClientLogger.info("Path found: ${path.size} waypoints", "ClickToMove")
            pathFollower.setPath(path)
            onPathFound(path)
            
            // Start moving to first waypoint
            val nextWaypoint = pathFollower.getNextWaypoint(currentPosition)
            nextWaypoint?.let { onMoveToWaypoint(it) }
            
            return true
        } else {
            ClientLogger.warn("No path found to target position: $targetPosition", "ClickToMove")
            return false
        }
    }
    
    /**
     * Update path following (call every frame)
     * According to GAME_SPEC.md section 2.2: Movement speed 150 units/second
     */
    fun update(currentPosition: Position, deltaTime: Float, onMoveToWaypoint: (Position) -> Unit) {
        if (pathFollower.isFollowingPath()) {
            val nextWaypoint = pathFollower.getNextWaypoint(currentPosition)
            if (nextWaypoint != null) {
                // Calculate distance to waypoint
                val deltaX = nextWaypoint.x - currentPosition.x
                val deltaY = nextWaypoint.y - currentPosition.y
                val distance = kotlin.math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                
                // Movement speed: 150 units/second (from GAME_SPEC.md section 2.1)
                val movementSpeed = 150f
                val moveDistance = movementSpeed * deltaTime
                
                if (distance <= moveDistance) {
                    // Reached waypoint, move to it
                    onMoveToWaypoint(nextWaypoint)
                } else {
                    // Move toward waypoint
                    // Calculate direction and move one step
                    val direction = when {
                        kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) -> {
                            if (deltaY < 0) "up" else "down"
                        }
                        else -> {
                            if (deltaX < 0) "left" else "right"
                        }
                    }
                    
                    // Move one grid cell in the direction of the waypoint
                    val newPosition = when (direction) {
                        "up" -> Position(currentPosition.x, currentPosition.y - 1)
                        "down" -> Position(currentPosition.x, currentPosition.y + 1)
                        "left" -> Position(currentPosition.x - 1, currentPosition.y)
                        "right" -> Position(currentPosition.x + 1, currentPosition.y)
                        else -> currentPosition
                    }
                    
                    onMoveToWaypoint(newPosition)
                }
            }
        }
    }
}

