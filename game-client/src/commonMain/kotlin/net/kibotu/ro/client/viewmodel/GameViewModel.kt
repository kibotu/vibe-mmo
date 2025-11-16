package net.kibotu.ro.client.viewmodel

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import net.kibotu.ro.client.model.*
import net.kibotu.ro.client.network.*
import net.kibotu.ro.client.state.*
import net.kibotu.ro.client.storage.CredentialStorage

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
    }
    
    suspend fun register(email: String, password: String) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        
        accountClient.register(email, password)
            .onSuccess {
                _state.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Registration successful! Please login."
                    )
                }
            }
            .onFailure { e ->
                _state.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Registration failed"
                    )
                }
            }
    }
    
    suspend fun login(email: String, password: String, rememberMe: Boolean = false) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        
        accountClient.login(email, password)
            .onSuccess { response ->
                // Save credentials if remember me is checked
                if (rememberMe) {
                    credentialStorage.saveCredentials(email, password)
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
                // Launch loadCharacters in ViewModel's scope to avoid composition scope issues
                scope.launch {
                    loadCharacters()
                }
            }
            .onFailure { e ->
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
        
        _state.update { it.copy(isLoading = true) }
        
        characterClient.listCharacters(token)
            .onSuccess { characters ->
                _state.update { 
                    it.copy(
                        isLoading = false,
                        characters = characters
                    )
                }
            }
            .onFailure { e ->
                _state.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load characters"
                    )
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
                _state.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to create character"
                    )
                }
            }
    }
    
    suspend fun selectCharacter(character: CharacterDTO) {
        _state.update { 
            it.copy(
                selectedCharacter = character,
                isLoading = true,
                errorMessage = null
            )
        }
        
        val token = _state.value.sessionToken ?: return
        
        // Join zone (default zone: "prontera")
        zoneClient.joinZone(token, character.id, "prontera")
            .onSuccess { response ->
                // Connect WebSocket
                val wsResult = zoneWebSocketClient.connect(
                    response.websocketUrl,
                    token,
                    character.id
                )
                
                if (wsResult.isSuccess) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            zoneInstanceId = response.zoneInstanceId,
                            playerPosition = response.spawnPosition,
                            currentScreen = Screen.GameWorld(character.id, character.name)
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to connect to game world"
                        )
                    }
                }
            }
            .onFailure { e ->
                _state.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to join zone"
                    )
                }
            }
    }
    
    fun movePlayer(direction: String) {
        val currentPosition = _state.value.playerPosition ?: return
        val newPosition = when (direction) {
            "up" -> Position(currentPosition.x, currentPosition.y - 1)
            "down" -> Position(currentPosition.x, currentPosition.y + 1)
            "left" -> Position(currentPosition.x - 1, currentPosition.y)
            "right" -> Position(currentPosition.x + 1, currentPosition.y)
            else -> currentPosition
        }
        
        _state.update { it.copy(playerPosition = newPosition) }
        
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
                _state.update {
                    it.copy(
                        players = message.players.associateBy { it.characterId },
                        npcs = message.npcs.associateBy { it.id },
                        monsters = message.monsters.associateBy { it.id }
                    )
                }
            }
            is ServerMessage.PlayerJoined -> {
                val player = PlayerDTO(
                    characterId = message.characterId,
                    name = message.name,
                    position = message.position,
                    direction = "down"
                )
                _state.update {
                    it.copy(
                        players = it.players + (message.characterId to player)
                    )
                }
            }
            is ServerMessage.PlayerLeft -> {
                _state.update {
                    it.copy(
                        players = it.players - message.characterId
                    )
                }
            }
            is ServerMessage.Movement -> {
                _state.update { state ->
                    val player = state.players[message.characterId]
                    if (player != null) {
                        val updatedPlayer = player.copy(
                            position = message.position,
                            direction = message.direction
                        )
                        state.copy(
                            players = state.players + (message.characterId to updatedPlayer),
                            // Update own position if it's our character
                            playerPosition = if (state.selectedCharacter?.id == message.characterId) {
                                message.position
                            } else {
                                state.playerPosition
                            }
                        )
                    } else {
                        state
                    }
                }
            }
            is ServerMessage.Chat -> {
                _state.update {
                    it.copy(
                        chatMessages = it.chatMessages + "${message.name}: ${message.message}"
                    )
                }
            }
            is ServerMessage.Error -> {
                _state.update {
                    it.copy(errorMessage = message.message)
                }
            }
        }
    }
    
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
    
    fun logout() {
        scope.launch {
            zoneWebSocketClient.disconnect()
        }
        // Optionally clear credentials on logout
        // credentialStorage.clearCredentials()
        _state.value = AppState()
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

