package net.kibotu.ro.client.game

import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger

/**
 * Camera mode enumeration
 */
enum class CameraMode {
    FOLLOW_PLAYER,  // Camera follows player (default)
    FREE_CAMERA     // Camera can be panned freely
}

/**
 * Controls camera behavior and mode switching.
 * Based on GAME_ENGINE_SPECIFICATION.md section 5.4.3
 */
class CameraController(
    private val camera: Camera,
    private val mapWidth: Int,
    private val mapHeight: Int
) {
    private var mode = CameraMode.FOLLOW_PLAYER
    
    /**
     * Toggle between follow player and free camera modes
     */
    fun toggleMode() {
        mode = when (mode) {
            CameraMode.FOLLOW_PLAYER -> {
                ClientLogger.info("Camera mode switched to FREE_CAMERA", "CameraController")
                // Initialize free camera position to current camera position
                camera.setCameraPosition(camera.cameraX, camera.cameraY)
                CameraMode.FREE_CAMERA
            }
            CameraMode.FREE_CAMERA -> {
                ClientLogger.info("Camera mode switched to FOLLOW_PLAYER", "CameraController")
                // Reset to follow mode
                camera.resetToFollowMode()
                CameraMode.FOLLOW_PLAYER
            }
        }
    }
    
    /**
     * Get current camera mode
     */
    fun getMode(): CameraMode = mode
    
    /**
     * Update camera based on current mode
     */
    fun update(playerPosition: Position?) {
        when (mode) {
            CameraMode.FOLLOW_PLAYER -> {
                playerPosition?.let { pos ->
                    camera.update(
                        pos.x.toFloat(),
                        pos.y.toFloat(),
                        mapWidth,
                        mapHeight
                    )
                }
            }
            CameraMode.FREE_CAMERA -> {
                // Camera position is controlled by panning/edge scrolling
                // Just ensure bounds are respected
                camera.clampToBounds(mapWidth, mapHeight)
            }
        }
    }
    
    /**
     * Set camera mode explicitly
     */
    fun setMode(newMode: CameraMode) {
        if (mode != newMode) {
            mode = newMode
            when (newMode) {
                CameraMode.FREE_CAMERA -> {
                    ClientLogger.info("Camera mode set to FREE_CAMERA", "CameraController")
                    // Initialize free camera position to current camera position
                    camera.setCameraPosition(camera.cameraX, camera.cameraY)
                }
                CameraMode.FOLLOW_PLAYER -> {
                    ClientLogger.info("Camera mode set to FOLLOW_PLAYER", "CameraController")
                    // Reset to follow mode
                    camera.resetToFollowMode()
                }
            }
        }
    }
}

