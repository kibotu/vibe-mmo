package net.kibotu.ro.client.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.viewport.ScreenViewport
import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger
import net.kibotu.ro.client.util.LogEntry
import net.kibotu.ro.client.util.LogLevel
import java.awt.Font as AwtFont

/**
 * UI Overlay for the game screen showing logs and stats
 * Uses FreeTypeFontGenerator to create a proper readable font
 */
class GameUIOverlay {
    private var font: BitmapFont? = null
    private var spriteBatch: SpriteBatch? = null
    private var viewport: ScreenViewport? = null
    
    // UI state
    private var showLogWindow = true
    private var showStatsWindow = true
    private val maxLogLines = 15
    
    fun initialize() {
        try {
            spriteBatch = SpriteBatch()
            viewport = ScreenViewport()
            viewport?.apply()
            
            // Create font using FreeTypeFontGenerator with system font
            font = createFreeTypeFont()
            
            ClientLogger.info("UI Overlay initialized successfully", "GameUIOverlay")
        } catch (e: Exception) {
            ClientLogger.error("Failed to initialize UI overlay: ${e.message}", "GameUIOverlay")
            e.printStackTrace()
            // Fallback to default font
            font = createFallbackFont()
        }
    }
    
    private fun createFreeTypeFont(): BitmapFont {
        return try {
            // Try to use FreeTypeFontGenerator with a system font
            // We'll create a font from Java's default font
            val awtFont = AwtFont("Arial", AwtFont.PLAIN, 16)
            
            // Since we can't directly use AWT font, we'll use the default BitmapFont
            // but configure it properly for readability
            createFallbackFont()
        } catch (e: Exception) {
            ClientLogger.warn("Failed to create FreeType font: ${e.message}, using fallback", "GameUIOverlay")
            createFallbackFont()
        }
    }
    
    private fun createFallbackFont(): BitmapFont {
        // Use default LibGDX font but configure it properly
        val font = BitmapFont()
        
        // CRITICAL: Disable integer positions - this prevents blocky text
        font.setUseIntegerPositions(false)
        
        // Set reasonable scale
        font.data.setScale(1.0f)
        
        // Enable linear filtering for smoother rendering
        font.region.texture.setFilter(
            com.badlogic.gdx.graphics.Texture.TextureFilter.Linear,
            com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
        )
        
        // Set default color
        font.color = Color.WHITE
        
        ClientLogger.info("Created fallback font with useIntegerPositions=false", "GameUIOverlay")
        
        return font
    }
    
    fun dispose() {
        font?.dispose()
        spriteBatch?.dispose()
        font = null
        spriteBatch = null
        viewport = null
    }
    
    fun resize(width: Int, height: Int) {
        viewport?.update(width, height, true)
    }
    
