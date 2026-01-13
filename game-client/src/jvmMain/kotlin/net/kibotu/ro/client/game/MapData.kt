package net.kibotu.ro.client.game

import net.kibotu.ro.client.model.Position

/**
 * Map data structure containing all information about a game map
 */
data class MapData(
    val name: String,
    val width: Int,
    val height: Int,
    val tileSize: Int = 32,
    val collisionMap: CollisionMap,
    val spawnPoints: List<Position>,
    val groundColor: Int = 0xFF90EE90.toInt(), // Light green default
    val heightMap: FloatArray? = null, // Optional heightmap data from server
    val maxHeight: Float = 2f // Maximum height value for normalization
) {
    /**
     * Get a random spawn point
     */
    fun getRandomSpawnPoint(): Position {
        return if (spawnPoints.isNotEmpty()) {
            spawnPoints.random()
        } else {
            Position(width / 2, height / 2)
        }
    }
    
    /**
     * Check if a position is within map bounds
     */
    fun isWithinBounds(position: Position): Boolean {
        return position.x >= 0 && position.y >= 0 &&
               position.x < width && position.y < height
    }
    
    /**
     * Check if a position is valid (within bounds and walkable)
     */
    fun isValidPosition(position: Position): Boolean {
        return isWithinBounds(position) && collisionMap.isValidPosition(position)
    }
}

