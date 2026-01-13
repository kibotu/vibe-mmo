package net.kibotu.ro.client.game

import net.kibotu.ro.client.model.Position

/**
 * Camera system that follows the player character.
 * Handles world-to-screen coordinate conversion and viewport management.
 * Supports both follow mode and free camera mode.
 */
class Camera(
    var targetX: Float,
    var targetY: Float,
    var viewportWidth: Float,
    var viewportHeight: Float,
    var tileSize: Float = 32f
) {
    // For free camera mode: direct camera position control
    private var freeCameraX: Float? = null
    private var freeCameraY: Float? = null
    
    // Calculate camera position (top-left corner of viewport in world coordinates)
    val cameraX: Float
        get() = freeCameraX ?: (targetX * tileSize - viewportWidth / 2)
    
    val cameraY: Float
        get() = freeCameraY ?: (targetY * tileSize - viewportHeight / 2)
    
    /**
     * Set camera position directly (for free camera mode)
     */
    fun setCameraPosition(x: Float, y: Float) {
        freeCameraX = x
        freeCameraY = y
    }
    
    /**
     * Reset to follow mode (clear free camera position)
     */
    fun resetToFollowMode() {
        freeCameraX = null
        freeCameraY = null
    }
    
    /**
     * Convert world coordinates to screen coordinates
     */
    fun worldToScreen(worldX: Float, worldY: Float): Pair<Float, Float> {
        return Pair(
            worldX * tileSize - cameraX,
            worldY * tileSize - cameraY
        )
    }
    
    /**
     * Convert screen coordinates to world coordinates
     */
    fun screenToWorld(screenX: Float, screenY: Float): Position {
        return Position(
            ((screenX + cameraX) / tileSize).toInt(),
            ((screenY + cameraY) / tileSize).toInt()
        )
    }
    
    /**
     * Clamp camera to map boundaries
     */
    fun clampToBounds(mapWidth: Int, mapHeight: Int) {
        val maxCameraX = mapWidth * tileSize - viewportWidth
        val maxCameraY = mapHeight * tileSize - viewportHeight
        
        val clampedX = cameraX.coerceIn(0f, maxCameraX)
        val clampedY = cameraY.coerceIn(0f, maxCameraY)
        
        // Update based on current mode
        if (freeCameraX != null || freeCameraY != null) {
            // Free camera mode: update free camera position
            if (clampedX != cameraX || clampedY != cameraY) {
                setCameraPosition(clampedX, clampedY)
            }
        } else {
            // Follow mode: adjust target to match clamped camera position
            targetX = (clampedX + viewportWidth / 2) / tileSize
            targetY = (clampedY + viewportHeight / 2) / tileSize
        }
    }
    
    /**
     * Check if a world position is within the viewport
     */
    fun isInViewport(worldX: Float, worldY: Float, margin: Float = tileSize): Boolean {
        val (screenX, screenY) = worldToScreen(worldX, worldY)
        return screenX >= -margin &&
               screenX <= viewportWidth + margin &&
               screenY >= -margin &&
               screenY <= viewportHeight + margin
    }
    
    /**
     * Update camera to follow target position
     */
    fun update(targetWorldX: Float, targetWorldY: Float, mapWidth: Int, mapHeight: Int) {
        // Only update if not in free camera mode
        if (freeCameraX == null && freeCameraY == null) {
            targetX = targetWorldX
            targetY = targetWorldY
            clampToBounds(mapWidth, mapHeight)
        }
    }
}

