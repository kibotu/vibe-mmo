package net.kibotu.ro.client.game

import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger

/**
 * Handles mouse input for player movement (click-to-move).
 * Based on GAME_ENGINE_SPECIFICATION.md section 5.3
 */
class MouseInputHandler(
    private val camera: Camera,
    private val onMovePlayerTo: (Position) -> Unit
) {
    private var lastClickTime = 0L
    private val doubleClickThreshold = 300L // milliseconds
    
    /**
     * Handle left mouse click for click-to-move
     */
    fun handleLeftClick(screenX: Float, screenY: Float) {
        val worldPos = camera.screenToWorld(screenX, screenY)
        val currentTime = System.currentTimeMillis()
        val isDoubleClick = (currentTime - lastClickTime) < doubleClickThreshold
        lastClickTime = currentTime
        
        ClientLogger.debug(
            "Left click at screen ($screenX, $screenY) -> world ($worldPos), doubleClick=$isDoubleClick",
            "MouseInputHandler"
        )
        
        // For prototype: simple direct movement (no pathfinding)
        // Future: implement pathfinding for double-click run
        movePlayerTo(worldPos, run = isDoubleClick)
    }
    
    /**
     * Move player to target position
     * For prototype: simple direct movement toward target
     */
    private fun movePlayerTo(targetPos: Position, run: Boolean) {
        ClientLogger.info(
            "Moving player to ($targetPos), run=$run",
            "MouseInputHandler"
        )
        
        // Call the callback to move player
        onMovePlayerTo(targetPos)
    }
}

