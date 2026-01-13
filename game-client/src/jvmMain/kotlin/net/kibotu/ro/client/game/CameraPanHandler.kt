package net.kibotu.ro.client.game

import net.kibotu.ro.client.util.ClientLogger

/**
 * Handles camera panning via middle-click or right-click drag.
 * Based on GAME_ENGINE_SPECIFICATION.md section 5.4.1
 */
class CameraPanHandler(
    private val camera: Camera,
    private val mapWidth: Int,
    private val mapHeight: Int
) {
    private var isPanning = false
    private var panStartX = 0f
    private var panStartY = 0f
    private var cameraStartX = 0f
    private var cameraStartY = 0f
    
    /**
     * Start camera panning
     */
    fun startPan(screenX: Float, screenY: Float) {
        isPanning = true
        panStartX = screenX
        panStartY = screenY
        cameraStartX = camera.cameraX
        cameraStartY = camera.cameraY
        ClientLogger.debug("Camera pan started at ($screenX, $screenY)", "CameraPanHandler")
    }
    
    /**
     * Update camera panning based on mouse movement
     */
    fun updatePan(screenX: Float, screenY: Float) {
        if (!isPanning) return
        
        val deltaX = panStartX - screenX
        val deltaY = panStartY - screenY
        
        // Update camera position directly (for free camera mode)
        val newCameraX = cameraStartX + deltaX
        val newCameraY = cameraStartY + deltaY
        
        // Set camera position directly (clamping will be handled by camera's clampToBounds)
        camera.setCameraPosition(newCameraX, newCameraY)
        
        // Clamp camera to map bounds
        camera.clampToBounds(mapWidth, mapHeight)
    }
    
    /**
     * End camera panning
     */
    fun endPan() {
        if (isPanning) {
            ClientLogger.debug("Camera pan ended", "CameraPanHandler")
        }
        isPanning = false
    }
    
    /**
     * Check if currently panning
     */
    fun isCurrentlyPanning(): Boolean = isPanning
}

