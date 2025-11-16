package net.kibotu.ro.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.kibotu.ro.client.model.*
import net.kibotu.ro.client.viewmodel.GameViewModel

@Composable
fun GameWorldScreen(viewModel: GameViewModel, characterName: String) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    var keysPressed by remember { mutableStateOf(emptySet<Key>()) }
    
    LaunchedEffect(Unit) {
        // Handle keyboard input
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Game Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF87CEEB)) // Sky blue background
                .focusable()
                .onKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyDown -> {
                            keysPressed = keysPressed + event.key
                            when (event.key) {
                                Key.W -> {
                                    viewModel.movePlayer("up")
                                    true
                                }
                                Key.S -> {
                                    viewModel.movePlayer("down")
                                    true
                                }
                                Key.A -> {
                                    viewModel.movePlayer("left")
                                    true
                                }
                                Key.D -> {
                                    viewModel.movePlayer("right")
                                    true
                                }
                                else -> false
                            }
                        }
                        event.type == KeyEventType.KeyUp -> {
                            keysPressed = keysPressed - event.key
                            false
                        }
                        else -> false
                    }
                }
        ) {
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
        
        // UI Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
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
    
    state.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Show error toast or dialog
        }
    }
}

