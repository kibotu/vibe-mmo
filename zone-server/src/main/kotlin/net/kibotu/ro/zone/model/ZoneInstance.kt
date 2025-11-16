package net.kibotu.ro.zone.model

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

data class MapData(
    val name: String,
    val width: Int,
    val height: Int,
    val collisionMap: String? = null, // Base64 encoded bitmap or null
    val spawnPoints: List<Position>,
    val npcs: List<NPC>
)

class ZoneInstance(
    val id: String,
    val zoneName: String,
    val mapData: MapData
) {
    private val players = ConcurrentHashMap<String, Player>()
    private val monsters = ConcurrentHashMap<String, Monster>()
    private val mutex = Mutex()
    
    init {
        // Initialize NPCs from map data
        // Monsters will be spawned dynamically
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
     * Update player position
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
     * Get all NPCs (static from map data)
     */
    fun getNPCs(): List<NPC> {
        return mapData.npcs
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
        return position.x >= 0 && position.y >= 0 && 
               position.x < mapData.width && position.y < mapData.height
        // TODO: Check collision map if available
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

