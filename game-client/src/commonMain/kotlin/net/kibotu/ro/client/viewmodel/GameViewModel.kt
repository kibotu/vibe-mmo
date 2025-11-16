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
        
        // Join zone (default zone: "prontera")
        ClientLogger.info("Joining zone: prontera", "Zone")
        zoneClient.joinZone(token, character.id, "prontera")
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
                            playerPosition = response.spawnPosition,
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
        ClientLogger.info("movePlayer() called with direction: $direction", "Movement")
        
        val currentPosition = _state.value.playerPosition
        val selectedCharacter = _state.value.selectedCharacter
        
        if (currentPosition == null) {
            ClientLogger.warn("movePlayer() called but playerPosition is null", "Movement")
            return
        }
        
        if (selectedCharacter == null) {
            ClientLogger.warn("movePlayer() called but selectedCharacter is null", "Movement")
            return
        }
        
        ClientLogger.debug("Current position: (${currentPosition.x}, ${currentPosition.y}), Character: ${selectedCharacter.name}", "Movement")
        
        val newPosition = when (direction) {
            "up" -> Position(currentPosition.x, currentPosition.y - 1)
            "down" -> Position(currentPosition.x, currentPosition.y + 1)
            "left" -> Position(currentPosition.x - 1, currentPosition.y)
            "right" -> Position(currentPosition.x + 1, currentPosition.y)
            else -> currentPosition
        }
        
        // Don't update if position hasn't changed
        if (newPosition == currentPosition) return
        
        ClientLogger.debug("Moving player: $direction from (${currentPosition.x}, ${currentPosition.y}) to (${newPosition.x}, ${newPosition.y})", "Movement")
        
        // Optimistic update: update local state immediately for responsive UI
        _state.update { state ->
            // Update own position
            val updatedState = state.copy(playerPosition = newPosition)
            
            // Also update in players map if we're tracking ourselves
            val currentPlayer = state.players[selectedCharacter.id]
            if (currentPlayer != null) {
                updatedState.copy(
                    players = state.players + (selectedCharacter.id to currentPlayer.copy(
                        position = newPosition,
                        direction = direction
                    ))
                )
            } else {
                updatedState
            }
        }
        
        // Send movement to server
        scope.launch {
            zoneWebSocketClient.sendMove(direction, newPosition)
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

