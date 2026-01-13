package net.kibotu.ro.client.game

import net.kibotu.ro.client.model.Position

/**
 * Collision map for tile-based collision detection.
 * true = walkable, false = blocked
 */
class CollisionMap(
    val width: Int,
    val height: Int,
    private val collisionData: BooleanArray = BooleanArray(width * height) { true }
) {
    /**
     * Check if a position is walkable
     */
    fun isWalkable(x: Int, y: Int): Boolean {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false
        }
        val index = y * width + x
        return collisionData[index]
    }
    
    /**
     * Set walkability for a position
     */
    fun setWalkable(x: Int, y: Int, walkable: Boolean) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            val index = y * width + x
            collisionData[index] = walkable
        }
    }
    
    /**
     * Check if a position is valid (within bounds and walkable)
     */
    fun isValidPosition(position: Position): Boolean {
        return isWalkable(position.x, position.y)
    }
    
    companion object {
        /**
         * Create a collision map from a base64-encoded bitmap string
         * Format matches zone server: bit 7 (MSB) first, 1 = walkable (true), 0 = blocked (false)
         */
        fun fromBase64(base64: String, width: Int, height: Int): CollisionMap {
            val bytes = java.util.Base64.getDecoder().decode(base64)
            val collisionData = BooleanArray(width * height)
            
            for (i in collisionData.indices) {
                val byteIndex = i / 8
                val bitIndex = 7 - (i % 8) // MSB first (bit 7)
                if (byteIndex < bytes.size) {
                    val byte = bytes[byteIndex].toInt()
                    // bit = 1 means walkable (true), bit = 0 means blocked (false)
                    collisionData[i] = (byte and (1 shl bitIndex)) != 0
                } else {
                    collisionData[i] = true // Default to walkable if out of bounds
                }
            }
            
            return CollisionMap(width, height, collisionData)
        }
        
        /**
         * Create a default collision map (all walkable)
         */
        fun default(width: Int, height: Int): CollisionMap {
            return CollisionMap(width, height)
        }
    }
}