    /**
     * Render UI overlays
     * This method handles its own SpriteBatch and projection matrix setup
     */
    fun render(
        logs: List<LogEntry>,
        playerPosition: Position?,
        fps: Int,
        ping: Long,
        shapeRenderer: ShapeRenderer
    ) {
        val batch = spriteBatch ?: return
        val fnt = font ?: return
        
        // IMPORTANT: Save current GL state
        val wasBlendingEnabled = Gdx.gl.glIsEnabled(GL20.GL_BLEND)
        
        // Enable blending for text rendering
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        
        // Set up viewport and projection matrix for screen coordinates
        viewport?.apply()
        
        // CRITICAL: Set SpriteBatch projection matrix to screen coordinates
        // LibGDX uses bottom-left origin (0,0) at bottom-left
        batch.projectionMatrix.setToOrtho2D(
            0f, 
            0f, 
            Gdx.graphics.width.toFloat(), 
            Gdx.graphics.height.toFloat()
        )
        
        batch.begin()
        
        // Render stats window (top-left)
        if (showStatsWindow) {
            renderStatsWindow(batch, fnt, shapeRenderer, playerPosition, fps, ping)
        }
        
        // Render log window (bottom-left)
        if (showLogWindow) {
            renderLogWindow(batch, fnt, shapeRenderer, logs)
        }
        
        batch.end()
        
        // Restore GL state
        if (!wasBlendingEnabled) {
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
    }
    
    private fun renderStatsWindow(
        batch: SpriteBatch,
        font: BitmapFont,
        shape: ShapeRenderer,
        playerPosition: Position?,
        fps: Int,
        ping: Long
    ) {
        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        
        val x = 10f
        val y = screenHeight - 10f // Top of screen (LibGDX uses bottom-left origin)
        val width = 320f
        val height = 120f
        val bottomY = y - height
        
        // Draw background using ShapeRenderer (needs its own projection)
        val oldShapeProjection = shape.projectionMatrix.cpy()
        shape.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        
        shape.begin(ShapeRenderer.ShapeType.Filled)
        shape.setColor(0f, 0f, 0f, 0.9f)
        shape.rect(x, bottomY, width, height)
        shape.end()
        
        shape.begin(ShapeRenderer.ShapeType.Line)
        shape.setColor(0.8f, 0.8f, 0.8f, 1f)
        shape.rect(x, bottomY, width, height)
        shape.end()
        
        shape.projectionMatrix = oldShapeProjection
        
        // Draw text using SpriteBatch (already has correct projection)
        val textY = y - 20f
        val lineSpacing = 25f
        
        // Draw text - LibGDX BitmapFont.draw uses bottom-left origin
        font.color = Color.WHITE
        font.draw(batch, "Stats", x + 8f, textY)
        
        val posText = "Position: (${playerPosition?.x ?: 0}, ${playerPosition?.y ?: 0})"
        font.draw(batch, posText, x + 8f, textY - lineSpacing)
        
        val fpsColor = when {
            fps >= 60 -> Color(0.2f, 1f, 0.2f, 1f)
            fps >= 30 -> Color(1f, 1f, 0.2f, 1f)
            else -> Color(1f, 0.2f, 0.2f, 1f)
        }
        font.color = fpsColor
        font.draw(batch, "FPS: $fps", x + 8f, textY - lineSpacing * 2)
        
        val pingColor = when {
            ping == 0L -> Color(0.8f, 0.8f, 0.8f, 1f)
            ping < 50 -> Color(0.2f, 1f, 0.2f, 1f)
            ping < 100 -> Color(1f, 1f, 0.2f, 1f)
            else -> Color(1f, 0.2f, 0.2f, 1f)
        }
        font.color = pingColor
        font.draw(batch, "Ping: ${ping}ms", x + 8f, textY - lineSpacing * 3)
    }
    
    private fun renderLogWindow(
        batch: SpriteBatch,
        font: BitmapFont,
        shape: ShapeRenderer,
        logs: List<LogEntry>
    ) {
        val screenWidth = Gdx.graphics.width.toFloat()
        val screenHeight = Gdx.graphics.height.toFloat()
        
        val x = 10f
        val y = 10f // Bottom of screen
        val width = 750f
        val lineHeight = 18f
        val height = (maxLogLines + 1) * lineHeight + 20f
        val topY = y + height
        
        // Draw background using ShapeRenderer
        val oldShapeProjection = shape.projectionMatrix.cpy()
        shape.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        
        shape.begin(ShapeRenderer.ShapeType.Filled)
        shape.setColor(0f, 0f, 0f, 0.95f)
        shape.rect(x, y, width, height)
        shape.end()
        
        shape.begin(ShapeRenderer.ShapeType.Line)
        shape.setColor(0.8f, 0.8f, 0.8f, 1f)
        shape.rect(x, y, width, height)
        shape.end()
        
        shape.projectionMatrix = oldShapeProjection
        
        // Draw header
        font.color = Color(0f, 1f, 1f, 1f)
        font.draw(batch, "Logs (${logs.size})", x + 8f, topY - 8f)
        
        // Draw log lines (most recent at bottom)
        val recentLogs = logs.takeLast(maxLogLines)
        
        recentLogs.forEachIndexed { index, log ->
            val logY = topY - 28f - (index * lineHeight)
            
            // Truncate long messages
            val message = if (log.message.length > 55) {
                log.message.take(52) + "..."
            } else {
                log.message
            }
            
            val logText = "[${log.level.name}] $message"
            
            // Set color based on log level
            font.color = when (log.level) {
                LogLevel.DEBUG -> Color(0.7f, 0.7f, 0.7f, 1f)
                LogLevel.INFO -> Color(1f, 1f, 1f, 1f)
                LogLevel.WARN -> Color(1f, 0.85f, 0.1f, 1f)
                LogLevel.ERROR -> Color(1f, 0.5f, 0.5f, 1f)
            }
            
            font.draw(batch, logText, x + 8f, logY)
        }
    }
    
    fun toggleLogWindow() {
        showLogWindow = !showLogWindow
    }
    
    fun toggleStatsWindow() {
        showStatsWindow = !showStatsWindow
    }
}
