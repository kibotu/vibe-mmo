package net.kibotu.ro.client.game

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.MathUtils
import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger

/**
 * Isometric-style perspective camera system according to GAME_SPEC.md section 1.
 * Features:
 * - Perspective projection with configurable FOV
 * - Orbit rotation around player (right/middle mouse drag)
 * - Zoom (mouse wheel)
 * - Smooth following with lerp
 * - Camera collision with terrain
 */
class IsometricCamera(
    private val gdxCamera: PerspectiveCamera,
    private val mapWidth: Int,
    private val mapHeight: Int
) {
    // Camera configuration (from GAME_SPEC.md section 1.2)
    private var defaultHeight: Float = 17.5f // 15-20 units above terrain
    private var defaultDistance: Float = 15f // 12-18 units from player (default: 15)
    private var defaultHorizontalAngle: Float = 0f // Face north (default orientation)
    private var defaultVerticalAngle: Float = 65f // ~65° high isometric view (default)
    
    // Current camera state
    private var currentHeight: Float = defaultHeight
    private var currentDistance: Float = defaultDistance
    private var horizontalAngle: Float = defaultHorizontalAngle // 0-360° rotation around player
    private var verticalAngle: Float = defaultVerticalAngle // 40-65° elevation (from GAME_SPEC.md section 1.3)
    
    // Camera rotation (from GAME_SPEC.md section 1.3)
    private val rotationSpeed: Float = 150f // 120-180°/second while dragging
    private val minVerticalAngle: Float = 40f // 40° minimum
    private val maxVerticalAngle: Float = 65f // 65° maximum
    
    // Zoom (from GAME_SPEC.md section 1.3)
    private val minDistance: Float = 8f
    private val maxDistance: Float = 25f
    private val zoomSpeed: Float = 2f // 2 units per scroll tick
    
    // Vertical tilt (from GAME_SPEC.md section 1.3)
    private val tiltIncrement: Float = 5f // 5° increment per scroll step
    
    // Camera collision (from GAME_SPEC.md section 1.4)
    private val minCameraDistance: Float = 3f // minimum distance from player
    
    // Target position (player position)
    private var targetPosition = Vector3(0f, 0f, 0f)
    private var currentCameraPosition = Vector3(0f, 0f, 0f)
    
    // FOV (from GAME_SPEC.md section 1.1)
    var fov: Float = 50f // 45-60° recommended
        set(value) {
            field = value.coerceIn(30f, 90f)
            gdxCamera.fieldOfView = field
            gdxCamera.update()
        }
    
    init {
        gdxCamera.fieldOfView = fov
        gdxCamera.near = 0.1f
        gdxCamera.far = 1000f
        updateCameraPosition()
    }
    
    /**
     * Update camera to follow target position rigidly (no interpolation)
     * According to GAME_SPEC.md section 1.3: "Camera position is rigidly locked to player position"
     * "Camera moves instantly with player (no lag/interpolation)"
     */
    fun update(targetWorldX: Float, targetWorldY: Float, deltaTime: Float = 0f) {
        // Update target position (assuming Y is up in world space)
        targetPosition.set(targetWorldX, 0f, targetWorldY)
        
        // Calculate camera position instantly (rigid follow)
        val desiredPosition = calculateDesiredCameraPosition()
        currentCameraPosition.set(desiredPosition)
        
        // Apply camera collision with terrain
        applyCameraCollision()
        
        // Update camera look-at
        updateCameraLookAt()
    }
    
    /**
     * Calculate desired camera position based on current angles and distance
     * According to GAME_SPEC.md section 1.3:
     * Spherical to Cartesian coordinates:
     * x = distance * cos(verticalAngle) * sin(horizontalAngle)
     * y = distance * sin(verticalAngle)
     * z = distance * cos(verticalAngle) * cos(horizontalAngle)
     */
    private fun calculateDesiredCameraPosition(): Vector3 {
        val radHorizontal = MathUtils.degreesToRadians * horizontalAngle
        val radVertical = MathUtils.degreesToRadians * verticalAngle
        
        // Spherical to Cartesian conversion (from GAME_SPEC.md section 1.3)
        val x = currentDistance * MathUtils.cos(radVertical) * MathUtils.sin(radHorizontal)
        val y = currentDistance * MathUtils.sin(radVertical)
        val z = currentDistance * MathUtils.cos(radVertical) * MathUtils.cos(radHorizontal)
        
        return Vector3(
            targetPosition.x + x,
            targetPosition.y + currentHeight + y,
            targetPosition.z + z
        )
    }
    
    /**
     * Apply camera collision with terrain (simplified version)
     * Full implementation would raycast from player to camera position
     */
    private fun applyCameraCollision() {
        // Simplified: ensure minimum distance
        val distanceToPlayer = currentCameraPosition.dst(targetPosition)
        if (distanceToPlayer < minCameraDistance) {
            // Move camera away from player
            val direction = Vector3(currentCameraPosition).sub(targetPosition).nor()
            currentCameraPosition.set(
                targetPosition.x + direction.x * minCameraDistance,
                currentCameraPosition.y,
                targetPosition.z + direction.z * minCameraDistance
            )
        }
    }
    
    /**
     * Update camera look-at target (look at player position)
     */
    private fun updateCameraLookAt() {
        gdxCamera.position.set(currentCameraPosition)
        gdxCamera.lookAt(targetPosition)
        gdxCamera.up.set(Vector3.Y)
        gdxCamera.update()
    }
    
    /**
     * Rotate camera horizontally (orbit around player)
     * According to GAME_SPEC.md section 1.3: Right mouse button hold + drag left/right
     * @param deltaAngle Change in angle in degrees
     */
    fun rotateHorizontal(deltaAngle: Float) {
        horizontalAngle += deltaAngle
        // Normalize to 0-360 range
        horizontalAngle = horizontalAngle % 360f
        if (horizontalAngle < 0) horizontalAngle += 360f
        updateCameraPosition()
    }
    
    /**
     * Rotate camera horizontally by pixel delta (for mouse drag)
     * @param deltaX Pixel delta (positive = rotate right/counter-clockwise)
     */
    fun rotateHorizontalByPixels(deltaX: Float, deltaTime: Float) {
        // Convert pixel movement to angle rotation
        // Rotation speed: 120-180°/second
        val rotationDegrees = (deltaX / 100f) * rotationSpeed * deltaTime
        rotateHorizontal(rotationDegrees)
    }
    
    /**
     * Adjust camera elevation (vertical tilt)
     * According to GAME_SPEC.md section 1.3: Hold Shift + mouse wheel up/down
     * @param delta Positive to increase elevation, negative to decrease
     */
    fun adjustElevation(delta: Float) {
        verticalAngle = (verticalAngle + delta * tiltIncrement).coerceIn(minVerticalAngle, maxVerticalAngle)
        updateCameraPosition()
    }
    
    /**
     * Zoom camera in/out
     * According to GAME_SPEC.md section 1.3: Mouse wheel up/down
     * @param amount Positive to zoom out, negative to zoom in
     */
    fun zoom(amount: Float) {
        currentDistance = (currentDistance + amount * zoomSpeed).coerceIn(minDistance, maxDistance)
        updateCameraPosition()
    }
    
    /**
     * Reset camera to face north (default orientation)
     * According to GAME_SPEC.md section 1.3: Double right-click to reset camera to face north
     */
    fun resetToNorth() {
        horizontalAngle = defaultHorizontalAngle
        updateCameraPosition()
    }
    
    /**
     * Reset camera elevation to default
     * According to GAME_SPEC.md section 1.3: Double right-click while holding Shift to reset elevation
     */
    fun resetElevation() {
        verticalAngle = defaultVerticalAngle
        updateCameraPosition()
    }
    
    /**
     * Reset zoom to default
     * According to GAME_SPEC.md section 1.3: Hold Ctrl + double right-click to reset zoom
     */
    fun resetZoom() {
        currentDistance = defaultDistance
        updateCameraPosition()
    }
    
    /**
     * Update camera position immediately
     */
    private fun updateCameraPosition() {
        val desiredPosition = calculateDesiredCameraPosition()
        currentCameraPosition.set(desiredPosition)
        applyCameraCollision()
        updateCameraLookAt()
    }
    
    /**
     * Get the LibGDX camera for rendering
     */
    fun getGdxCamera(): Camera = gdxCamera
    
    /**
     * Convert world position to screen coordinates
     * Note: This is a simplified version. Full implementation would use proper projection
     */
    fun worldToScreen(worldX: Float, worldY: Float, worldZ: Float = 0f): Vector3 {
        val worldPos = Vector3(worldX, worldY, worldZ)
        val screenPos = Vector3()
        gdxCamera.project(screenPos.set(worldPos))
        return screenPos
    }
    
    /**
     * Convert screen coordinates to world position (raycast)
     */
    fun screenToWorld(screenX: Float, screenY: Float): Vector3 {
        val screenPos = Vector3(screenX, screenY, 0f)
        val worldPos = Vector3()
        gdxCamera.unproject(worldPos.set(screenPos))
        return worldPos
    }
    
    /**
     * Reset camera to default position (all settings)
     */
    fun reset() {
        currentHeight = defaultHeight
        currentDistance = defaultDistance
        horizontalAngle = defaultHorizontalAngle
        verticalAngle = defaultVerticalAngle
        updateCameraPosition()
    }
    
    /**
     * Get current horizontal rotation angle
     */
    fun getHorizontalAngle(): Float = horizontalAngle
    
    /**
     * Get current vertical elevation angle
     */
    fun getVerticalAngle(): Float = verticalAngle
    
    /**
     * Get current zoom distance
     */
    fun getDistance(): Float = currentDistance
    
    /**
     * Get current camera configuration
     */
    fun getConfig(): CameraConfig = CameraConfig(
        height = currentHeight,
        distance = currentDistance,
        horizontalAngle = horizontalAngle,
        verticalAngle = verticalAngle,
        fov = fov
    )
    
    /**
     * Set camera configuration
     */
    fun setConfig(config: CameraConfig) {
        currentHeight = config.height
        currentDistance = config.distance.coerceIn(minDistance, maxDistance)
        horizontalAngle = config.horizontalAngle
        verticalAngle = config.verticalAngle.coerceIn(minVerticalAngle, maxVerticalAngle)
        fov = config.fov
        updateCameraPosition()
    }
    
    data class CameraConfig(
        val height: Float,
        val distance: Float,
        val horizontalAngle: Float,
        val verticalAngle: Float,
        val fov: Float
    )
}


