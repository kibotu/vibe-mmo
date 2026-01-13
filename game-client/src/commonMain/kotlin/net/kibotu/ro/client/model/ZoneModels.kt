package net.kibotu.ro.client.model

import kotlinx.serialization.Serializable

@Serializable
data class JoinZoneRequest(
    val characterId: String,
    val zoneName: String
)

@Serializable
data class Position(
    val x: Int,
    val y: Int
)

@Serializable
data class JoinZoneResponse(
    val zoneInstanceId: String,
    val websocketUrl: String,
    val spawnPosition: Position
)

@Serializable
data class PlayerDTO(
    val characterId: String,
    val name: String,
    val position: Position,
    val direction: String
)

@Serializable
data class NPCDTO(
    val id: String,
    val name: String,
    val position: Position,
    val type: String
)

@Serializable
data class MonsterDTO(
    val id: String,
    val type: String,
    val position: Position,
    val health: Int
)

// WebSocket Messages (Client → Server)
@kotlinx.serialization.Serializable
sealed class ClientMessage {
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("move_to")
    data class MoveTo(
        val targetPosition: Position  // Target position for click-to-move pathfinding
    ) : ClientMessage()
    
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("move_direction")
    data class MoveDirection(
        val direction: String  // "up", "down", "left", "right" for keyboard movement
    ) : ClientMessage()
    
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("chat")
    data class Chat(
        val message: String
    ) : ClientMessage()
}

// WebSocket Messages (Server → Client)
@kotlinx.serialization.Serializable
sealed class ServerMessage {
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("zone_state")
    data class ZoneState(
        val players: List<PlayerDTO>,
        val npcs: List<NPCDTO>,
        val monsters: List<MonsterDTO>
    ) : ServerMessage()
    
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("player_joined")
    data class PlayerJoined(
        val characterId: String,
        val name: String,
        val position: Position
    ) : ServerMessage()
    
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("player_left")
    data class PlayerLeft(
        val characterId: String
    ) : ServerMessage()
    
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("movement")
    data class Movement(
        val characterId: String,
        val position: Position,
        val direction: String
    ) : ServerMessage()
    
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("chat")
    data class Chat(
        val characterId: String,
        val name: String,
        val message: String
    ) : ServerMessage()
    
    @kotlinx.serialization.Serializable
    @kotlinx.serialization.SerialName("error")
    data class Error(
        val message: String
    ) : ServerMessage()
}

// Server Map Data (matches zone server MapData structure)
@Serializable
data class ServerMapData(
    val name: String,
    val displayName: String,
    val description: String? = null,
    val width: Int,
    val height: Int,
    val tileSize: Int = 32,
    val collisionMap: ServerCollisionMapData,
    val heightMap: ServerHeightMapData? = null,
    val spawnPoints: List<Position>,
    val npcs: List<ServerNPCData>,
    val monsterSpawns: List<ServerMonsterSpawnData>? = null,
    val version: String = "1.0",
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class ServerHeightMapData(
    val width: Int,
    val height: Int,
    val data: String,  // Base64-encoded FloatArray
    val maxHeight: Float = 2f
)

@Serializable
data class ServerCollisionMapData(
    val width: Int,
    val height: Int,
    val data: String  // Base64-encoded bitmap
)

@Serializable
data class ServerNPCData(
    val id: String,
    val name: String,
    val position: Position,
    val type: String,
    val direction: String = "down",
    val properties: Map<String, String> = emptyMap()
)

@Serializable
data class ServerMonsterSpawnData(
    val id: String,
    val monsterType: String,
    val position: Position,
    val respawnTime: Int = 5000,
    val maxCount: Int = 1,
    val spawnRadius: Int = 0
)

