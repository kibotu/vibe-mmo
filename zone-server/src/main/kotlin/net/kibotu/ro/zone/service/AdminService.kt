package net.kibotu.ro.zone.service

import kotlinx.serialization.Serializable
import net.kibotu.ro.zone.model.ZoneInstance
import java.time.Instant
import java.util.*

class AdminService(
    private val zoneService: ZoneService
) {
    /**
     * Get all zone instances
     */
    fun getAllZoneInstances(): List<ZoneInstanceInfo> {
        // Access zone instances through reflection or add a getter method
        // For now, we'll need to add a method to ZoneService to expose instances
        return zoneService.getAllZoneInstances().map { instance ->
            val players = instance.getAllPlayers()
            ZoneInstanceInfo(
                id = instance.id,
                zoneName = instance.zoneName,
                playerCount = players.size,
                players = players.map { player ->
                    PlayerInfo(
                        characterId = player.characterId,
                        name = player.name,
                        position = PositionInfo(player.position.x, player.position.y),
                        direction = player.direction
                    )
                },
                npcCount = instance.getNPCs().size,
                monsterCount = instance.getAllMonsters().size
            )
        }
    }
    
    /**
     * Get server statistics
     */
    fun getStatistics(): ZoneServerStatistics {
        val instances = zoneService.getAllZoneInstances()
        val totalPlayers = instances.sumOf { it.getAllPlayers().size }
        val totalNPCs = instances.sumOf { it.getNPCs().size }
        val totalMonsters = instances.sumOf { it.getAllMonsters().size }
        
        val zonesByName = instances.groupBy { it.zoneName }
        
        return ZoneServerStatistics(
            totalZones = instances.size,
            totalPlayers = totalPlayers,
            totalNPCs = totalNPCs,
            totalMonsters = totalMonsters,
            zonesByName = zonesByName.mapValues { it.value.size }
        )
    }
    
    /**
     * Kick a player from a zone
     */
    suspend fun kickPlayer(instanceId: String, characterId: String): Boolean {
        return zoneService.leaveZone(instanceId, characterId)
    }
}

@Serializable
data class ZoneInstanceInfo(
    val id: String,
    val zoneName: String,
    val playerCount: Int,
    val players: List<PlayerInfo>,
    val npcCount: Int,
    val monsterCount: Int
)

@Serializable
data class PlayerInfo(
    val characterId: String,
    val name: String,
    val position: PositionInfo,
    val direction: String
)

@Serializable
data class PositionInfo(
    val x: Int,
    val y: Int
)

@Serializable
data class ZoneServerStatistics(
    val totalZones: Int,
    val totalPlayers: Int,
    val totalNPCs: Int,
    val totalMonsters: Int,
    val zonesByName: Map<String, Int>
)

