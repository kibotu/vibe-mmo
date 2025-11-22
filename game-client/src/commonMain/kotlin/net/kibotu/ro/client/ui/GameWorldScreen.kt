package net.kibotu.ro.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.kibotu.ro.client.model.*
import net.kibotu.ro.client.viewmodel.GameViewModel
import net.kibotu.ro.client.util.ClientLogger

/**
 * Reference to LibGDX window for cleanup purposes
 */
expect class LibGDXWindowRef {
    fun close()
}

@Composable
fun GameWorldScreen(viewModel: GameViewModel, characterName: String) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Store reference to LibGDX window for cleanup
    var libGDXWindowRef by remember { mutableStateOf<LibGDXWindowRef?>(null) }
    
    // Launch LibGDX window when entering game world
    LaunchedEffect(Unit) {
        ClientLogger.info("GameWorldScreen: LaunchedEffect triggered", "GameWorldScreen")
        ClientLogger.info("GameWorldScreen: Current state - selectedCharacter=${state.selectedCharacter?.id}, playerPosition=${state.playerPosition}", "GameWorldScreen")
        
        val characterId = state.selectedCharacter?.id
        val spawnPosition = state.playerPosition ?: Position(150, 150)
        
        ClientLogger.info("GameWorldScreen: Launching LibGDX window for character: $characterId at position: $spawnPosition", "GameWorldScreen")
        
        if (characterId != null) {
            // Launch LibGDX game window (platform-specific)
            ClientLogger.info("GameWorldScreen: Calling launchLibGDXGameWindow()", "GameWorldScreen")
            val windowRef = launchLibGDXGameWindow(viewModel, characterId, spawnPosition)
            libGDXWindowRef = windowRef
            ClientLogger.info("GameWorldScreen: launchLibGDXGameWindow() returned", "GameWorldScreen")
        } else {
            ClientLogger.error("GameWorldScreen: Character ID is null, cannot launch game window", "GameWorldScreen")
        }
    }
    
    // Cleanup: Close LibGDX window when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            ClientLogger.info("GameWorldScreen: Disposing, closing LibGDX window", "GameWorldScreen")
            libGDXWindowRef?.close()
            libGDXWindowRef = null
        }
    }
    
    // Observe state changes and log them
    LaunchedEffect(state.playerPosition, state.players, state.npcs, state.monsters) {
        ClientLogger.debug(
            "GameWorldScreen: State changed - playerPosition=${state.playerPosition}, players=${state.players.size}, npcs=${state.npcs.size}, monsters=${state.monsters.size}",
            "GameWorldScreen"
        )
    }
    
    // Main UI layout
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top bar with stats and logout button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stats section
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Character: $characterName",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Position: (${state.playerPosition?.x ?: 0}, ${state.playerPosition?.y ?: 0})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "FPS: ${state.fps}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                state.fps >= 60 -> Color(0xFF4CAF50)
                                state.fps >= 30 -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            }
                        )
                        Text(
                            "Ping: ${state.ping}ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                state.ping == 0L -> Color.Gray
                                state.ping < 50 -> Color(0xFF4CAF50)
                                state.ping < 100 -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            }
                        )
                    }
                }
                
                // Logout button
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.logout()
                        }
                    }
                ) {
                    Text("Logout")
                }
            }
        }
        
        // Terminal/log console at the bottom
        Spacer(modifier = Modifier.weight(1f))
        
        Terminal(
            logs = state.logs,
            onClear = { viewModel.clearLogs() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Platform-specific function to launch LibGDX window
expect fun launchLibGDXGameWindow(
    viewModel: GameViewModel,
    characterId: String,
    initialPosition: Position
): LibGDXWindowRef

// Keep the old implementation commented for reference, but we're using LibGDX now
// Old implementation removed - using LibGDX now
/*
@Composable
fun GameWorldScreenOld(viewModel: GameViewModel, characterName: String) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    var keysPressed by remember { mutableStateOf(emptySet<Key>()) }
    val focusRequester = remember { FocusRequester() }
    
    // FPS tracking
    var frameCount by remember { mutableStateOf(0) }
    var lastFpsUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Update FPS every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            val elapsed = now - lastFpsUpdateTime
            if (elapsed > 0) {
                val fps = (frameCount * 1000 / elapsed).toInt()
                viewModel.updateFps(fps)
                frameCount = 0
                lastFpsUpdateTime = now
            }
        }
    }
    
    // Request focus when screen loads
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            ClientLogger.error("Failed to request focus: ${e.message}", "Input")
        }
    }
    
    // Handle continuous movement when keys are held down
    LaunchedEffect(Unit) {
        // Movement rate: move every 150ms while key is held
        while (true) {
            val currentKeys = keysPressed
            if (currentKeys.isNotEmpty()) {
                ClientLogger.debug("Continuous movement: keys pressed=$currentKeys", "Input")
                when {
                    currentKeys.contains(Key.W) || currentKeys.contains(Key.DirectionUp) -> {
                        ClientLogger.debug("Continuous movement: moving up", "Input")
                        viewModel.movePlayer("up")
                    }
                    currentKeys.contains(Key.S) || currentKeys.contains(Key.DirectionDown) -> {
                        ClientLogger.debug("Continuous movement: moving down", "Input")
                        viewModel.movePlayer("down")
                    }
                    currentKeys.contains(Key.A) || currentKeys.contains(Key.DirectionLeft) -> {
                        ClientLogger.debug("Continuous movement: moving left", "Input")
                        viewModel.movePlayer("left")
                    }
                    currentKeys.contains(Key.D) || currentKeys.contains(Key.DirectionRight) -> {
                        ClientLogger.debug("Continuous movement: moving right", "Input")
                        viewModel.movePlayer("right")
                    }
                }
            }
            delay(150) // Delay between movements
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Game Canvas
        var isFocused by remember { mutableStateOf(false) }
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Color(0xFF87CEEB)) // Sky blue background
                .focusable()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    ClientLogger.info("Game area focus changed: isFocused=$isFocused", "Input")
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {
                    focusRequester.requestFocus()
                }
                .onKeyEvent { event ->
                    // Log ALL keyboard events to debug input issues
                    ClientLogger.debug(
                        "Keyboard event received: type=${event.type}, key=${event.key}",
                        "Input"
                    )
                    
                    when {
                        event.type == KeyEventType.KeyDown -> {
                            val key = event.key
                            ClientLogger.info("KeyDown event: key=$key", "Input")

                            // Only track movement keys
                            if (key == Key.W || key == Key.DirectionUp || 
                                key == Key.S || key == Key.DirectionDown ||
                                key == Key.A || key == Key.DirectionLeft ||
                                key == Key.D || key == Key.DirectionRight) {
                                ClientLogger.info("Movement key detected: $key", "Input")
                                keysPressed = keysPressed + key
                                ClientLogger.debug("Keys pressed set updated: $keysPressed", "Input")

                                // Trigger immediate movement on key down
                                val direction = when (key) {
                                    Key.W, Key.DirectionUp -> {
                                        ClientLogger.info("Calling movePlayer('up')", "Input")
                                        "up"
                                    }
                                    Key.S, Key.DirectionDown -> {
                                        ClientLogger.info("Calling movePlayer('down')", "Input")
                                        "down"
                                    }
                                    Key.A, Key.DirectionLeft -> {
                                        ClientLogger.info("Calling movePlayer('left')", "Input")
                                        "left"
                                    }
                                    Key.D, Key.DirectionRight -> {
                                        ClientLogger.info("Calling movePlayer('right')", "Input")
                                        "right"
                                    }
                                    else -> {
                                        ClientLogger.warn("Unexpected key in movement handler: $key", "Input")
                                        null
                                    }
                                }
                                
                                direction?.let { viewModel.movePlayer(it) }
                                true
                            } else {
                                ClientLogger.debug("Non-movement key ignored: $key", "Input")
                                false
                            }
                        }
                        event.type == KeyEventType.KeyUp -> {
                            val key = event.key
                            ClientLogger.info("KeyUp event: key=$key", "Input")
                            keysPressed = keysPressed - event.key
                            ClientLogger.debug("Keys pressed set updated: $keysPressed", "Input")
                            false
                        }
                        else -> {
                            ClientLogger.debug("Other key event type: ${event.type}", "Input")
                            false
                        }
                    }
                }
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
            // Increment frame count for FPS calculation
            frameCount++
            
            val tileSize = 32f
            val cameraX = (state.playerPosition?.x?.toFloat() ?: 0f) * tileSize - size.width / 2
            val cameraY = (state.playerPosition?.y?.toFloat() ?: 0f) * tileSize - size.height / 2
            
            // Draw ground tiles (simple grid)
            val startX = (cameraX / tileSize).toInt() - 1
            val endX = ((cameraX + size.width) / tileSize).toInt() + 1
            val startY = (cameraY / tileSize).toInt() - 1
            val endY = ((cameraY + size.height) / tileSize).toInt() + 1
            
            for (x in startX..endX) {
                for (y in startY..endY) {
                    val screenX = x * tileSize - cameraX
                    val screenY = y * tileSize - cameraY
                    
                    // Draw grass tile
                    drawRect(
                        color = Color(0xFF90EE90), // Light green
                        topLeft = Offset(screenX, screenY),
                        size = androidx.compose.ui.geometry.Size(tileSize, tileSize)
                    )
                    
                    // Draw grid lines
                    drawLine(
                        color = Color(0xFF7F7F7F),
                        start = Offset(screenX, screenY),
                        end = Offset(screenX + tileSize, screenY),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color(0xFF7F7F7F),
                        start = Offset(screenX, screenY),
                        end = Offset(screenX, screenY + tileSize),
                        strokeWidth = 1f
                    )
                }
            }
            
            // Draw NPCs
            state.npcs.values.forEach { npc ->
                val screenX = npc.position.x * tileSize - cameraX
                val screenY = npc.position.y * tileSize - cameraY
                
                // Draw NPC as a blue circle
                drawCircle(
                    color = Color.Blue,
                    radius = tileSize / 2 - 2,
                    center = Offset(screenX + tileSize / 2, screenY + tileSize / 2)
                )
            }
            
            // Draw monsters
            state.monsters.values.forEach { monster ->
                val screenX = monster.position.x * tileSize - cameraX
                val screenY = monster.position.y * tileSize - cameraY
                
                // Draw monster as a red circle
                drawCircle(
                    color = Color.Red,
                    radius = tileSize / 2 - 2,
                    center = Offset(screenX + tileSize / 2, screenY + tileSize / 2)
                )
            }
            
            // Draw other players
            state.players.values.forEach { player ->
                val screenX = player.position.x * tileSize - cameraX
                val screenY = player.position.y * tileSize - cameraY
                
                // Draw player as a green circle
                drawCircle(
                    color = Color.Green,
                    radius = tileSize / 2 - 2,
                    center = Offset(screenX + tileSize / 2, screenY + tileSize / 2)
                )
            }
            
            // Draw own player
            state.playerPosition?.let { position ->
                val screenX = position.x * tileSize - cameraX
                val screenY = position.y * tileSize - cameraY
                
                // Draw own player as a yellow circle
                drawCircle(
                    color = Color.Yellow,
                    radius = tileSize / 2,
                    center = Offset(screenX + tileSize / 2, screenY + tileSize / 2)
                )
            }
        }
        
        // UI Overlay - make sure it doesn't intercept keyboard events
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusable(false) // Explicitly make overlay non-focusable
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Character: $characterName", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Position: (${state.playerPosition?.x ?: 0}, ${state.playerPosition?.y ?: 0})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    // FPS and Ping display
                    Card {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "FPS: ${state.fps}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.fps >= 60) Color.Green else if (state.fps >= 30) Color.Yellow else Color.Red
                            )
                            Text(
                                "Ping: ${state.ping}ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    state.ping == 0L -> Color.Gray
                                    state.ping < 50 -> Color.Green
                                    state.ping < 100 -> Color.Yellow
                                    else -> Color.Red
                                }
                            )
                        }
                    }
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.logout()
                        }
                    }
                ) {
                    Text("Logout")
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Chat window
            if (state.chatMessages.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(200.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        state.chatMessages.forEach { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        
            // Instructions
            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Controls:", style = MaterialTheme.typography.labelSmall)
                    Text("WASD / Arrow Keys: Move", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        // Terminal at the bottom
        Terminal(
            logs = state.logs,
            onClear = { viewModel.clearLogs() },
            modifier = Modifier.fillMaxWidth()
        )
    }
    
    state.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Show error toast or dialog
        }
    }
}
*/

