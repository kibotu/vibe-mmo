package net.kibotu.ro.client.viewmodel

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import net.kibotu.ro.client.model.*
import net.kibotu.ro.client.network.*
import net.kibotu.ro.client.state.*
import net.kibotu.ro.client.storage.CredentialStorage
import net.kibotu.ro.client.util.ClientLogger

class GameViewModel {
    private val accountClient = AccountClient()
    private val characterClient = CharacterClient()
    private val zoneClient = ZoneClient()
    private val zoneWebSocketClient = ZoneWebSocketClient()
    private val credentialStorage = CredentialStorage()
    
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        // Observe WebSocket messages
        scope.launch {
            zoneWebSocketClient.messages.collect { message ->
                message?.let { handleServerMessage(it) }
            }
        }
        
        // Observe logs and update state
        scope.launch {
            ClientLogger.logs.collect { logs ->
                _state.update { it.copy(logs = logs) }
            }
        }
    }
    
    suspend fun register(email: String, password: String) {
        ClientLogger.info("Attempting to register account: $email", "Account")
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        
        accountClient.register(email, password)
            .onSuccess {
                ClientLogger.info("Registration successful: $email", "Account")
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Registration successful! Please login."
                    )
                }
            }
            .onFailure { e ->
                ClientLogger.error("Registration failed: ${e.message}", "Account")
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Registration failed"
                    )
                }
            }
    }
    
    suspend fun login(email: String, password: String, rememberMe: Boolean = false) {
        ClientLogger.info("Attempting to login: $email", "Account")
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        
        accountClient.login(email, password)
            .onSuccess { response ->
                ClientLogger.info("Login successful: $email (userId: ${response.userId})", "Account")
                // Save credentials if remember me is checked
                if (rememberMe) {
                    credentialStorage.saveCredentials(email, password)
                    ClientLogger.debug("Credentials saved", "Storage")
                } else {
                    credentialStorage.clearCredentials()
                }
                
                _state.update { 
                    it.copy(
                        isLoading = false,
                        sessionToken = response.sessionToken,
                        userId = response.userId,
                        currentScreen = Screen.CharacterSelection
                    )
                }
                scope.launch {
                    loadCharacters()
                }
            }
            .onFailure { e ->
                ClientLogger.error("Login failed: ${e.message}", "Account")
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Login failed"
                    )
                }
            }
    }
    
    fun getSavedEmail(): String? {
        return credentialStorage.getSavedEmail()
    }
    
    fun getSavedPassword(): String? {
        return credentialStorage.getSavedPassword()
    }
    
    fun hasSavedCredentials(): Boolean {
        return credentialStorage.hasSavedCredentials()
    }
    
    suspend fun loadCharacters() {
        val token = _state.value.sessionToken ?: return
        ClientLogger.info("Loading characters...", "Character")

        _state.update { it.copy(isLoading = true) }
        
        characterClient.listCharacters(token)
            .onSuccess { characters ->
                ClientLogger.info("Loaded ${characters.size} character(s)", "Character")
                _state.update {
                    it.copy(
                        isLoading = false,
                        characters = characters
                    )
                }
            }
            .onFailure { e ->
                ClientLogger.error("Failed to load characters: ${e.message}", "Character")
                // Check if session is invalid
                if (e is SessionInvalidException) {
                    ClientLogger.warn("Session invalid, redirecting to login", "Account")
                    handleSessionInvalidation()
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load characters"
                        )
                    }
                }
            }
    }
    
    suspend fun createCharacter(name: String) {
        val token = _state.value.sessionToken ?: return
        
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        
        characterClient.createCharacter(token, name)
            .onSuccess { character ->
                _state.update { 
                    it.copy(
                        isLoading = false,
                        characters = _state.value.characters + character
                    )
                }
            }
            .onFailure { e ->
                // Check if session is invalid
                if (e is SessionInvalidException) {
                    ClientLogger.warn("Session invalid, redirecting to login", "Account")
                    handleSessionInvalidation()
                } else {
                    _state.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to create character"
                        )
                    }
                }
            }
    }
    
    suspend fun selectCharacter(character: CharacterDTO) {
        ClientLogger.info("Selecting character: ${character.name} (${character.id})", "Character")
        _state.update { 
            it.copy(
                selectedCharacter = character,
                isLoading = true,
                errorMessage = null
            )
        }
        
        val token = _state.value.sessionToken ?: return
        
        // Get first available zone
        val zoneName = zoneClient.getAvailableMaps()
            .getOrNull()
            ?.firstOrNull()
            ?: "prontera" // Fallback to default if no maps available
        
        ClientLogger.info("Joining zone: $zoneName", "Zone")
        
        // Fetch map data from server
        val mapDataResult = zoneClient.getMapData(zoneName)
        val mapData = mapDataResult.getOrNull()
        if (mapData != null) {
            ClientLogger.info("Map data loaded: ${mapData.name} (${mapData.width}x${mapData.height})", "Zone")
        } else {
            ClientLogger.warn("Failed to load map data: ${mapDataResult.exceptionOrNull()?.message}", "Zone")
        }
        
        zoneClient.joinZone(token, character.id, zoneName)
            .onSuccess { response ->
                ClientLogger.info("Zone joined: ${response.zoneInstanceId}", "Zone")
                ClientLogger.debug("Spawn position: (${response.spawnPosition.x}, ${response.spawnPosition.y})", "Zone")
                
                // Connect WebSocket
                ClientLogger.info("Connecting to WebSocket: ${response.websocketUrl}", "WebSocket")
                
                // Set up ping callback
                zoneWebSocketClient.setPingCallback { ping ->
                    updatePing(ping)
                }
                
                // Set up disconnection callback
                zoneWebSocketClient.setDisconnectionCallback {
                    ClientLogger.warn("WebSocket disconnected", "WebSocket")
                    handleWebSocketDisconnection()
                }
                
                val wsResult = zoneWebSocketClient.connect(
                    response.websocketUrl,
                    token,
                    character.id
                )
                
                if (wsResult.isSuccess) {
                    ClientLogger.info("WebSocket connected successfully", "WebSocket")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            zoneInstanceId = response.zoneInstanceId,
                            zoneName = zoneName,
                            playerPosition = response.spawnPosition,
                            mapData = mapData,
                            currentScreen = Screen.GameWorld(character.id, character.name)
                        )
                    }
                } else {
                    ClientLogger.error("WebSocket connection failed: ${wsResult.exceptionOrNull()?.message}", "WebSocket")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to connect to game world"
                        )
                    }
                }
            }
            .onFailure { e ->
                ClientLogger.error("Failed to join zone: ${e.message}", "Zone")
                // Check if session is invalid
                if (e is SessionInvalidException) {
                    ClientLogger.warn("Session invalid, redirecting to login", "Account")
                    handleSessionInvalidation()
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to join zone"
                        )
                    }
                }
            }
    }
    
    fun movePlayer(direction: String) {
        ClientLogger.info("GameViewModel.movePlayer() called with direction: $direction", "Movement")
        
        val currentPosition = _state.value.playerPosition
        val selectedCharacter = _state.value.selectedCharacter
        
        ClientLogger.debug("Current state: playerPosition=$currentPosition, selectedCharacter=${selectedCharacter?.id}", "Movement")
        
        if (currentPosition == null) {
            ClientLogger.warn("movePlayer() called but playerPosition is null. Cannot move.", "Movement")
            return
        }
        
        if (selectedCharacter == null) {
            ClientLogger.warn("movePlayer() called but selectedCharacter is null. Cannot move.", "Movement")
            return
        }
        
        ClientLogger.debug("Current position: (${currentPosition.x}, ${currentPosition.y}), Character: ${selectedCharacter.name}", "Movement")
        
        val newPosition = when (direction) {
            "up" -> Position(currentPosition.x, currentPosition.y - 1)
            "down" -> Position(currentPosition.x, currentPosition.y + 1)
            "left" -> Position(currentPosition.x - 1, currentPosition.y)
            "right" -> Position(currentPosition.x + 1, currentPosition.y)
            else -> {
                ClientLogger.warn("Unknown direction: $direction", "Movement")
                currentPosition
            }
        }
        
        // Don't update if position hasn't changed
        if (newPosition == currentPosition) {
            ClientLogger.debug("Position unchanged, skipping update", "Movement")
            return
        }
        
        // Check collision using map data from state
        val mapData = _state.value.mapData
        if (mapData != null) {
            // Check if new position is within bounds
            val collisionMapData = mapData.collisionMap
            if (newPosition.x < 0 || newPosition.x >= collisionMapData.width ||
                newPosition.y < 0 || newPosition.y >= collisionMapData.height) {
                ClientLogger.debug("Movement blocked: position out of bounds: ($newPosition)", "Movement")
                return
            }
            
            // Note: Full collision checking requires converting base64 collision data
            // For now, we'll let the server validate movement
            // TODO: Convert ServerCollisionMapData to CollisionMap for client-side validation
        }
        
        ClientLogger.info("Sending movement command to server: direction=$direction", "Movement")
        
        // Send movement command to server (server will handle pathfinding and validation)
        scope.launch {
            try {
                zoneWebSocketClient.sendMoveDirection(direction)
                ClientLogger.debug("Movement command sent to server successfully", "Movement")
            } catch (e: Exception) {
                ClientLogger.error("Failed to send movement command to server: ${e.message}", "Movement")
            }
        }
    }
    
    /**
     * Move player toward a target position (for click-to-move).
     * Sends move command to server - server handles pathfinding.
     */
    fun movePlayerToPosition(targetPosition: Position) {
        val currentPosition = _state.value.playerPosition
        val selectedCharacter = _state.value.selectedCharacter
        
        if (currentPosition == null || selectedCharacter == null) {
            ClientLogger.warn("movePlayerToPosition() called but playerPosition or selectedCharacter is null", "Movement")
            return
        }
        
        // If already at target, do nothing
        if (currentPosition == targetPosition) {
            ClientLogger.debug("Already at target position", "Movement")
            return
        }
        
        ClientLogger.info(
            "Sending move-to command to server: target=($targetPosition) from current position ($currentPosition)",
            "Movement"
        )
        
        // Send move-to command to server (server will handle pathfinding)
        scope.launch {
            try {
                zoneWebSocketClient.sendMoveTo(targetPosition)
                ClientLogger.debug("Move-to command sent to server successfully", "Movement")
            } catch (e: Exception) {
                ClientLogger.error("Failed to send move-to command to server: ${e.message}", "Movement")
            }
        }
    }
    
    fun sendChat(message: String) {
        scope.launch {
            zoneWebSocketClient.sendChat(message)
        }
    }
    
    private fun handleServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.ZoneState -> {
                ClientLogger.debug("Received zone state: ${message.players.size} players, ${message.npcs.size} NPCs, ${message.monsters.size} monsters", "WebSocket")
                _state.update { state ->
                    val playersMap = message.players.associateBy { it.characterId }
                    val selectedCharacterId = state.selectedCharacter?.id
                    
                    // Sync own position from zone state if available
                    val ownPlayerInZone = selectedCharacterId?.let { playersMap[it] }
                    val syncedPlayerPosition = ownPlayerInZone?.position ?: state.playerPosition
                    
                    state.copy(
                        players = playersMap,
                        npcs = message.npcs.associateBy { it.id },
                        monsters = message.monsters.associateBy { it.id },
                        playerPosition = syncedPlayerPosition
                    )
                }
            }
            is ServerMessage.PlayerJoined -> {
                ClientLogger.info("Player joined: ${message.name} (${message.characterId})", "WebSocket")
                val player = PlayerDTO(
                    characterId = message.characterId,
                    name = message.name,
                    position = message.position,
                    direction = "down"
                )
                _state.update {
                    it.copy(players = it.players + (message.characterId to player))
                }
            }
            is ServerMessage.PlayerLeft -> {
                ClientLogger.info("Player left: ${message.characterId}", "WebSocket")
                _state.update {
                    it.copy(players = it.players - message.characterId)
                }
            }
            is ServerMessage.Movement -> {
                _state.update { state ->
                    val isOwnCharacter = state.selectedCharacter?.id == message.characterId
                    
                    val existingPlayer = state.players[message.characterId]
                    val updatedPlayer = existingPlayer?.copy(
                        position = message.position,
                        direction = message.direction
                    ) ?: PlayerDTO(
                        characterId = message.characterId,
                        name = message.characterId,
                        position = message.position,
                        direction = message.direction
                    )
                    
                    state.copy(
                        players = state.players + (message.characterId to updatedPlayer),
                        playerPosition = if (isOwnCharacter) message.position else state.playerPosition
                    )
                }
            }
            is ServerMessage.Chat -> {
                ClientLogger.info("Chat: ${message.name}: ${message.message}", "Chat")
                _state.update {
                    it.copy(
                        chatMessages = it.chatMessages + "${message.name}: ${message.message}"
                    )
                }
            }
            is ServerMessage.Error -> {
                ClientLogger.error("Server error: ${message.message}", "WebSocket")
                _state.update { state ->
                    state.copy(errorMessage = message.message)
                }
            }
        }
    }
    
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
    
    fun logout() {
        ClientLogger.info("Logging out...", "Account")
        scope.launch {
            zoneWebSocketClient.disconnect()
        }
        _state.value = AppState()
    }
    
    fun clearLogs() {
        ClientLogger.clear()
    }
    
    fun updateFps(fps: Int) {
        _state.update { it.copy(fps = fps) }
    }
    
    fun updatePing(ping: Long) {
        _state.update { it.copy(ping = ping) }
    }
    
    /**
     * Handle session invalidation - navigate back to login
     */
    private fun handleSessionInvalidation() {
        scope.launch {
            zoneWebSocketClient.disconnect()
        }
        _state.update {
            it.copy(
                currentScreen = Screen.Login,
                sessionToken = null,
                userId = null,
                characters = emptyList(),
                selectedCharacter = null,
                zoneInstanceId = null,
                playerPosition = null,
                players = emptyMap(),
                errorMessage = "Session expired. Please login again."
            )
        }
    }
    
    /**
     * Handle WebSocket disconnection - navigate back to character selection
     */
    fun handleWebSocketDisconnection() {
        scope.launch {
            zoneWebSocketClient.disconnect()
        }
        _state.update {
            it.copy(
                currentScreen = Screen.CharacterSelection,
                zoneInstanceId = null,
                playerPosition = null,
                players = emptyMap(),
                npcs = emptyMap(),
                monsters = emptyMap(),
                chatMessages = emptyList(),
                errorMessage = "Disconnected from zone server"
            )
        }
    }
    
    fun cleanup() {
        scope.cancel()
        accountClient.close()
        characterClient.close()
        zoneClient.close()
        scope.launch {
            zoneWebSocketClient.disconnect()
        }
    }
}

