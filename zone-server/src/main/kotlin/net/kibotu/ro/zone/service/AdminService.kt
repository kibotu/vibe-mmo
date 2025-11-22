package net.kibotu.ro.zone.service

import kotlinx.serialization.Serializable
import net.kibotu.ro.zone.model.ZoneInstance
import net.kibotu.ro.zone.websocket.WebSocketHandler
import java.time.Instant
import java.util.*

class AdminService(
    private val zoneService: ZoneService,
    private val webSocketHandler: WebSocketHandler
) {
    /**
     * Get all zone instances
     */
    fun getAllZoneInstances(): List<ZoneInstanceInfo> {
        // Access zone instances through reflection or add a getter method
        // For now, we'll need to add a method to ZoneService to expose instances
        return zoneService.getAllZoneInstances().map { instance ->
            val players = instance.getAllPlayers()
            val npcs = instance.getNPCs()
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
                npcCount = npcs.size,
                npcs = npcs.map { npc ->
                    NPCInfo(
                        id = npc.id,
                        name = npc.name,
                        position = PositionInfo(npc.position.x, npc.position.y),
                        type = npc.type
                    )
                },
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
     * Get minimap data for a zone instance
     */
    fun getMinimapData(instanceId: String): MinimapData? {
        val instance = zoneService.getAllZoneInstances().find { it.id == instanceId } ?: return null
        
        return MinimapData(
            zoneName = instance.zoneName,
            mapWidth = instance.mapData.width,
            mapHeight = instance.mapData.height,
            tileSize = instance.mapData.tileSize,
            players = instance.getAllPlayers().map { player ->
                MinimapPlayerInfo(
                    characterId = player.characterId,
                    name = player.name,
                    position = PositionInfo(player.position.x, player.position.y),
                    direction = player.direction
                )
            },
            npcs = instance.getNPCs().map { npc ->
                MinimapNPCInfo(
                    id = npc.id,
                    name = npc.name,
                    position = PositionInfo(npc.position.x, npc.position.y),
                    type = npc.type
                )
            }
        )
    }
    
    /**
     * Load a map into a zone instance (create zone if it doesn't exist)
     */
    suspend fun loadMapIntoZone(zoneName: String): LoadMapResult {
        val instance = zoneService.createZoneInstance(zoneName)
        return if (instance != null) {
            LoadMapResult.Success(
                instanceId = instance.id,
                zoneName = instance.zoneName,
                playerCount = instance.getAllPlayers().size,
                npcCount = instance.getNPCs().size
            )
        } else {
            LoadMapResult.Error("Map not found: $zoneName")
        }
    }
    
    /**
     * Kick a player from a zone
     */
    suspend fun kickPlayer(instanceId: String, characterId: String): Boolean {
        // Close the WebSocket connection first
        webSocketHandler.closePlayerConnection(instanceId, characterId)
        // Then remove the player from the zone
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
    val npcs: List<NPCInfo>,
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
data class NPCInfo(
    val id: String,
    val name: String,
    val position: PositionInfo,
    val type: String
)

@Serializable
data class ZoneServerStatistics(
    val totalZones: Int,
    val totalPlayers: Int,
    val totalNPCs: Int,
    val totalMonsters: Int,
    val zonesByName: Map<String, Int>
)

@Serializable
data class MinimapData(
    val zoneName: String,
    val mapWidth: Int,
    val mapHeight: Int,
    val tileSize: Int,
    val players: List<MinimapPlayerInfo>,
    val npcs: List<MinimapNPCInfo>
)

@Serializable
data class MinimapPlayerInfo(
    val characterId: String,
    val name: String,
    val position: PositionInfo,
    val direction: String
)

@Serializable
data class MinimapNPCInfo(
    val id: String,
    val name: String,
    val position: PositionInfo,
    val type: String
)

@Serializable
sealed class LoadMapResult {
    @Serializable
    data class Success(
        val instanceId: String,
        val zoneName: String,
        val playerCount: Int,
        val npcCount: Int
    ) : LoadMapResult()
    
    @Serializable
    data class Error(val message: String) : LoadMapResult()
}

