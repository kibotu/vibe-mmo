package net.kibotu.ro.zone.model

import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * Complete map data structure for zone server
 */
@Serializable
data class MapData(
    // Basic Information
    val name: String,
    val displayName: String,
    val description: String? = null,
    
    // Dimensions
    val width: Int,
    val height: Int,
    val tileSize: Int = 32,
    
    // Collision Data
    val collisionMap: CollisionMapData,
    
    // Terrain Data (optional - for 3D terrain heightmaps)
    val heightMap: HeightMapData? = null,
    
    // Spawn Points
    val spawnPoints: List<Position>,
    
    // Static Entities
    val npcs: List<NPCData>,
    
    // Monster Spawns (optional)
    val monsterSpawns: List<MonsterSpawnData>? = null,
    
    // Metadata
    val version: String = "1.0",
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * Collision map data structure
 */
@Serializable
data class CollisionMapData(
    val width: Int,
    val height: Int,
    val data: String  // Base64-encoded bitmap
)

/**
 * Heightmap data structure for 3D terrain
 * Stores height values as FloatArray encoded in Base64
 */
@Serializable
data class HeightMapData(
    val width: Int,
    val height: Int,
    val data: String,  // Base64-encoded FloatArray (4 bytes per float)
    val maxHeight: Float = 2f  // Maximum height value for normalization
)

/**
 * NPC data structure
 */
@Serializable
data class NPCData(
    val id: String,
    val name: String,
    val position: Position,
    val type: String,
    val direction: String = "down",
    val properties: Map<String, String> = emptyMap()
)

/**
 * Monster spawn data structure
 */
@Serializable
data class MonsterSpawnData(
    val id: String,
    val monsterType: String,
    val position: Position,
    val respawnTime: Int = 5000,
    val maxCount: Int = 1,
    val spawnRadius: Int = 0
)

/**
 * Runtime collision map for efficient collision checking
 */
class CollisionMap(
    val width: Int,
    val height: Int,
    private val collisionData: BooleanArray
) {
    fun isWalkable(x: Int, y: Int): Boolean {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false
        }
        val index = y * width + x
        return collisionData[index]
    }
    
    fun setWalkable(x: Int, y: Int, walkable: Boolean) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            val index = y * width + x
            collisionData[index] = walkable
        }
    }
    
    fun toBase64(): String {
        val bytes = ByteArray((width * height + 7) / 8)
        for (i in collisionData.indices) {
            if (collisionData[i]) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
            }
        }
        return Base64.getEncoder().encodeToString(bytes)
    }
    
    companion object {
        fun fromBase64(base64: String, width: Int, height: Int): CollisionMap {
            val bytes = Base64.getDecoder().decode(base64)
            val collisionData = BooleanArray(width * height)
            for (i in collisionData.indices) {
                collisionData[i] = (bytes[i / 8].toInt() and (1 shl (7 - (i % 8)))) != 0
            }
            return CollisionMap(width, height, collisionData)
        }
        
        fun createEmpty(width: Int, height: Int, defaultWalkable: Boolean = true): CollisionMap {
            val collisionData = BooleanArray(width * height) { defaultWalkable }
            return CollisionMap(width, height, collisionData)
        }
    }
}

