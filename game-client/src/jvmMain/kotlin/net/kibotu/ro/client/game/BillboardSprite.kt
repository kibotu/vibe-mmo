package net.kibotu.ro.client.game

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector3
import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.util.ClientLogger
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Billboard sprite system according to GAME_SPEC.md section 3.
 * Features:
 * - 2D sprites rendered in 3D space that always face camera
 * - 8-directional sprites (South, Southwest, West, Northwest, North, Northeast, East, Southeast)
 * - Animation states (idle, walk, attack, hit, dead)
 */
enum class SpriteDirection(val index: Int) {
    SOUTH(0),
    SOUTHWEST(1),
    WEST(2),
    NORTHWEST(3),
    NORTH(4),
    NORTHEAST(5),
    EAST(6),
    SOUTHEAST(7);
    
    companion object {
        /**
         * Calculate sprite direction from character forward direction and camera forward
         * According to GAME_SPEC.md section 3.2
         */
        fun fromDirection(direction: String, cameraForward: Vector3): SpriteDirection {
            // Map direction string to angle
            val directionAngle = when (direction) {
                "up" -> 90f
                "down" -> 270f
                "left" -> 180f
                "right" -> 0f
                else -> 0f
            }
            
            // Calculate angle between character forward and camera forward
            val cameraAngle = Math.toDegrees(atan2(cameraForward.z.toDouble(), cameraForward.x.toDouble())).toFloat()
            
            // Relative angle
            var relativeAngle = directionAngle - cameraAngle
            while (relativeAngle < 0) relativeAngle += 360f
            while (relativeAngle >= 360) relativeAngle -= 360f
            
            // Snap to nearest 45° increment
            val snappedAngle = ((relativeAngle + 22.5f) / 45f).toInt() * 45f
            
            return when (snappedAngle.toInt()) {
                0, 360 -> EAST
                45 -> NORTHEAST
                90 -> NORTH
                135 -> NORTHWEST
                180 -> WEST
                225 -> SOUTHWEST
                270 -> SOUTH
                315 -> SOUTHEAST
                else -> SOUTH
            }
        }
    }
}

enum class AnimationState {
    IDLE,
    WALK,
    ATTACK,
    HIT,
    DEAD
}

/**
 * Billboard sprite data structure
 */
data class BillboardSpriteData(
    val spriteSheet: Texture?,
    val spriteWidth: Int = 128,
    val spriteHeight: Int = 128,
    val directions: Int = 8,
    val animationFrames: Map<AnimationState, Int> = mapOf(
        AnimationState.IDLE to 3,
        AnimationState.WALK to 6,
        AnimationState.ATTACK to 4,
        AnimationState.HIT to 2,
        AnimationState.DEAD to 1
    ),
    val animationDurations: Map<AnimationState, Float> = mapOf(
        AnimationState.IDLE to 0.5f,
        AnimationState.WALK to 0.6f,
        AnimationState.ATTACK to 0.4f,
        AnimationState.HIT to 0.2f,
        AnimationState.DEAD to 0f
    )
) {
    /**
     * Get texture region for a specific direction and animation frame
     */
    fun getTextureRegion(direction: SpriteDirection, state: AnimationState, frame: Int): TextureRegion? {
        if (spriteSheet == null) return null
        
        val frames = animationFrames[state] ?: 1
        val frameIndex = frame % frames
        
        // Calculate sprite sheet position
        // Layout: 8 directions × N animation frames
        val row = direction.index
        val col = frameIndex
        
        val x = col * spriteWidth
        val y = row * spriteHeight
        
        return TextureRegion(spriteSheet, x, y, spriteWidth, spriteHeight)
    }
}

/**
 * Billboard renderer for entities
 */
class BillboardRenderer(
    private val spriteBatch: SpriteBatch
) {
    private val spriteScale: Float = 1.75f // 1.5-2.0 units height in world space (from GAME_SPEC.md)
    
    /**
     * Render a billboard sprite
     */
    fun render(
        spriteData: BillboardSpriteData,
        worldPosition: Vector3,
        direction: SpriteDirection,
        animationState: AnimationState,
        animationTime: Float,
        camera: com.badlogic.gdx.graphics.Camera
    ) {
        val frames = spriteData.animationFrames[animationState] ?: 1
        val duration = spriteData.animationDurations[animationState] ?: 0f
        
        // Calculate current frame
        val frame = if (duration > 0) {
            ((animationTime / duration) * frames).toInt() % frames
        } else {
            0
        }
        
        val textureRegion = spriteData.getTextureRegion(direction, animationState, frame) ?: return
        
        // Convert world position to screen position
        val screenPos = Vector3()
        camera.project(screenPos.set(worldPosition))
        
        // Calculate sprite size (maintain aspect ratio)
        val width = spriteData.spriteWidth * spriteScale
        val height = spriteData.spriteHeight * spriteScale
        
        // Render sprite (always facing camera)
        spriteBatch.begin()
        spriteBatch.draw(
            textureRegion,
            screenPos.x - width / 2,
            screenPos.y - height / 2,
            width,
            height
        )
        spriteBatch.end()
    }
}

/**
 * Billboard entity component
 */
class BillboardComponent(
    var spriteData: BillboardSpriteData? = null,
    var currentState: AnimationState = AnimationState.IDLE,
    var animationTime: Float = 0f,
    var direction: SpriteDirection = SpriteDirection.SOUTH
) {
    /**
     * Update animation time
     */
    fun update(deltaTime: Float) {
        if (currentState != AnimationState.DEAD) {
            animationTime += deltaTime
        }
    }
    
    /**
     * Set animation state
     */
    fun setState(state: AnimationState) {
        if (currentState != state) {
            currentState = state
            animationTime = 0f
        }
    }
}


