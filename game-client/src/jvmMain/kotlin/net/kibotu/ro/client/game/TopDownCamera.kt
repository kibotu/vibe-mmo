package net.kibotu.ro.client.game

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector3
import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger

/**
 * Simple top-down orthographic camera for 2D tile-based rendering.
 * Camera follows player position and renders tiles from above.
 */
class TopDownCamera(
    viewportWidth: Float,
    viewportHeight: Float,
    private val mapWidth: Int,
    private val mapHeight: Int,
    private val tileSize: Float = 32f
) {
    private val camera = OrthographicCamera(viewportWidth, viewportHeight)
    
    // Camera position in world coordinates (center of viewport)
    private var targetX: Float = 0f
    private var targetY: Float = 0f
    
    // Zoom level (higher = zoomed out)
    private var zoom: Float = 1f
    private val minZoom: Float = 0.5f
    private val maxZoom: Float = 3f
    
    init {
        // Set camera to look down from above (Y is up in world space)
        camera.position.set(0f, 10f, 0f) // 10 units above ground
        camera.direction.set(0f, -1f, 0f) // Look straight down
        camera.up.set(0f, 0f, -1f) // Z is forward/backward
        camera.near = 0.1f
        camera.far = 100f
        camera.update()
    }
    
    /**
     * Update camera to follow target position
     * Camera is centered on the target position (player)
     * 
     * @param targetWorldX Tile X coordinate of target
     * @param targetWorldY Tile Y coordinate of target
     */
    fun update(targetWorldX: Float, targetWorldY: Float) {
        targetX = targetWorldX
        targetY = targetWorldY
        
        // Update camera position (X and Z are horizontal, Y is vertical)
        // Convert tile coordinates to world coordinates
        // Camera should be centered on player, so we use the center of the tile
        // This matches how entities are rendered: entity.position.x * tileSize + tileSize / 2f
        val worldX = targetX * tileSize + tileSize / 2f
        val worldZ = targetY * tileSize + tileSize / 2f
        
        camera.position.x = worldX
        camera.position.z = worldZ
        
        // Clamp camera to map boundaries
        clampToBounds()
        
        camera.update()
    }
    
    /**
     * Clamp camera to map boundaries
     */
    private fun clampToBounds() {
        val halfViewportWidth = (camera.viewportWidth * zoom) / 2f
        val halfViewportHeight = (camera.viewportHeight * zoom) / 2f
        
        val maxX = mapWidth * tileSize - halfViewportWidth
        val maxZ = mapHeight * tileSize - halfViewportHeight
        
        camera.position.x = camera.position.x.coerceIn(halfViewportWidth, maxX)
        camera.position.z = camera.position.z.coerceIn(halfViewportHeight, maxZ)
    }
    
    /**
     * Set zoom level
     */
    fun setZoom(newZoom: Float) {
        zoom = newZoom.coerceIn(minZoom, maxZoom)
        camera.zoom = zoom
        camera.update()
        clampToBounds()
    }
    
    /**
     * Zoom in/out
     */
    fun zoomIn() {
        setZoom(zoom * 1.1f)
    }
    
    fun zoomOut() {
        setZoom(zoom / 1.1f)
    }
    
    /**
     * Get the LibGDX camera for rendering
     */
    fun getGdxCamera(): OrthographicCamera = camera
    
    /**
     * Convert world position to screen coordinates
     */
    fun worldToScreen(worldX: Float, worldY: Float): Vector3 {
        val worldPos = Vector3(worldX * tileSize, 0f, worldY * tileSize)
        val screenPos = Vector3()
        camera.project(screenPos.set(worldPos))
        return screenPos
    }
    
    /**
     * Convert screen coordinates to world position
     */
    fun screenToWorld(screenX: Float, screenY: Float): Position {
        val screenPos = Vector3(screenX, screenY, 0f)
        val worldPos = Vector3()
        camera.unproject(worldPos.set(screenPos))
        return Position(
            (worldPos.x / tileSize).toInt(),
            (worldPos.z / tileSize).toInt()
        )
    }
    
    /**
     * Resize camera viewport
     */
    fun resize(width: Float, height: Float) {
        camera.viewportWidth = width
        camera.viewportHeight = height
        camera.update()
        clampToBounds()
    }
    
    /**
     * Get current zoom level
     */
    fun getZoom(): Float = zoom
    
    /**
     * Get camera position in world coordinates
     */
    fun getPosition(): Vector3 = Vector3(camera.position)
}

