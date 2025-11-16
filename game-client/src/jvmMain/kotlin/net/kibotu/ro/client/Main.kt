package net.kibotu.ro.client

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import net.kibotu.ro.client.ui.*
import net.kibotu.ro.client.viewmodel.GameViewModel

fun main() = application {
    val viewModel = remember { GameViewModel() }
    
    Window(
        onCloseRequest = {
            viewModel.cleanup()
            exitApplication()
        },
        title = "Ragnarok Online",
        state = rememberWindowState(width = 1024.dp, height = 768.dp)
    ) {
        MaterialTheme {
            val state by viewModel.state.collectAsState()
            
            when (val screen = state.currentScreen) {
                is net.kibotu.ro.client.state.Screen.Login -> {
                    LoginScreen(viewModel)
                }
                is net.kibotu.ro.client.state.Screen.CharacterSelection -> {
                    CharacterSelectionScreen(viewModel)
                }
                is net.kibotu.ro.client.state.Screen.GameWorld -> {
                    GameWorldScreen(viewModel, screen.characterName)
                }
            }
        }
    }
}

