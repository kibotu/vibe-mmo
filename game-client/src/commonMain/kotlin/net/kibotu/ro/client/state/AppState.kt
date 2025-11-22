package net.kibotu.ro.client.state

import net.kibotu.ro.client.model.*
import net.kibotu.ro.client.util.LogEntry

sealed class Screen {
    object Login : Screen()
    object CharacterSelection : Screen()
    data class GameWorld(val characterId: String, val characterName: String) : Screen()
}

data class AppState(
    val currentScreen: Screen = Screen.Login,
    val sessionToken: String? = null,
    val userId: String? = null,
    val characters: List<CharacterDTO> = emptyList(),
    val selectedCharacter: CharacterDTO? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    // Game world state
    val zoneInstanceId: String? = null,
    val zoneName: String? = null,
    val playerPosition: Position? = null,
    val players: Map<String, PlayerDTO> = emptyMap(),
    val npcs: Map<String, NPCDTO> = emptyMap(),
    val monsters: Map<String, MonsterDTO> = emptyMap(),
    val chatMessages: List<String> = emptyList(),
    val mapData: ServerMapData? = null, // Map data from zone server
    // Terminal logs
    val logs: List<LogEntry> = emptyList(),
    // Performance metrics
    val fps: Int = 0,
    val ping: Long = 0 // milliseconds
)

