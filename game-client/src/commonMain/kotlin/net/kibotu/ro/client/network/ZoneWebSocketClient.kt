package net.kibotu.ro.client.network

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import net.kibotu.ro.client.model.*

class ZoneWebSocketClient {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        serializersModule = SerializersModule {
            polymorphic(ClientMessage::class) {
                subclass(ClientMessage.Move::class, serializer())
                subclass(ClientMessage.Chat::class, serializer())
            }
            polymorphic(ServerMessage::class) {
                subclass(ServerMessage.ZoneState::class, serializer())
                subclass(ServerMessage.PlayerJoined::class, serializer())
                subclass(ServerMessage.PlayerLeft::class, serializer())
                subclass(ServerMessage.Movement::class, serializer())
                subclass(ServerMessage.Chat::class, serializer())
                subclass(ServerMessage.Error::class, serializer())
            }
        }
    }
    
    private var client: HttpClient? = null
    private var session: DefaultWebSocketSession? = null
    private val _messages = MutableStateFlow<ServerMessage?>(null)
    val messages: StateFlow<ServerMessage?> = _messages.asStateFlow()
    
    private var connectionJob: Job? = null
    private val connectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Ping tracking
    private var pingCallback: ((Long) -> Unit)? = null
    private var lastPingTime: Long = 0
    private var pingJob: Job? = null
    private var movementSendTime: Long = 0 // Track when movement was sent
    
    fun setPingCallback(callback: (Long) -> Unit) {
        pingCallback = callback
    }
    
    suspend fun connect(
        url: String,
        sessionToken: String,
        characterId: String
    ): Result<Unit> {
        return try {
            val wsClient = HttpClient {
                install(io.ktor.client.plugins.websocket.WebSockets)
            }
            client = wsClient
            
            // Parse WebSocket URL - use the URL directly from the response
            // The server should provide a full WebSocket URL like ws://localhost:28082/ws/zone/{instanceId}
            val wsUrl = url.replace("http://", "ws://").replace("https://", "wss://")
            
            // Extract host, port, and path from URL
            val protocol = if (wsUrl.startsWith("wss://")) "wss" else "ws"
            val withoutProtocol = wsUrl.removePrefix("ws://").removePrefix("wss://")
            val hostPort = withoutProtocol.substringBefore("/")
            val host = hostPort.substringBefore(":")
            val portStr = hostPort.substringAfter(":", "")
            val port = if (portStr.isNotEmpty()) portStr.toIntOrNull() ?: if (protocol == "wss") 443 else 80
                else if (protocol == "wss") 443 else 80
            val path = "/" + withoutProtocol.substringAfter("/", "")
            
            // Launch WebSocket connection in background coroutine
            connectionJob = connectionScope.launch {
                try {
                    wsClient.webSocket(
                        method = HttpMethod.Get,
                        host = host,
                        port = port,
                        path = path,
                        request = {
                            header("Authorization", "Bearer $sessionToken")
                            header("Character-Id", characterId)
                        }
                    ) {
                        session = this
                        println("WebSocket connection established")
                        
                        // Start ping measurement loop
                        pingJob = connectionScope.launch {
                            while (isActive && session != null) {
                                delay(5000) // Ping every 5 seconds
                                if (session != null) {
                                    lastPingTime = System.currentTimeMillis()
                                    try {
                                        // Send ping frame
                                        session?.send(Frame.Ping(ByteArray(0)))
                                    } catch (e: Exception) {
                                        // Connection might be closed
                                        break
                                    }
                                }
                            }
                        }
                        
                        // Start receiving messages
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    try {
                                        val message = json.decodeFromString(ServerMessage.serializer(), text)
                                        _messages.value = message
                                        
                                        // Measure ping from movement confirmations (more accurate)
                                        if (message is ServerMessage.Movement && movementSendTime > 0) {
                                            val ping = System.currentTimeMillis() - movementSendTime
                                            pingCallback?.invoke(ping)
                                            movementSendTime = 0 // Reset after measurement
                                        }
                                    } catch (e: Exception) {
                                        // Handle deserialization error
                                        println("Failed to deserialize message: $text, error: ${e.message}")
                                    }
                                } else if (frame is Frame.Pong) {
                                    // Measure ping from pong response
                                    if (lastPingTime > 0) {
                                        val ping = System.currentTimeMillis() - lastPingTime
                                        pingCallback?.invoke(ping)
                                        lastPingTime = 0 // Reset after measurement
                                    }
                                } else if (frame is Frame.Close) {
                                    println("WebSocket received close frame")
                                    break
                                }
                            }
                        } catch (e: CancellationException) {
                            println("WebSocket receive cancelled: ${e.message}")
                            throw e
                        } catch (e: Exception) {
                            println("WebSocket receive error: ${e.message}")
                        } finally {
                            println("WebSocket receive loop ended")
                            // Clear session when receive loop ends
                            session = null
                        }
                    }
                } catch (e: CancellationException) {
                    println("WebSocket connection cancelled: ${e.message}")
                    session = null
                    throw e
                } catch (e: Exception) {
                    println("WebSocket connection error: ${e.message}")
                    session = null
                } finally {
                    println("WebSocket connection closed")
                    session = null
                }
            }
            
            // Wait a bit to ensure connection is established
            delay(100)
            
            // Check if connection was established
            if (session == null) {
                connectionJob?.cancel()
                return Result.failure(Exception("Failed to establish WebSocket connection"))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendMove(direction: String, position: Position) {
        val currentSession = session
        if (currentSession == null) {
            println("Failed to send move message: WebSocket session is null")
            return
        }
        
        try {
            // Track when movement is sent for ping measurement
            movementSendTime = System.currentTimeMillis()
            
            val message = ClientMessage.Move(direction, position)
            val jsonString = json.encodeToString(ClientMessage.serializer(), message)
            currentSession.send(Frame.Text(jsonString))
        } catch (e: CancellationException) {
            println("Failed to send move message: Channel was cancelled")
            session = null
        } catch (e: Exception) {
            println("Failed to send move message: ${e.message}")
            // If send fails, the connection might be dead
            if (e.message?.contains("cancelled", ignoreCase = true) == true ||
                e.message?.contains("closed", ignoreCase = true) == true) {
                session = null
            }
        }
    }
    
    suspend fun sendChat(message: String) {
        val currentSession = session
        if (currentSession == null) {
            println("Failed to send chat message: WebSocket session is null")
            return
        }
        
        try {
            val chatMessage = ClientMessage.Chat(message)
            val jsonString = json.encodeToString(ClientMessage.serializer(), chatMessage)
            currentSession.send(Frame.Text(jsonString))
        } catch (e: CancellationException) {
            println("Failed to send chat message: Channel was cancelled")
            session = null
        } catch (e: Exception) {
            println("Failed to send chat message: ${e.message}")
            // If send fails, the connection might be dead
            if (e.message?.contains("cancelled", ignoreCase = true) == true ||
                e.message?.contains("closed", ignoreCase = true) == true) {
                session = null
            }
        }
    }
    
    suspend fun disconnect() {
        println("Disconnecting WebSocket...")
        pingJob?.cancel()
        connectionJob?.cancel()
        pingCallback = null
        try {
            session?.close()
        } catch (e: Exception) {
            println("Error closing session: ${e.message}")
        }
        session = null
        try {
            client?.close()
        } catch (e: Exception) {
            println("Error closing client: ${e.message}")
        }
        client = null
        _messages.value = null
        println("WebSocket disconnected")
    }
}

