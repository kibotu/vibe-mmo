package net.kibotu.ro.zone.websocket

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import net.kibotu.ro.zone.model.*
import net.kibotu.ro.zone.service.ZoneService
import net.kibotu.ro.zone.service.ValidationResult as SessionValidationResult
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class WebSocketHandler(
    private val zoneService: ZoneService
) {
    // Map: instanceId -> characterId -> WebSocketSession
    private val connections = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Shared JSON instance for serialization
    private val json = Json { 
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        serializersModule = SerializersModule {
            polymorphic(ClientMessage::class) {
                subclass(ClientMessage.MoveTo::class)
                subclass(ClientMessage.MoveDirection::class)
                subclass(ClientMessage.Chat::class)
            }
            polymorphic(ServerMessage::class) {
                subclass(ServerMessage.ZoneState::class)
                subclass(ServerMessage.PlayerJoined::class)
                subclass(ServerMessage.PlayerLeft::class)
                subclass(ServerMessage.Movement::class)
                subclass(ServerMessage.Chat::class)
                subclass(ServerMessage.Error::class)
            }
        }
    }
    
    fun setupRoutes(routing: Routing) {
        routing.webSocket("/ws/zone/{instanceId}") {
            val instanceId = call.parameters["instanceId"] ?: run {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing instance ID"))
                return@webSocket
            }
            
            // Get session token and character ID from headers
            val authHeader = call.request.headers["Authorization"]
            val characterIdHeader = call.request.headers["Character-Id"]
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing Authorization header"))
                return@webSocket
            }
            
            if (characterIdHeader == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing Character-Id header"))
                return@webSocket
            }
            
            val sessionToken = authHeader.removePrefix("Bearer ").trim()
            val characterId = characterIdHeader.trim()
            
            // Validate session
            val sessionValidation = zoneService.sessionValidationService.validateSession(sessionToken)
            if (sessionValidation !is SessionValidationResult.Valid) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid session"))
                return@webSocket
            }
            
            // Get zone instance
            val zoneInstance = zoneService.getZoneInstance(instanceId)
            if (zoneInstance == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Zone instance not found"))
                return@webSocket
            }
            
            // Verify player is in zone
            val player = zoneInstance.getPlayer(characterId)
            if (player == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Player not in zone"))
                return@webSocket
            }
            
            // Register connection
            val instanceConnections = connections.getOrPut(instanceId) { ConcurrentHashMap() }
            instanceConnections[characterId] = this
            
            try {
                // Send initial zone state
                sendZoneState(this, zoneInstance, player)
                
                // Broadcast player joined to others
                broadcastPlayerJoined(zoneInstance, player, characterId)
                
                // Start movement update loop for this player
                val movementJob = scope.launch {
                    while (isActive) {
                        delay(100) // Update movement every 100ms (10 updates per second)
                        val result = zoneInstance.continuePath(characterId)
                        if (result != null) {
                            when (result) {
                                is net.kibotu.ro.zone.model.ZoneInstance.MoveResult.Success -> {
                                    val updatedPlayer = zoneInstance.getPlayer(characterId)
                                    if (updatedPlayer != null) {
                                        val movementMessage = ServerMessage.Movement(
                                            characterId = updatedPlayer.characterId,
                                            position = updatedPlayer.position,
                                            direction = updatedPlayer.direction
                                        )
                                        try {
                                            sendMessage(movementMessage)
                                            broadcastMovement(zoneInstance, updatedPlayer, updatedPlayer.position, updatedPlayer.direction, characterId)
                                        } catch (e: Exception) {
                                            // Connection closed, stop movement updates
                                            break
                                        }
                                    }
                                }
                                is net.kibotu.ro.zone.model.ZoneInstance.MoveResult.Error -> {
                                    // Path error, stop movement
                                    break
                                }
                            }
                        }
                    }
                }
                
                // Handle incoming messages
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        handleMessage(this, frame.readText(), zoneInstance, player, characterId)
                    } else if (frame is Frame.Close) {
                        break
                    }
                }
                
                // Cancel movement updates when connection closes
                movementJob.cancel()
            } catch (e: Exception) {
                // Log error but don't print stack trace in production
            } finally {
                // Cleanup
                instanceConnections.remove(characterId)
                if (instanceConnections.isEmpty()) {
                    connections.remove(instanceId)
                }
                zoneService.leaveZone(instanceId, characterId)
                broadcastPlayerLeft(zoneInstance, characterId, characterId)
            }
        }
    }
    
    private suspend fun sendZoneState(session: WebSocketSession, zoneInstance: net.kibotu.ro.zone.model.ZoneInstance, currentPlayer: net.kibotu.ro.zone.model.Player) {
        val players = zoneInstance.getAllPlayers().map { player ->
            PlayerDTO(
                characterId = player.characterId,
                name = player.name,
                position = player.position,
                direction = player.direction
            )
        }
        
        val npcs = zoneInstance.getNPCs().map { npc ->
            NPCDTO(
                id = npc.id,
                name = npc.name,
                position = npc.position,
                type = npc.type
            )
        }
        
        val monsters = zoneInstance.getAllMonsters().map { monster ->
            MonsterDTO(
                id = monster.id,
                type = monster.type,
                position = monster.position,
                health = monster.health
            )
        }
        
        val message = ServerMessage.ZoneState(players, npcs, monsters)
        session.sendMessage(message)
    }
    
    private suspend fun handleMessage(
        session: WebSocketSession,
        text: String,
        zoneInstance: net.kibotu.ro.zone.model.ZoneInstance,
        player: net.kibotu.ro.zone.model.Player,
        characterId: String
    ) {
        try {
            val message = json.decodeFromString(ClientMessage.serializer(), text)
            
            when (message) {
                is ClientMessage.MoveTo -> {
                    val result = zoneInstance.movePlayerTo(characterId, message.targetPosition)
                    when (result) {
                        is net.kibotu.ro.zone.model.ZoneInstance.MoveResult.Success -> {
                            val updatedPlayer = zoneInstance.getPlayer(characterId)
                            if (updatedPlayer != null) {
                                val movementMessage = ServerMessage.Movement(
                                    characterId = updatedPlayer.characterId,
                                    position = updatedPlayer.position,
                                    direction = updatedPlayer.direction
                                )
                                session.sendMessage(movementMessage)
                                broadcastMovement(zoneInstance, updatedPlayer, updatedPlayer.position, updatedPlayer.direction, characterId)
                            }
                        }
                        is net.kibotu.ro.zone.model.ZoneInstance.MoveResult.Error -> {
                            session.sendMessage(ServerMessage.Error(result.message))
                        }
                    }
                }
                is ClientMessage.MoveDirection -> {
                    val result = zoneInstance.movePlayerDirection(characterId, message.direction)
                    when (result) {
                        is net.kibotu.ro.zone.model.ZoneInstance.MoveResult.Success -> {
                            val updatedPlayer = zoneInstance.getPlayer(characterId)
                            if (updatedPlayer != null) {
                                val movementMessage = ServerMessage.Movement(
                                    characterId = updatedPlayer.characterId,
                                    position = updatedPlayer.position,
                                    direction = updatedPlayer.direction
                                )
                                session.sendMessage(movementMessage)
                                broadcastMovement(zoneInstance, updatedPlayer, updatedPlayer.position, updatedPlayer.direction, characterId)
                            }
                        }
                        is net.kibotu.ro.zone.model.ZoneInstance.MoveResult.Error -> {
                            session.sendMessage(ServerMessage.Error(result.message))
                        }
                    }
                }
                is ClientMessage.Chat -> {
                    val currentPlayer = zoneInstance.getPlayer(characterId) ?: player
                    broadcastChat(zoneInstance, currentPlayer, message.message, characterId)
                }
            }
        } catch (e: Exception) {
            session.sendMessage(ServerMessage.Error("Invalid message format: ${e.message}"))
        }
    }
    
    private suspend fun broadcastPlayerJoined(
        zoneInstance: net.kibotu.ro.zone.model.ZoneInstance,
        player: net.kibotu.ro.zone.model.Player,
        excludeCharacterId: String
    ) {
        val message = ServerMessage.PlayerJoined(
            characterId = player.characterId,
            name = player.name,
            position = player.position
        )
        broadcastToOthers(zoneInstance.id, message, excludeCharacterId)
    }
    
    private suspend fun broadcastPlayerLeft(
        zoneInstance: net.kibotu.ro.zone.model.ZoneInstance,
        characterId: String,
        excludeCharacterId: String
    ) {
        val message = ServerMessage.PlayerLeft(characterId)
        broadcastToOthers(zoneInstance.id, message, excludeCharacterId)
    }
    
    private suspend fun broadcastMovement(
        zoneInstance: net.kibotu.ro.zone.model.ZoneInstance,
        player: net.kibotu.ro.zone.model.Player,
        position: Position,
        direction: String,
        excludeCharacterId: String
    ) {
        val message = ServerMessage.Movement(
            characterId = player.characterId,
            position = position,
            direction = direction
        )
        broadcastToOthers(zoneInstance.id, message, excludeCharacterId)
    }
    
    private suspend fun broadcastChat(
        zoneInstance: net.kibotu.ro.zone.model.ZoneInstance,
        player: net.kibotu.ro.zone.model.Player,
        message: String,
        excludeCharacterId: String
    ) {
        val serverMessage = ServerMessage.Chat(
            characterId = player.characterId,
            name = player.name,
            message = message
        )
        // Broadcast to all including sender
        broadcastToAll(zoneInstance.id, serverMessage)
    }
    
    private suspend fun broadcastToOthers(
        instanceId: String,
        message: ServerMessage,
        excludeCharacterId: String
    ) {
        val instanceConnections = connections[instanceId] ?: return
        val messageText = json.encodeToString(ServerMessage.serializer(), message)
        
        instanceConnections.forEach { (characterId, session) ->
            if (characterId != excludeCharacterId) {
                try {
                    session.send(Frame.Text(messageText))
                } catch (e: Exception) {
                    // Connection might be closed
                    instanceConnections.remove(characterId)
                }
            }
        }
    }
    
    private suspend fun broadcastToAll(
        instanceId: String,
        message: ServerMessage
    ) {
        val instanceConnections = connections[instanceId] ?: return
        val messageText = json.encodeToString(ServerMessage.serializer(), message)
        
        instanceConnections.forEach { (characterId, session) ->
            try {
                session.send(Frame.Text(messageText))
            } catch (e: Exception) {
                // Connection might be closed
                instanceConnections.remove(characterId)
            }
        }
    }
    
    private suspend fun WebSocketSession.sendMessage(message: ServerMessage) {
        val text = json.encodeToString(ServerMessage.serializer(), message)
        send(Frame.Text(text))
    }
    
    /**
     * Close a specific player's WebSocket connection (e.g., when kicked)
     */
    suspend fun closePlayerConnection(instanceId: String, characterId: String) {
        val instanceConnections = connections[instanceId] ?: return
        val session = instanceConnections[characterId] ?: return
        
        try {
            session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Kicked from zone"))
        } catch (e: Exception) {
            // Connection might already be closed
        } finally {
            instanceConnections.remove(characterId)
            if (instanceConnections.isEmpty()) {
                connections.remove(instanceId)
            }
        }
    }
}

