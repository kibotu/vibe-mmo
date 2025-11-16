package net.kibotu.ro.zone.model

import kotlinx.serialization.Serializable

// Request DTOs
@Serializable
data class JoinZoneRequest(
    val characterId: String,
    val zoneName: String
)

// Response DTOs
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null
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

// WebSocket Message DTOs
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
    @kotlinx.serialization.SerialName("move")
    data class Move(
        val direction: String,
        val position: Position
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

