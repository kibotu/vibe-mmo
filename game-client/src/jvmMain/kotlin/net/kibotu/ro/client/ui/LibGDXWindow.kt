package net.kibotu.ro.client.ui

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import kotlinx.coroutines.*
import net.kibotu.ro.client.game.CollisionMap
import net.kibotu.ro.client.game.GameScreen
import net.kibotu.ro.client.game.HeightMapDecoder
import net.kibotu.ro.client.game.LibGDXGame
import net.kibotu.ro.client.game.MapData
import net.kibotu.ro.client.model.Position
import net.kibotu.ro.client.model.ServerMapData
import net.kibotu.ro.client.util.ClientLogger
import net.kibotu.ro.client.viewmodel.GameViewModel

/**
 * Manages the LibGDX game window
 */
class LibGDXWindow(
    private val viewModel: GameViewModel,
    private val ownCharacterId: String,
    private val initialPosition: Position
) {
    @Volatile
    private var application: Lwjgl3Application? = null
    private var gameScreen: GameScreen? = null
    private var libGDXGame: LibGDXGame? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Convert server map data to client map data
     */
    private fun convertServerMapData(serverMapData: ServerMapData?): MapData {
        if (serverMapData == null) {
            // Default fallback map data
            return MapData(
                name = "prontera",
                width = 100,
                height = 100,
                tileSize = 32,
                collisionMap = CollisionMap.default(100, 100),
                spawnPoints = listOf(Position(50, 50)),
                heightMap = null,
                maxHeight = 2f
            )
        }
        
        // Convert collision map from server format
        val collisionMap = try {
            CollisionMap.fromBase64(
                serverMapData.collisionMap.data,
                serverMapData.collisionMap.width,
                serverMapData.collisionMap.height
            )
        } catch (e: Exception) {
            ClientLogger.error("Failed to decode collision map: ${e.message}", "LibGDXWindow")
            CollisionMap.default(serverMapData.width, serverMapData.height)
        }
        
        // Decode heightmap if present
        val heightMap = serverMapData.heightMap?.let { heightMapData ->
            try {
                HeightMapDecoder.decodeHeightMap(
                    heightMapData.data,
                    heightMapData.width,
                    heightMapData.height
                )
            } catch (e: Exception) {
                ClientLogger.error("Failed to decode heightmap: ${e.message}", "LibGDXWindow")
                null
            }
        }
        
        return MapData(
            name = serverMapData.name,
            width = serverMapData.width,
            height = serverMapData.height,
            tileSize = serverMapData.tileSize,
            collisionMap = collisionMap,
            spawnPoints = serverMapData.spawnPoints,
            heightMap = heightMap,
            maxHeight = serverMapData.heightMap?.maxHeight ?: 2f
        )
    }
    
    /**
     * Create and show the LibGDX window
     * Launches in a separate thread to avoid blocking the Compose UI
     */
    fun show() {
        if (application != null) {
            ClientLogger.warn("LibGDX window already shown, ignoring show() call", "LibGDXWindow")
            return // Already shown
        }
        
        ClientLogger.info("Creating LibGDX window for character: $ownCharacterId at position: $initialPosition", "LibGDXWindow")
        
        // Get map data from state
        val serverMapData = viewModel.state.value.mapData
        val mapData = convertServerMapData(serverMapData)
        ClientLogger.info("Using map data: ${mapData.name} (${mapData.width}x${mapData.height})", "LibGDXWindow")
        
        val config = Lwjgl3ApplicationConfiguration().apply {
            setTitle("Ragnarok Online - Game World")
            setWindowedMode(1024, 768)
            setResizable(true)
            useVsync(true)
            setForegroundFPS(60)
        }
        
        ClientLogger.info("Creating GameScreen...", "LibGDXWindow")
        gameScreen = GameScreen(viewModel, ownCharacterId, initialPosition, mapData)
        libGDXGame = LibGDXGame(gameScreen!!)
        
        // Start observing state changes (before launching window)
        ClientLogger.info("Starting state observer...", "LibGDXWindow")
        startStateObserver()
        
        // Launch LibGDX application in a separate thread to avoid blocking Compose UI
        ClientLogger.info("Starting LibGDX application in background thread...", "LibGDXWindow")
        Thread({
            try {
                application = Lwjgl3Application(libGDXGame!!, config)
                ClientLogger.info("LibGDX application thread started", "LibGDXWindow")
            } catch (e: Exception) {
                ClientLogger.error("Failed to start LibGDX application: ${e.message}", "LibGDXWindow")
                e.printStackTrace()
            }
        }, "LibGDX-Thread").apply {
            isDaemon = false // Keep thread alive while window is open
            start()
        }
        
        ClientLogger.info("LibGDX window launch initiated (non-blocking)", "LibGDXWindow")
    }
    
    /**
     * Close the LibGDX window
     */
    fun close() {
        application?.exit()
        application = null
        gameScreen?.dispose()
        gameScreen = null
        libGDXGame = null
        scope.cancel()
    }
    
    /**
     * Start observing ViewModel state and update game screen
     */
    private fun startStateObserver() {
        scope.launch {
            try {
                ClientLogger.info("State observer started, collecting state updates...", "LibGDXWindow")
                viewModel.state.collect { state ->
                    ClientLogger.debug(
                        "State update received: playerPosition=${state.playerPosition}, players=${state.players.size}, npcs=${state.npcs.size}, monsters=${state.monsters.size}",
                        "LibGDXWindow"
                    )
                    
                    // Update map data if it changed
                    if (state.mapData != null && gameScreen != null) {
                        val newMapData = convertServerMapData(state.mapData)
                        // Note: GameScreen would need to be updated/recreated with new map data
                        // For now, we'll use the initial map data, but log the update
                        ClientLogger.debug("Map data updated: ${newMapData.name}", "LibGDXWindow")
                    }
                    
                    gameScreen?.updateGameState(
                        state.playerPosition,
                        state.players,
                        state.npcs,
                        state.monsters
                    )
                }
            } catch (e: Exception) {
                ClientLogger.error("State observer error: ${e.message}", "LibGDXWindow")
                // Handle cancellation or errors gracefully
            }
        }
    }
}

