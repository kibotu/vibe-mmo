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
    
    private var receiveJob: Job? = null
    
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
                
                // Start receiving messages
                receiveJob = CoroutineScope(Dispatchers.Default).launch {
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val message = json.decodeFromString(ServerMessage.serializer(), text)
                                    _messages.value = message
                                } catch (e: Exception) {
                                    // Handle deserialization error
                                    println("Failed to deserialize message: $text, error: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("WebSocket receive error: ${e.message}")
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendMove(direction: String, position: Position) {
        val currentSession = session ?: return
        try {
            val message = ClientMessage.Move(direction, position)
            val jsonString = json.encodeToString(ClientMessage.serializer(), message)
            currentSession.send(Frame.Text(jsonString))
        } catch (e: Exception) {
            println("Failed to send move message: ${e.message}")
        }
    }
    
    suspend fun sendChat(message: String) {
        val currentSession = session ?: return
        try {
            val chatMessage = ClientMessage.Chat(message)
            val jsonString = json.encodeToString(ClientMessage.serializer(), chatMessage)
            currentSession.send(Frame.Text(jsonString))
        } catch (e: Exception) {
            println("Failed to send chat message: ${e.message}")
        }
    }
    
    suspend fun disconnect() {
        receiveJob?.cancel()
        session?.close()
        session = null
        client?.close()
        client = null
        _messages.value = null
    }
}

