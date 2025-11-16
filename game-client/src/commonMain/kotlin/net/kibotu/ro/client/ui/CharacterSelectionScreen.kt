package net.kibotu.ro.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.kibotu.ro.client.viewmodel.GameViewModel

@Composable
fun CharacterSelectionScreen(viewModel: GameViewModel) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCharacterName by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.loadCharacters()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            text = "Select Character",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        if (state.isLoading && state.characters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (state.characters.isEmpty()) {
                Text(
                    text = "No characters found. Create one to get started!",
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.characters) { character ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    viewModel.selectCharacter(character)
                                }
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = character.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Created: ${character.createdAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                enabled = !state.isLoading
            ) {
                Text("Create New Character")
            }
        }
        
        state.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
    
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Character") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCharacterName,
                        onValueChange = { newCharacterName = it },
                        label = { Text("Character Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCharacterName.isNotBlank()) {
                            scope.launch {
                                viewModel.createCharacter(newCharacterName)
                                newCharacterName = ""
                                showCreateDialog = false
                            }
                        }
                    },
                    enabled = newCharacterName.isNotBlank() && !state.isLoading
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

