package net.kibotu.ro.client.game

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.MathUtils

/**
 * Diablo-style isometric perspective camera
 * Features:
 * - Perspective projection
 * - Locked to player position
 * - Side view angle (similar to Diablo)
 * - Fixed distance and angle for isometric feel
 */
class DiabloCamera(
    viewportWidth: Float,
    viewportHeight: Float,
    private val terrainData: TerrainData
) {
    private val camera: PerspectiveCamera
    private val targetPosition = Vector3(0f, 0f, 0f)
    
    // Camera configuration for Diablo-style view
    private var cameraDistance: Float = 30f // Distance from player (increased for better view)
    private var cameraHeight: Float = 20f // Height above terrain (can be adjusted)
    private var cameraAngle: Float = 45f // Horizontal angle (degrees) - southeast view (can be rotated)
    private var cameraElevation: Float = 50f // Vertical elevation angle (degrees) - can be rotated
    
    // Default values for reset
    private val defaultDistance: Float = 30f
    private val defaultHeight: Float = 20f
    private val defaultAngle: Float = 45f
    private val defaultElevation: Float = 50f
    
    // Vertical rotation limits
    private val minElevation: Float = 30f // Minimum elevation angle (degrees)
    private val maxElevation: Float = 75f // Maximum elevation angle (degrees)
    private val verticalRotationSensitivity: Float = 0.3f // Degrees per pixel dragged
    
    // Zoom limits
    private val minDistance: Float = 15f // Minimum zoom distance
    private val maxDistance: Float = 60f // Maximum zoom distance
    private val zoomSpeed: Float = 5f // Units per scroll tick
    
    // Height adjustment limits
    private val minHeight: Float = 10f // Minimum camera height
    private val maxHeight: Float = 40f // Maximum camera height
    private val heightDragSensitivity: Float = 0.3f // Height change per pixel dragged
    
    // Zoom drag sensitivity
    private val zoomDragSensitivity: Float = 0.2f // Distance change per pixel dragged
    
    // Camera offset from player (for side view) - recalculated when distance changes
    private var offsetX: Float = 0f
    private var offsetZ: Float = 0f
    private var offsetY: Float = 0f
    
    private fun updateOffsets() {
        offsetX = -cameraDistance * MathUtils.cosDeg(cameraElevation) * MathUtils.sinDeg(cameraAngle)
        offsetZ = -cameraDistance * MathUtils.cosDeg(cameraElevation) * MathUtils.cosDeg(cameraAngle)
        offsetY = cameraDistance * MathUtils.sinDeg(cameraElevation)
    }
    
    init {
        camera = PerspectiveCamera(60f, viewportWidth, viewportHeight)
        camera.near = 0.1f
        camera.far = 500f
        updateOffsets()
    }
    
    /**
     * Update camera to follow player position
     */
    fun update(playerWorldX: Float, playerWorldZ: Float, deltaTime: Float = 0f) {
        // Get terrain height at player position
        val terrainHeight = terrainData.getHeightAt(playerWorldX, playerWorldZ)
        
        // Update target position (player position)
        targetPosition.set(playerWorldX, terrainHeight + 1f, playerWorldZ) // Player is 1 unit above terrain
        
        // Calculate camera position (offset from player)
        val cameraX = targetPosition.x + offsetX
        val cameraZ = targetPosition.z + offsetZ
        val cameraY = targetPosition.y + offsetY + cameraHeight
        
        // Ensure camera doesn't go below terrain
        val terrainHeightAtCamera = terrainData.getHeightAt(cameraX, cameraZ)
        val minCameraY = terrainHeightAtCamera + 2f // Keep camera at least 2 units above terrain
        
        camera.position.set(
            cameraX,
            cameraY.coerceAtLeast(minCameraY),
            cameraZ
        )
        
        // Look at player position
        camera.lookAt(targetPosition)
        camera.up.set(Vector3.Y)
        camera.update()
    }
    
    fun getCamera(): PerspectiveCamera = camera
    
    /**
     * Get camera forward direction projected onto XZ plane (for movement)
     * Forward is the direction the camera is looking (towards player), projected onto horizontal plane
     * For movement, we want to move in this direction when pressing W/Up
     */
    fun getForwardDirection(): Vector3 {
        val forward = Vector3(camera.direction)
        forward.y = 0f // Project onto XZ plane
        if (forward.len2() < 0.01f) {
            // If forward is nearly vertical, use a default direction
            return Vector3(0f, 0f, -1f) // Default to north
        }
        return forward.nor()
    }
    
    /**
     * Get camera right direction projected onto XZ plane (for movement)
     * Right is perpendicular to forward on the horizontal plane
     * Calculated as: right = forward × up (where up is Y axis)
     */
    fun getRightDirection(): Vector3 {
        val forward = getForwardDirection()
        val right = Vector3(forward).crs(Vector3.Y) // Cross product with up vector gives right
        if (right.len2() < 0.01f) {
            // If right is nearly zero, use a default direction
            return Vector3(1f, 0f, 0f) // Default to east
        }
        return right.nor()
    }
    
    /**
     * Zoom camera in/out
     * @param amount Positive to zoom out (increase distance), negative to zoom in (decrease distance)
     */
    fun zoom(amount: Float) {
        cameraDistance = (cameraDistance + amount * zoomSpeed).coerceIn(minDistance, maxDistance)
        updateOffsets()
    }
    
    /**
     * Rotate camera horizontally around player
     * @param deltaAngle Change in angle in degrees (positive = rotate right/counter-clockwise when viewed from above)
     */
    fun rotateHorizontal(deltaAngle: Float) {
        cameraAngle += deltaAngle
        // Normalize to 0-360 range
        cameraAngle = cameraAngle % 360f
        if (cameraAngle < 0) cameraAngle += 360f
        updateOffsets()
    }
    
    /**
     * Rotate camera horizontally by pixel delta (for mouse drag)
     * @param deltaX Pixel delta (positive = rotate right/counter-clockwise)
     * @param sensitivity Rotation sensitivity (degrees per pixel)
     */
    fun rotateHorizontalByPixels(deltaX: Float, sensitivity: Float = 0.5f) {
        val deltaAngle = deltaX * sensitivity
        rotateHorizontal(deltaAngle)
    }
    
    /**
     * Zoom camera by pixel delta (for Ctrl + drag)
     * @param deltaY Pixel delta (positive = drag down = zoom out, negative = drag up = zoom in)
     */
    fun zoomByDrag(deltaY: Float) {
        // Invert deltaY: dragging down (positive) should zoom out (increase distance)
        val deltaDistance = -deltaY * zoomDragSensitivity
        cameraDistance = (cameraDistance + deltaDistance).coerceIn(minDistance, maxDistance)
        updateOffsets()
    }
    
    /**
     * Adjust camera height by pixel delta (for Shift + drag)
     * @param deltaY Pixel delta (positive = drag down = decrease height, negative = drag up = increase height)
     */
    fun adjustHeightByDrag(deltaY: Float) {
        // Invert deltaY: dragging down (positive) should decrease height
        val deltaHeight = -deltaY * heightDragSensitivity
        cameraHeight = (cameraHeight + deltaHeight).coerceIn(minHeight, maxHeight)
    }
    
    /**
     * Rotate camera vertically (change elevation angle)
     * @param deltaAngle Change in angle in degrees (positive = increase elevation, negative = decrease elevation)
     */
    fun rotateVertical(deltaAngle: Float) {
        cameraElevation = (cameraElevation + deltaAngle).coerceIn(minElevation, maxElevation)
        updateOffsets()
    }
    
    /**
     * Rotate camera vertically by pixel delta (for middle mouse drag)
     * @param deltaY Pixel delta (positive = drag down = decrease elevation, negative = drag up = increase elevation)
     */
    fun rotateVerticalByPixels(deltaY: Float) {
        // Invert deltaY: dragging down (positive) should decrease elevation (look down more)
        val deltaAngle = -deltaY * verticalRotationSensitivity
        rotateVertical(deltaAngle)
    }
    
    /**
     * Reset camera view to default position (angle, distance, height, elevation)
     */
    fun resetView() {
        cameraDistance = defaultDistance
        cameraHeight = defaultHeight
        cameraAngle = defaultAngle
        cameraElevation = defaultElevation
        updateOffsets()
    }
    
    /**
     * Get current camera distance
     */
    fun getDistance(): Float = cameraDistance
    
    /**
     * Get current camera height
     */
    fun getHeight(): Float = cameraHeight
    
    /**
     * Get current camera angle
     */
    fun getAngle(): Float = cameraAngle
    
    /**
     * Get current camera elevation angle
     */
    fun getElevation(): Float = cameraElevation
    
    fun resize(width: Float, height: Float) {
        camera.viewportWidth = width
        camera.viewportHeight = height
        camera.update()
    }
}

