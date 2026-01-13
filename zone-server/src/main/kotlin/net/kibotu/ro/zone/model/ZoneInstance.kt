package net.kibotu.ro.zone.model

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kibotu.ro.zone.service.PathfindingService

data class Player(
    val characterId: String,
    val name: String,
    var position: Position,
    var direction: String = "down",
    val sessionId: String
)

data class NPC(
    val id: String,
    val name: String,
    val position: Position,
    val type: String
)

data class Monster(
    val id: String,
    val type: String,
    var position: Position,
    var health: Int
)

class ZoneInstance(
    val id: String,
    val zoneName: String,
    val mapData: net.kibotu.ro.zone.model.MapData
) {
    private val players = ConcurrentHashMap<String, Player>()
    private val monsters = ConcurrentHashMap<String, Monster>()
    private val mutex = Mutex()
    private val collisionMap: CollisionMap
    private val pathfindingService: PathfindingService
    // Store active paths for each player (characterId -> path)
    private val playerPaths = ConcurrentHashMap<String, List<Position>>()
    
    init {
        // Decode collision map
        collisionMap = try {
            CollisionMap.fromBase64(
                mapData.collisionMap.data,
                mapData.collisionMap.width,
                mapData.collisionMap.height
            )
        } catch (e: Exception) {
            // Fallback to all walkable if collision map is invalid
            CollisionMap.createEmpty(mapData.width, mapData.height, true)
        }
        pathfindingService = PathfindingService(collisionMap)
    }
    
    /**
     * Add a player to the zone
     */
    suspend fun addPlayer(player: Player): Boolean {
        return mutex.withLock {
            if (players.containsKey(player.characterId)) {
                false
            } else {
                players[player.characterId] = player
                true
            }
        }
    }
    
    /**
     * Remove a player from the zone
     */
    suspend fun removePlayer(characterId: String): Player? {
        return mutex.withLock {
            players.remove(characterId)
        }
    }
    
    /**
     * Get a player by character ID
     */
    fun getPlayer(characterId: String): Player? {
        return players[characterId]
    }
    
    /**
     * Get all players
     */
    fun getAllPlayers(): List<Player> {
        return players.values.toList()
    }
    
    /**
     * Update player position (direct position update)
     */
    suspend fun updatePlayerPosition(characterId: String, position: Position, direction: String): Boolean {
        return mutex.withLock {
            val player = players[characterId] ?: return@withLock false
            if (isValidPosition(position)) {
                player.position = position
                player.direction = direction
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Handle move-to command (click-to-move with pathfinding)
     */
    suspend fun movePlayerTo(characterId: String, targetPosition: Position): MoveResult {
        return mutex.withLock {
            val player = players[characterId] ?: return@withLock MoveResult.Error("Player not found")
            
            if (!isValidPosition(targetPosition)) {
                return@withLock MoveResult.Error("Target position is invalid")
            }
            
            // Find path using A*
            val path = pathfindingService.findPath(player.position, targetPosition)
            
            if (path.isEmpty()) {
                return@withLock MoveResult.Error("No path found to target")
            }
            
            // Store path for this player
            playerPaths[characterId] = path
            
            // Move to first waypoint if path has more than one point
            if (path.size > 1) {
                val nextPosition = path[1] // path[0] is current position
                val direction = calculateDirection(player.position, nextPosition)
                player.position = nextPosition
                player.direction = direction
                return@withLock MoveResult.Success(nextPosition, direction, path)
            } else {
                // Already at target
                return@withLock MoveResult.Success(player.position, player.direction, emptyList())
            }
        }
    }
    
    /**
     * Handle direction-based movement (keyboard input)
     */
    suspend fun movePlayerDirection(characterId: String, direction: String): MoveResult {
        return mutex.withLock {
            val player = players[characterId] ?: return@withLock MoveResult.Error("Player not found")
            
            // Clear any active path when using keyboard movement
            playerPaths.remove(characterId)
            
            val newPosition = when (direction.lowercase()) {
                "up" -> Position(player.position.x, player.position.y - 1)
                "down" -> Position(player.position.x, player.position.y + 1)
                "left" -> Position(player.position.x - 1, player.position.y)
                "right" -> Position(player.position.x + 1, player.position.y)
                else -> return@withLock MoveResult.Error("Invalid direction: $direction")
            }
            
            if (!isValidPosition(newPosition)) {
                return@withLock MoveResult.Error("Cannot move in that direction")
            }
            
            player.position = newPosition
            player.direction = direction.lowercase()
            
            return@withLock MoveResult.Success(newPosition, player.direction, emptyList())
        }
    }
    
    /**
     * Continue following active path (called periodically)
     */
    suspend fun continuePath(characterId: String): MoveResult? {
        return mutex.withLock {
            val player = players[characterId] ?: return@withLock null
            val path = playerPaths[characterId] ?: return@withLock null
            
            if (path.isEmpty() || path.size <= 1) {
                playerPaths.remove(characterId)
                return@withLock null
            }
            
            // Find current position in path
            val currentIndex = path.indexOfFirst { it == player.position }
            if (currentIndex == -1 || currentIndex >= path.size - 1) {
                // Off path or reached end
                playerPaths.remove(characterId)
                return@withLock null
            }
            
            // Move to next waypoint
            val nextPosition = path[currentIndex + 1]
            val direction = calculateDirection(player.position, nextPosition)
            
            if (!isValidPosition(nextPosition)) {
                // Next waypoint is blocked, clear path
                playerPaths.remove(characterId)
                return@withLock null
            }
            
            player.position = nextPosition
            player.direction = direction
            
            // If reached end of path, clear it
            if (currentIndex + 1 >= path.size - 1) {
                playerPaths.remove(characterId)
            }
            
            return@withLock MoveResult.Success(nextPosition, direction, path)
        }
    }
    
    /**
     * Calculate direction string from two positions
     */
    private fun calculateDirection(from: Position, to: Position): String {
        val dx = to.x - from.x
        val dy = to.y - from.y
        
        return when {
            kotlin.math.abs(dy) > kotlin.math.abs(dx) -> {
                if (dy < 0) "up" else "down"
            }
            else -> {
                if (dx < 0) "left" else "right"
            }
        }
    }
    
    /**
     * Result of a movement operation
     */
    sealed class MoveResult {
        data class Success(
            val position: Position,
            val direction: String,
            val path: List<Position>
        ) : MoveResult()
        
        data class Error(val message: String) : MoveResult()
    }
    
    /**
     * Get all NPCs (static from map data)
     */
    fun getNPCs(): List<NPC> {
        return mapData.npcs.map { npcData ->
            NPC(
                id = npcData.id,
                name = npcData.name,
                position = npcData.position,
                type = npcData.type
            )
        }
    }
    
    /**
     * Get all monsters
     */
    fun getAllMonsters(): List<Monster> {
        return monsters.values.toList()
    }
    
    /**
     * Add a monster
     */
    suspend fun addMonster(monster: Monster) {
        mutex.withLock {
            monsters[monster.id] = monster
        }
    }
    
    /**
     * Remove a monster
     */
    suspend fun removeMonster(monsterId: String) {
        mutex.withLock {
            monsters.remove(monsterId)
        }
    }
    
    /**
     * Validate position is within map bounds and not colliding
     */
    private fun isValidPosition(position: Position): Boolean {
        // Check bounds
        if (position.x < 0 || position.y < 0 || 
            position.x >= mapData.width || position.y >= mapData.height) {
            return false
        }
        
        // Check collision map
        return collisionMap.isWalkable(position.x, position.y)
    }
    
    /**
     * Get a random spawn point
     */
    fun getSpawnPosition(): Position {
        return if (mapData.spawnPoints.isNotEmpty()) {
            mapData.spawnPoints.random()
        } else {
            Position(mapData.width / 2, mapData.height / 2)
        }
    }
}

