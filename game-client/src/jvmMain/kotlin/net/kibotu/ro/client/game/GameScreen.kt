package net.kibotu.ro.client.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import kotlinx.coroutines.*
import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger
import net.kibotu.ro.client.viewmodel.GameViewModel
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * New GameScreen with Diablo-style gameplay:
 * - Noise-based height map terrain
 * - Perspective camera with isometric lock on player
 * - Billboard player character
 * - Click-to-move controls
 * - Terrain collision detection
 */
class GameScreen(
    private val viewModel: GameViewModel,
    private val ownCharacterId: String,
    private val initialPosition: Position,
    private val initialMapData: MapData
) : Screen {
    
    // Rendering
    private var shapeRenderer: ShapeRenderer? = null
    private var spriteBatch: SpriteBatch? = null
    private var modelBatch: ModelBatch? = null
    private var environment: Environment? = null
    private var playerPointLight: PointLight? = null
    
    // Terrain
    private var terrainData: TerrainData? = null
    private var terrainRenderer: TerrainMeshRenderer? = null
    private var terrainInstance: com.badlogic.gdx.graphics.g3d.ModelInstance? = null
    
    // Camera
    private var diabloCamera: DiabloCamera? = null
    
    // Player
    private var playerWorldPosition = Vector3(0f, 0f, 0f)
    private var playerTargetPosition: Vector3? = null
    private var playerPath: List<Vector3> = emptyList()
    private var pathIndex = 0
    private val playerSpeed = 8f // units per second (increased from 5f)
    private var playerFacingDirection = Vector3(0f, 0f, -1f) // Default facing direction (north)
    
    // Player billboard
    private var playerSprite: TextureRegion? = null
    private var playerBillboard: BillboardPlayerRenderer? = null
    private var playerBillboardInstance: com.badlogic.gdx.graphics.g3d.ModelInstance? = null
    
    // Click-to-move
    private var clickToMove: ClickToMove? = null
    
    // Assets
    private var noviceAtlas: TextureAtlas? = null
    
    // Input
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pressedKeys = mutableSetOf<Int>() // Track currently pressed keys
    private var rightMousePressed = false // Track right mouse button state
    private var middleMousePressed = false // Track middle mouse button state
    private var lastMouseX = 0 // Last mouse X position for drag calculation
    private var lastMouseY = 0 // Last mouse Y position for drag calculation
    
    // Double-click detection for right mouse button
    private var lastRightClickTime: Long = 0
    private val doubleClickThreshold: Long = 300 // milliseconds
    
    // Click indicators for debugging
    data class ClickIndicator(val position: Vector3, val spawnTime: Float)
    private val clickIndicators = mutableListOf<ClickIndicator>()
    
    // Terrain dimensions (will be set from map data)
    private val terrainWidth: Int
    private val terrainHeight: Int
    
    init {
        // Use map dimensions for terrain
        terrainWidth = initialMapData.width
        terrainHeight = initialMapData.height
    }
    
    override fun show() {
        ClientLogger.info("GameScreen.show() - Initializing new Diablo-style game", "GameScreen")
        
        // Initialize renderers
        shapeRenderer = ShapeRenderer()
        spriteBatch = SpriteBatch()
        modelBatch = ModelBatch()
        environment = Environment().apply {
            // Reduce ambient light significantly so point light is visible
            set(ColorAttribute.createAmbientLight(0.2f, 0.2f, 0.25f, 1f))
            // Directional light using ColorAttribute (reduced)
            val lightDirection = com.badlogic.gdx.math.Vector3(-0.5f, -1f, -0.5f).nor()
            set(ColorAttribute.createDiffuse(0.2f, 0.2f, 0.2f, 1f))
            // Add point light that will follow player facing direction
            // Initial position will be updated in render()
            playerPointLight = PointLight().apply {
                color.set(1f, 1f, 0.9f, 1f) // Warm white/yellow light
                intensity = 10f // Increased intensity for visibility
                position.set(0f, 0f, 0f) // Position (will be updated)
            }
            add(playerPointLight!!)
        }
        
        // Load player sprite
        try {
            noviceAtlas = TextureAtlas(Gdx.files.internal("assets/novice.atlas"))
            playerSprite = noviceAtlas?.findRegion("sprite_0")
            playerSprite?.let { sprite ->
                ClientLogger.info("Loaded player sprite: sprite_0, size: ${sprite.regionWidth}x${sprite.regionHeight}, texture: ${sprite.texture?.width}x${sprite.texture?.height}", "GameScreen")
            } ?: run {
                ClientLogger.error("Failed to find sprite_0 in atlas", "GameScreen")
            }
        } catch (e: Exception) {
            ClientLogger.error("Failed to load sprite atlas: ${e.message}", "GameScreen")
            e.printStackTrace()
        }
        
        // Use server-provided heightmap or generate fallback terrain
        val heightMap: FloatArray = if (initialMapData.heightMap != null) {
            ClientLogger.info("Using server-provided heightmap (${terrainWidth}x${terrainHeight})", "GameScreen")
            initialMapData.heightMap
        } else {
            ClientLogger.info("No server heightmap, generating noise-based terrain...", "GameScreen")
            generateNoiseHeightMap(terrainWidth, terrainHeight, maxHeight = initialMapData.maxHeight, seed = 12345L)
        }
        terrainData = TerrainData(terrainWidth, terrainHeight, heightMap)
        
        // Create terrain renderer
        terrainRenderer = TerrainMeshRenderer()
        terrainInstance = terrainRenderer?.buildTerrainMesh(terrainData!!)
        
        // If mesh building failed, we'll use simple rendering
        if (terrainInstance == null) {
            ClientLogger.warn("Terrain mesh building failed, will use simple rendering", "GameScreen")
        }
        
        // Initialize player position first
        val startX = initialPosition.x.toFloat().coerceIn(10f, terrainWidth.toFloat() - 10f) // Keep away from edges
        val startZ = initialPosition.y.toFloat().coerceIn(10f, terrainHeight.toFloat() - 10f)
        val startHeight = terrainData!!.getHeightAt(startX, startZ)
        playerWorldPosition.set(startX, startHeight + 1f, startZ) // Player 1 unit above terrain
        
        // Initialize server position tracking
        serverPosition = initialPosition
        targetServerPosition = initialPosition
        
        // Initialize camera (needs player position)
        diabloCamera = DiabloCamera(
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat(),
            terrainData!!
        )
        // Initialize camera with player position
        diabloCamera!!.update(playerWorldPosition.x, playerWorldPosition.z, 0f)
        
        // Initialize click-to-move
        clickToMove = ClickToMove(terrainData!!)
        
        // Create player billboard
        playerBillboard = BillboardPlayerRenderer()
        playerBillboardInstance = playerBillboard?.createBillboardModel(playerSprite)
        
        ClientLogger.info("GameScreen initialized: terrain=${terrainWidth}x${terrainHeight}, player at ($startX, $startHeight, $startZ)", "GameScreen")
        ClientLogger.info("Terrain instance: ${terrainInstance != null}, Player billboard: ${playerBillboardInstance != null}", "GameScreen")
    }
    
    override fun render(delta: Float) {
        val renderer = shapeRenderer ?: return
        val batch = modelBatch ?: return
        val camera = diabloCamera ?: return
        val terrain = terrainData ?: return
        
        // Clear screen
        Gdx.gl.glClearColor(0.5f, 0.7f, 1f, 1f) // Sky blue
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        
        // Enable depth testing for 3D rendering
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        
        // Update click indicators (remove old ones)
        val currentTime = System.currentTimeMillis() / 1000f
        clickIndicators.removeAll { currentTime - it.spawnTime > 1f }
        
        // Update player movement (keyboard or path)
        updatePlayerMovement(delta, terrain)
        
        // Update point light position based on player facing direction
        updatePlayerLight()
        
        // Update camera to follow player
        camera.update(playerWorldPosition.x, playerWorldPosition.z, delta)
        
        // Update player billboard to face camera
        playerBillboardInstance?.let { instance ->
            val cameraPos = camera.getCamera().position
            val cameraUp = camera.getCamera().up
            val cameraDir = camera.getCamera().direction
            
            // Debug: Log positions periodically
            if ((System.currentTimeMillis() / 1000) % 2 == 0L) {
                ClientLogger.debug("Billboard: pos=(${playerWorldPosition.x}, ${playerWorldPosition.y}, ${playerWorldPosition.z}), camera=(${cameraPos.x}, ${cameraPos.y}, ${cameraPos.z}), dir=(${cameraDir.x}, ${cameraDir.y}, ${cameraDir.z})", "GameScreen")
            }
            
            playerBillboard?.updateBillboard(instance, playerWorldPosition, cameraPos, cameraUp)
        }
        
        // Always use ShapeRenderer for now to ensure visibility
        // Set up ShapeRenderer for 3D rendering
        renderer.projectionMatrix = camera.getCamera().combined
        renderer.setAutoShapeType(true)
        
        // Render terrain using ShapeRenderer (more reliable)
        terrainRenderer?.renderTerrainSimple(terrain, renderer)
        
        // Enable blending for transparent billboard sprites
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        
        // Disable back-face culling for billboards (they should be visible from both sides)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        
        // Render player billboard using ModelBatch
        batch.begin(camera.getCamera())
        
        // Render terrain mesh if available
        terrainInstance?.let { instance ->
            batch.render(instance, environment)
        }
        
        // Render player billboard if available
        playerBillboardInstance?.let { instance ->
            // Ensure billboard is rendered with proper depth testing
            // Billboards should be depth tested but can be transparent
            batch.render(instance, environment)
            
            // Debug: Render a simple wireframe box at billboard position to verify visibility
            renderer.begin(ShapeRenderer.ShapeType.Line)
            renderer.color = com.badlogic.gdx.graphics.Color.CYAN
            renderer.box(
                playerWorldPosition.x - 0.5f,
                playerWorldPosition.y - 0.5f,
                playerWorldPosition.z - 0.5f,
                1f, 1f, 1f
            )
            renderer.end()
        } ?: run {
            ClientLogger.warn("Player billboard instance is null", "GameScreen")
        }
        
        batch.end()
        
        // Re-enable culling for other rendering
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        
        // Note: Blending remains enabled for transparent billboards
        // This is fine as long as subsequent rendering handles it appropriately
        
        // Render click indicators
        renderClickIndicators(renderer, camera.getCamera(), currentTime)
        
        // Render light position indicator (debug)
        renderLightIndicator(renderer, camera.getCamera())
        
        // Render path debug (optional)
        if (playerPath.isNotEmpty()) {
            renderPath(renderer, camera.getCamera())
        }
    }
    
    private fun renderClickIndicators(renderer: ShapeRenderer, camera: com.badlogic.gdx.graphics.Camera, currentTime: Float) {
        if (clickIndicators.isEmpty()) return
        
        renderer.begin(ShapeRenderer.ShapeType.Filled)
        
        for (indicator in clickIndicators) {
            val age = currentTime - indicator.spawnTime
            val alpha = 1f - (age / 1f).coerceIn(0f, 1f) // Fade out over 1 second
            
            // Render circle on terrain (yellow, fading)
            renderer.color = com.badlogic.gdx.graphics.Color(1f, 1f, 0f, alpha)
            val radius = 1.5f
            val segments = 16 // Fewer segments for better performance
            
            // Draw filled circle using triangles
            val center = indicator.position
            for (i in 0 until segments) {
                val angle1 = (i * 2f * kotlin.math.PI / segments).toFloat()
                val angle2 = ((i + 1) * 2f * kotlin.math.PI / segments).toFloat()
                
                val x1 = center.x + radius * kotlin.math.cos(angle1)
                val z1 = center.z + radius * kotlin.math.sin(angle1)
                val x2 = center.x + radius * kotlin.math.cos(angle2)
                val z2 = center.z + radius * kotlin.math.sin(angle2)
                
                // Draw triangle (center -> point1 -> point2) to create filled circle
                renderer.triangle(
                    center.x, center.z,
                    x1, z1,
                    x2, z2
                )
            }
        }
        
        renderer.end()
    }
    
    private fun updatePlayerMovement(delta: Float, terrain: TerrainData) {
        // Player movement is now handled by server
        // This function only applies server position updates
        // Keyboard input is sent to server as movement commands
        
        // Check for keyboard input and send movement commands to server
        val camera = diabloCamera ?: return
        val inputDirection = Vector3(0f, 0f, 0f)
        
        // WASD and Arrow keys movement
        if (pressedKeys.contains(Input.Keys.W) || pressedKeys.contains(Input.Keys.UP)) {
            inputDirection.z += 1f
        }
        if (pressedKeys.contains(Input.Keys.S) || pressedKeys.contains(Input.Keys.DOWN)) {
            inputDirection.z -= 1f
        }
        if (pressedKeys.contains(Input.Keys.A) || pressedKeys.contains(Input.Keys.LEFT)) {
            inputDirection.x -= 1f
        }
        if (pressedKeys.contains(Input.Keys.D) || pressedKeys.contains(Input.Keys.RIGHT)) {
            inputDirection.x += 1f
        }
        
        // Send movement commands to server (throttled to avoid spam)
        if (inputDirection.len2() > 0.01f) {
            // Cancel any active path when keyboard movement starts
            playerPath = emptyList()
            pathIndex = 0
            playerTargetPosition = null
            
            // Get camera-relative directions
            val cameraForward = camera.getForwardDirection()
            val cameraRight = camera.getRightDirection()
            
            // Transform input direction to world space
            val worldDirection = Vector3(0f, 0f, 0f)
            worldDirection.add(cameraForward.scl(inputDirection.z))
            worldDirection.add(cameraRight.scl(inputDirection.x))
            worldDirection.nor()
            
            // Determine direction string for server
            val direction = when {
                kotlin.math.abs(worldDirection.z) > kotlin.math.abs(worldDirection.x) -> {
                    if (worldDirection.z < 0) "up" else "down"
                }
                else -> {
                    if (worldDirection.x < 0) "left" else "right"
                }
            }
            
            // Send movement command to server (throttled)
            val now = System.currentTimeMillis()
            if (now - lastMovementCommandTime > 100) { // Max 10 commands per second
                lastMovementCommandTime = now
                scope.launch {
                    viewModel.movePlayer(direction)
                }
            }
        }
        
        // Apply server position updates (interpolate smoothly)
        applyServerPosition(terrain, delta)
    }
    
    private var lastMovementCommandTime = 0L
    private var serverPosition: net.kibotu.ro.client.model.Position? = null
    private var targetServerPosition: net.kibotu.ro.client.model.Position? = null
    
    /**
     * Update point light position to be in front of player based on facing direction
     */
    private fun updatePlayerLight() {
        val light = playerPointLight ?: return
        val terrain = terrainData ?: return
        
        // Calculate light position: in front of player in facing direction
        val lightOffsetDistance = 2f // Distance in front of player
        val lightPosition = Vector3(playerFacingDirection).scl(lightOffsetDistance)
        lightPosition.add(playerWorldPosition)
        
        // Keep light slightly above terrain
        val terrainHeightAtLight = terrain.getHeightAt(lightPosition.x, lightPosition.z)
        lightPosition.y = terrainHeightAtLight + 1.5f // Light 1.5 units above terrain
        
        // Update point light position
        light.position.set(lightPosition)
    }
    
    /**
     * Render a visual indicator showing where the point light is positioned
     */
    private fun renderLightIndicator(renderer: ShapeRenderer, camera: com.badlogic.gdx.graphics.Camera) {
        val light = playerPointLight ?: return
        
        renderer.begin(ShapeRenderer.ShapeType.Filled)
        renderer.color = com.badlogic.gdx.graphics.Color.YELLOW
        val lightPos = light.position
        // Render a small box at light position to visualize it
        renderer.box(
            lightPos.x - 0.2f,
            lightPos.y - 0.2f,
            lightPos.z - 0.2f,
            0.4f, 0.4f, 0.4f
        )
        renderer.end()
    }
    
    private fun renderPath(renderer: ShapeRenderer, camera: com.badlogic.gdx.graphics.Camera) {
        if (playerPath.isEmpty()) return
        
        renderer.begin(ShapeRenderer.ShapeType.Line)
        renderer.color = com.badlogic.gdx.graphics.Color.CYAN
        
        // Render path as lines in 3D space
        for (i in 0 until playerPath.size - 1) {
            val p1 = playerPath[i]
            val p2 = playerPath[i + 1]
            
            // Draw line in 3D space (slightly above terrain)
            renderer.line(p1.x, p1.y + 0.1f, p1.z, p2.x, p2.y + 0.1f, p2.z)
            }
            
            renderer.end()
    }
    
    override fun resize(width: Int, height: Int) {
        diabloCamera?.resize(width.toFloat(), height.toFloat())
    }
    
    override fun pause() {}
    
    override fun resume() {}
    
    override fun hide() {}
    
    override fun dispose() {
        scope.cancel()
        shapeRenderer?.dispose()
        spriteBatch?.dispose()
        modelBatch?.dispose()
        terrainRenderer?.dispose()
        playerBillboard?.dispose()
        noviceAtlas?.dispose()
        shapeRenderer = null
        spriteBatch = null
        modelBatch = null
        terrainRenderer = null
        playerBillboard = null
        noviceAtlas = null
        playerSprite = null
    }
    
    /**
     * Handle mouse click for click-to-move and camera rotation
     * Right mouse button controls:
     * - Double-click: Reset camera view
     * - Hold + drag: Rotate camera
     * - Hold Ctrl + drag: Zoom camera
     * - Hold Shift + drag: Change camera height
     * Middle mouse button controls:
     * - Hold + drag vertically: Rotate camera vertically (change elevation)
     */
    fun handleMouseDown(screenX: Int, screenY: Int, button: Int): Boolean {
        if (button == Input.Buttons.MIDDLE) {
            // Middle mouse button pressed - start vertical rotation
            middleMousePressed = true
            lastMouseX = screenX
            lastMouseY = screenY
            return true
        }
        
        if (button == Input.Buttons.RIGHT) {
            val currentTime = System.currentTimeMillis()
            val isDoubleClick = (currentTime - lastRightClickTime) < doubleClickThreshold
            lastRightClickTime = currentTime
            
            if (isDoubleClick) {
                // Double right-click: Reset camera view
                val camera = diabloCamera ?: return false
                camera.resetView()
                ClientLogger.info("Camera view reset to default", "GameScreen")
                return true
            } else {
                // Right mouse button pressed - start camera control
                rightMousePressed = true
                lastMouseX = screenX
                lastMouseY = screenY
                return true
            }
        }
        
        if (button != Input.Buttons.LEFT) return false
        
        val camera = diabloCamera?.getCamera() ?: return false
        val terrain = terrainData ?: return false
        
        // Convert screen coordinates to world coordinates (raycast to terrain)
        // LibGDX coordinate system:
        // - Input.getX()/getY() typically returns TOP-LEFT origin (0,0 at top-left, Y increases downward)
        // - Camera.unproject() expects BOTTOM-LEFT origin (0,0 at bottom-left, Y increases upward)
        // Standard approach: flip Y coordinate: flippedY = viewportHeight - screenY
        // 
        // However, if Y-axis is inverted (clicking bottom makes player walk up):
        // Option 1: Don't flip (if Input already provides bottom-left coordinates)
        // Option 2: Flip but the resulting world Z is inverted (need to invert Z instead)
        // 
        // Current fix: Try WITHOUT flipping Y (removed the flip)
        val viewportHeight = Gdx.graphics.height.toFloat()
        val unprojectY = screenY.toFloat() // No flip - test if this fixes Y-axis inversion
        
        // If this doesn't work, try: val unprojectY = viewportHeight - screenY.toFloat()
        
        val near = Vector3()
        val far = Vector3()
        camera.unproject(near.set(screenX.toFloat(), unprojectY, 0f))
        camera.unproject(far.set(screenX.toFloat(), unprojectY, 1f))
        
        ClientLogger.debug("Screen coords: ($screenX, $screenY), viewportHeight: $viewportHeight, unprojectY: $unprojectY (no flip)", "GameScreen")
        
        // Raycast: find intersection with terrain
        val direction = Vector3(far).sub(near).nor()
        val rayOrigin = Vector3(near)
        
        ClientLogger.info("Raycast: screen($screenX, $screenY) -> origin($rayOrigin), dir($direction)", "GameScreen")
        
        // Improved raycast: use binary search for better accuracy
        var hitPoint: Vector3? = null
        val rayLength = 1000f // Max ray distance
        val minStep = 0.1f // Minimum step size
        
        // Binary search for intersection
        var tMin = 0f
        var tMax = rayLength
        var lastValidPoint: Vector3? = null
        
        // First, find approximate intersection using larger steps
        var t = 0f
        var stepSize = 5f
        var lastY = rayOrigin.y
        
        while (t < rayLength) {
            val point = Vector3(rayOrigin).add(Vector3(direction).scl(t))
            
            // Check bounds
            if (point.x >= 0 && point.x < terrainWidth && point.z >= 0 && point.z < terrainHeight) {
                val terrainHeightAtPoint = terrain.getHeightAt(point.x, point.z)
                
                // Check if ray has intersected terrain (going downward through terrain surface)
                if (point.y <= terrainHeightAtPoint + 0.2f && lastY > terrainHeightAtPoint) {
                    // Found intersection, refine with binary search
                    tMin = t - stepSize
                    tMax = t
                    lastValidPoint = Vector3(point.x, terrainHeightAtPoint, point.z)
                    break
                }
                
                lastY = point.y
            }
            
            t += stepSize
        }
        
        // Refine intersection with binary search
        if (lastValidPoint != null) {
            var iterations = 0
            while (iterations < 20 && (tMax - tMin) > minStep) {
                t = (tMin + tMax) / 2f
                val point = Vector3(rayOrigin).add(Vector3(direction).scl(t))
                
                if (point.x >= 0 && point.x < terrainWidth && point.z >= 0 && point.z < terrainHeight) {
                    val terrainHeightAtPoint = terrain.getHeightAt(point.x, point.z)
                    if (point.y <= terrainHeightAtPoint + 0.1f) {
                        tMax = t
                        hitPoint = Vector3(point.x, terrainHeightAtPoint, point.z)
                    } else {
                        tMin = t
                    }
                } else {
                    tMax = t
                }
                iterations++
            }
        }
        
        // Fallback: if no intersection found, use plane intersection at Y=0
        if (hitPoint == null && direction.y < -0.01f) { // Ray going downward
            val planeY = 0f
            val t = (planeY - rayOrigin.y) / direction.y
            if (t > 0 && t < rayLength) {
                val point = Vector3(rayOrigin).add(Vector3(direction).scl(t))
                if (point.x >= 0 && point.x < terrainWidth && point.z >= 0 && point.z < terrainHeight) {
                    val actualHeight = terrain.getHeightAt(point.x, point.z)
                    hitPoint = Vector3(point.x, actualHeight, point.z)
                    ClientLogger.info("Fallback plane intersection at t=$t: ($hitPoint)", "GameScreen")
                }
            }
        }
        
        if (hitPoint != null) {
            // Clamp to terrain bounds
            var targetX = hitPoint.x.coerceIn(0f, terrainWidth.toFloat())
            var targetZ = hitPoint.z.coerceIn(0f, terrainHeight.toFloat())
            
            // Fix Y-axis inversion: For perspective camera with southeast orientation,
            // the Z coordinate from raycast might be inverted relative to screen Y.
            // When clicking "up" on screen (higher screenY), Z should increase to move "northwest".
            // If Z is inverted, we need to reflect it. Since we can't negate (would go negative),
            // we'll test by NOT flipping screenY for the unproject, or by adjusting the hit point.
            // Actually, the simplest test: try NOT flipping screenY and see if that fixes it.
            // But that would break the coordinate system...
            // Let's try a different approach: use the camera's view to determine if Z needs adjustment.
            // For now, we'll add debug logging and let the user test.
            // If Y-axis is inverted, we might need to: targetZ = terrainHeight - targetZ
            // But that would invert around center, not what we want.
            // Better: Check if we need to adjust the unproject call itself.
            
            val targetHeight = terrain.getHeightAt(targetX, targetZ)
            
            // Add click indicator (yellow circle that fades out after 1 second)
            val currentTime = System.currentTimeMillis() / 1000f
            clickIndicators.add(ClickIndicator(Vector3(targetX, targetHeight + 0.1f, targetZ), currentTime))
            
            ClientLogger.info("Click at screen ($screenX, $screenY) -> world ($targetX, $targetHeight, $targetZ)", "GameScreen")
            ClientLogger.info("Player position: (${playerWorldPosition.x}, ${playerWorldPosition.y}, ${playerWorldPosition.z})", "GameScreen")
            
            // Send move-to command to server (server handles pathfinding)
            val targetTileX = targetX.toInt()
            val targetTileZ = targetZ.toInt()
            
            ClientLogger.info("Sending move-to command to server: target tile=($targetTileX, $targetTileZ)", "GameScreen")
            
            // Clear any local path (server will handle pathfinding)
            playerPath = emptyList()
            pathIndex = 0
            playerTargetPosition = null
            
            // Send command to server via ViewModel
            scope.launch {
                viewModel.movePlayerToPosition(Position(targetTileX, targetTileZ))
            }
            
            return true
        }
        
        ClientLogger.warn("No terrain intersection found for click at ($screenX, $screenY)", "GameScreen")
        ClientLogger.warn("Near: ($near), Far: ($far), Direction: ($direction)", "GameScreen")
        return false
    }
    
    fun handleMouseUp(screenX: Int, screenY: Int, button: Int): Boolean {
        if (button == Input.Buttons.MIDDLE) {
            // Middle mouse button released - stop vertical rotation
            middleMousePressed = false
            return true
        }
        
        if (button == Input.Buttons.RIGHT) {
            // Right mouse button released - stop camera rotation
            rightMousePressed = false
            return true
        }
        return false
    }
    
    fun handleMouseMoved(screenX: Int, screenY: Int): Boolean {
        // Update last mouse position for drag calculations
        lastMouseX = screenX
        lastMouseY = screenY
        return false
    }
    
    /**
     * Handle mouse dragged (right-click and middle-click hold and drag for camera control)
     * Right mouse button controls:
     * - Hold + drag left/right: Rotate camera horizontally
     * - Hold Ctrl + drag up/down: Zoom camera in/out
     * - Hold Shift + drag up/down: Change camera height
     * Middle mouse button controls:
     * - Hold + drag vertically: Rotate camera vertically (change elevation)
     */
    fun handleMouseDragged(screenX: Int, screenY: Int, deltaTime: Float): Boolean {
        val camera = diabloCamera ?: return false
        val deltaX = screenX - lastMouseX
        val deltaY = screenY - lastMouseY
        
        if (middleMousePressed) {
            // Middle mouse button + drag vertically: Rotate camera vertically
            if (deltaY != 0) {
                camera.rotateVerticalByPixels(deltaY.toFloat())
                ClientLogger.debug("Camera vertical rotation: elevation=${camera.getElevation()}°", "GameScreen")
            }
            lastMouseX = screenX
            lastMouseY = screenY
            return true
        }
        
        if (rightMousePressed) {
            // Check for modifier keys
            val ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)
            val shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
            
            when {
                ctrlPressed -> {
                    // Ctrl + drag: Zoom camera
                    if (deltaY != 0) {
                        camera.zoomByDrag(deltaY.toFloat())
                        ClientLogger.debug("Camera zoom: distance=${camera.getDistance()}", "GameScreen")
                    }
                }
                shiftPressed -> {
                    // Shift + drag: Change camera height
                    if (deltaY != 0) {
                        camera.adjustHeightByDrag(deltaY.toFloat())
                        ClientLogger.debug("Camera height adjusted", "GameScreen")
                    }
                }
                else -> {
                    // Default: Rotate camera horizontally
                    if (deltaX != 0) {
                        // Use sensitivity of 0.5 degrees per pixel for smooth rotation
                        camera.rotateHorizontalByPixels(deltaX.toFloat(), sensitivity = 0.5f)
                    }
                }
            }
            
            lastMouseX = screenX
            lastMouseY = screenY
            return true
        }
        return false
    }
    
    fun handleScrolled(amountX: Float, amountY: Float): Boolean {
        // Handle mouse wheel zoom
        val camera = diabloCamera ?: return false
        if (amountY != 0f) {
            // amountY > 0 means scroll up (zoom out), amountY < 0 means scroll down (zoom in)
            // Negate amountY so that scrolling up zooms out (increases distance)
            camera.zoom(-amountY)
            return true
        }
        return false
    }
    
    fun handleKeyDown(keycode: Int): Boolean {
        // Track pressed keys for WASD and arrow keys
        when (keycode) {
            Input.Keys.W, Input.Keys.S, Input.Keys.A, Input.Keys.D,
            Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT -> {
                pressedKeys.add(keycode)
                return true
            }
        }
        return false
    }
    
    fun handleKeyUp(keycode: Int): Boolean {
        // Remove key from pressed set
        when (keycode) {
            Input.Keys.W, Input.Keys.S, Input.Keys.A, Input.Keys.D,
            Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT -> {
                pressedKeys.remove(keycode)
                return true
            }
        }
        return false
    }
    
    /**
     * Update game state from ViewModel - applies server position updates
     */
    fun updateGameState(
        playerPosition: Position?,
        players: Map<String, net.kibotu.ro.client.model.PlayerDTO>,
        npcs: Map<String, net.kibotu.ro.client.model.NPCDTO>,
        monsters: Map<String, net.kibotu.ro.client.model.MonsterDTO>
    ) {
        // Update server position for interpolation
        if (playerPosition != null) {
            val currentServerPos = serverPosition
            if (currentServerPos != playerPosition) {
                // Server position changed - update target for interpolation
                serverPosition = playerPosition
                targetServerPosition = playerPosition
            }
        }
    }
    
    /**
     * Apply server position updates with smooth interpolation
     */
    private fun applyServerPosition(terrain: TerrainData, delta: Float) {
        val targetPos = targetServerPosition ?: return
        
        // Convert server tile position to world coordinates
        val targetWorldX = targetPos.x.toFloat()
        val targetWorldZ = targetPos.y.toFloat()
        
        // Calculate distance to target
        val dx = targetWorldX - playerWorldPosition.x
        val dz = targetWorldZ - playerWorldPosition.z
        val distance = kotlin.math.sqrt(dx * dx + dz * dz)
        
        // If close enough, snap to position
        if (distance < 0.1f) {
            playerWorldPosition.x = targetWorldX
            playerWorldPosition.z = targetWorldZ
        } else {
            // Interpolate towards server position
            val interpolationSpeed = 10f // units per second
            val moveDistance = interpolationSpeed * delta
            val moveRatio = (moveDistance / distance).coerceIn(0f, 1f)
            
            playerWorldPosition.x += dx * moveRatio
            playerWorldPosition.z += dz * moveRatio
        }
        
        // Update height based on terrain
        val terrainHeight = terrain.getHeightAt(playerWorldPosition.x, playerWorldPosition.z)
        playerWorldPosition.y = terrainHeight + 1f
    }
}
