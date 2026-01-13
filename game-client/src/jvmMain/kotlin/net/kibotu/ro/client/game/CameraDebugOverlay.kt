package net.kibotu.ro.client.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger

/**
 * Unity-style axis gizmo debug overlay for camera visualization
 * Shows:
 * - Camera axes (X=Red, Y=Green, Z=Blue)
 * - Player facing direction
 * - Camera position and rotation info
 */
class CameraDebugOverlay {
    private var enabled: Boolean = true // Default to enabled for debugging
    
    /**
     * Toggle debug overlay visibility
     */
    fun toggle() {
        enabled = !enabled
        ClientLogger.info("Camera debug overlay ${if (enabled) "enabled" else "disabled"}", "CameraDebugOverlay")
    }
    
    /**
     * Set debug overlay visibility
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    /**
     * Check if debug overlay is enabled
     */
    fun isEnabled(): Boolean = enabled
    
    /**
     * Render camera axes gizmo and player facing direction as a fixed screen overlay
     * Similar to Unity's scene view gizmo - fixed to screen, shows camera-relative directions
     * @param renderer ShapeRenderer for drawing lines
     * @param camera LibGDX camera
     * @param playerPosition Player world position
     * @param playerDirection Player facing direction ("up", "down", "left", "right")
     * @param cameraHorizontalAngle Camera horizontal rotation angle (0-360°)
     * @param cameraVerticalAngle Camera vertical elevation angle (40-65°)
     */
    fun render(
        renderer: ShapeRenderer,
        camera: com.badlogic.gdx.graphics.Camera,
        playerPosition: Position?,
        playerDirection: String?,
        cameraHorizontalAngle: Float,
        cameraVerticalAngle: Float
    ) {
        if (!enabled || playerPosition == null) return
        
        // Save current projection matrix
        val oldProjection = renderer.projectionMatrix.cpy()
        
        // Switch to screen-space (2D) rendering
        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        renderer.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        
        // Disable depth testing for 2D overlay
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        
        // Gizmo position (bottom-right corner, like Unity)
        val gizmoSize = 80f // Size of the gizmo in pixels
        val margin = 20f
        val centerX = screenWidth - margin - gizmoSize / 2f
        val centerY = margin + gizmoSize / 2f
        
        // Calculate player direction relative to camera
        val playerWorldDir = calculatePlayerForwardVector(playerDirection ?: "down")
        
        // Transform player direction to camera space
        val cameraRight = Vector3(camera.direction).crs(Vector3.Y).nor()
        val cameraUp = Vector3(cameraRight).crs(camera.direction).nor()
        val cameraForward = Vector3(camera.direction).nor()
        
        // Project player direction onto camera's right/forward plane
        val playerDirInCameraSpace = Vector3(
            playerWorldDir.dot(cameraRight),
            playerWorldDir.dot(cameraUp),
            playerWorldDir.dot(cameraForward)
        )
        
        // Draw gizmo background (semi-transparent circle)
        renderer.begin(ShapeRenderer.ShapeType.Filled)
        renderer.color = Color(0f, 0f, 0f, 0.5f)
        renderer.circle(centerX, centerY, gizmoSize / 2f + 5f)
        renderer.end()
        
        // Draw axes (camera-relative)
        val axisLength = gizmoSize * 0.35f
        Gdx.gl.glLineWidth(3f)
        renderer.begin(ShapeRenderer.ShapeType.Line)
        
        // X axis (Red) - Camera Right
        renderer.color = Color.RED
        renderer.line(centerX, centerY, centerX + axisLength, centerY)
        
        // Y axis (Green) - Camera Up
        renderer.color = Color.GREEN
        renderer.line(centerX, centerY, centerX, centerY + axisLength)
        
        // Z axis (Blue) - Camera Forward (into screen, so shorter)
        renderer.color = Color.BLUE
        val forwardLength = axisLength * 0.7f
        renderer.line(centerX, centerY, centerX - forwardLength * 0.7f, centerY - forwardLength * 0.7f)
        
        // Draw player facing direction (Yellow arrow) relative to camera
        playerDirection?.let {
            renderer.color = Color.YELLOW
            val playerDirX = playerDirInCameraSpace.x * axisLength * 1.2f
            val playerDirY = playerDirInCameraSpace.y * axisLength * 1.2f
            
            val arrowEndX = centerX + playerDirX
            val arrowEndY = centerY + playerDirY
            
            // Draw arrow line
            renderer.line(centerX, centerY, arrowEndX, arrowEndY)
            
            // Draw arrow head
            val arrowLength = 8f
            val arrowWidth = 6f
            val dir = Vector3(playerDirX, playerDirY, 0f).nor()
            val perp = Vector3(-dir.y, dir.x, 0f) // Perpendicular vector
            
            val arrowTipX = arrowEndX
            val arrowTipY = arrowEndY
            val arrowBaseX = arrowEndX - dir.x * arrowLength
            val arrowBaseY = arrowEndY - dir.y * arrowLength
            
            val arrowLeftX = arrowBaseX + perp.x * arrowWidth
            val arrowLeftY = arrowBaseY + perp.y * arrowWidth
            val arrowRightX = arrowBaseX - perp.x * arrowWidth
            val arrowRightY = arrowBaseY - perp.y * arrowWidth
            
            renderer.line(arrowTipX, arrowTipY, arrowLeftX, arrowLeftY)
            renderer.line(arrowTipX, arrowTipY, arrowRightX, arrowRightY)
        }
        
        renderer.end()
        
        // Draw axis labels (small circles at axis ends)
        renderer.begin(ShapeRenderer.ShapeType.Filled)
        val labelSize = 4f
        
        // X axis label (red)
        renderer.color = Color.RED
        renderer.circle(centerX + axisLength, centerY, labelSize)
        
        // Y axis label (green)
        renderer.color = Color.GREEN
        renderer.circle(centerX, centerY + axisLength, labelSize)
        
        // Z axis label (blue)
        renderer.color = Color.BLUE
        renderer.circle(centerX - forwardLength * 0.7f, centerY - forwardLength * 0.7f, labelSize)
        
        renderer.end()
        
        // Restore projection matrix and GL state
        renderer.projectionMatrix = oldProjection
        Gdx.gl.glLineWidth(1f)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }
    
    /**
     * Calculate player forward vector based on direction string
     */
    private fun calculatePlayerForwardVector(direction: String): Vector3 {
        return when (direction) {
            "up" -> Vector3(0f, 0f, -1f)    // North (negative Z)
            "down" -> Vector3(0f, 0f, 1f)   // South (positive Z)
            "left" -> Vector3(-1f, 0f, 0f)   // West (negative X)
            "right" -> Vector3(1f, 0f, 0f)  // East (positive X)
            else -> Vector3(0f, 0f, -1f)    // Default to north
        }
    }
    
    
    /**
     * Render camera info text overlay (for future use with font rendering)
     */
    fun getCameraInfoText(
        playerPosition: Position?,
        cameraHorizontalAngle: Float,
        cameraVerticalAngle: Float,
        cameraDistance: Float
    ): String {
        if (playerPosition == null) return ""
        
        return """
            Camera Debug Info:
            Player Pos: (${playerPosition.x}, ${playerPosition.y})
            Camera Angle: ${String.format("%.1f", cameraHorizontalAngle)}°
            Elevation: ${String.format("%.1f", cameraVerticalAngle)}°
            Distance: ${String.format("%.1f", cameraDistance)} units
            Axes: Red=X, Green=Y, Blue=Z
            Cyan=Camera Forward, Yellow=Player Forward
        """.trimIndent()
    }
}

