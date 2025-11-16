# Player Movement Flow - Complete Trace

This document traces the entire flow of player movement from keyboard input to server update and back to client rendering.

## Overview

The movement system uses an **optimistic update** pattern: the client immediately updates its local state for responsive UI, then sends the movement to the server for validation. The server validates and broadcasts updates back to all clients.

---

## Step-by-Step Flow

### 1. **Keyboard Input Recognition** (Client - UI Layer)

**File:** `game-client/src/commonMain/kotlin/net/kibotu/ro/client/ui/GameWorldScreen.kt`

**Location:** Lines 75-104

```kotlin
.onKeyEvent { event ->
    when {
        event.type == KeyEventType.KeyDown -> {
            val key = event.key
            // Only track movement keys
            if (key == Key.W || key == Key.DirectionUp || 
                key == Key.S || key == Key.DirectionDown ||
                key == Key.A || key == Key.DirectionLeft ||
                key == Key.D || key == Key.DirectionRight) {
                keysPressed = keysPressed + key
                // Trigger immediate movement on key down
                when (key) {
                    Key.W, Key.DirectionUp -> viewModel.movePlayer("up")
                    Key.S, Key.DirectionDown -> viewModel.movePlayer("down")
                    Key.A, Key.DirectionLeft -> viewModel.movePlayer("left")
                    Key.D, Key.DirectionRight -> viewModel.movePlayer("right")
                    else -> {}
                }
                true
            }
        }
        event.type == KeyEventType.KeyUp -> {
            keysPressed = keysPressed - event.key
            false
        }
    }
}
```

**What happens:**
- Compose UI captures keyboard events via `.onKeyEvent` modifier on the Canvas
- Canvas has focus (requested via `FocusRequester` on screen load)
- On `KeyDown` for movement keys (WASD or Arrow Keys):
  - Key is added to `keysPressed` set
  - `viewModel.movePlayer(direction)` is called immediately
- On `KeyUp`: Key is removed from `keysPressed` set
- Continuous movement: A `LaunchedEffect` loop (lines 38-60) checks `keysPressed` every 150ms and triggers movement while keys are held

**Key Points:**
- Supports both WASD and Arrow keys
- Immediate response on key press
- Continuous movement while key is held (150ms interval)

---

### 2. **Client-Side Movement Processing** (Client - ViewModel Layer)

**File:** `game-client/src/commonMain/kotlin/net/kibotu/ro/client/viewmodel/GameViewModel.kt`

**Location:** Lines 226-266

```kotlin
fun movePlayer(direction: String) {
    val currentPosition = _state.value.playerPosition ?: return
    val selectedCharacter = _state.value.selectedCharacter ?: return
    
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
```

**What happens:**
1. **Calculate new position**: Based on current position and direction (1 tile movement)
2. **Early return**: If position hasn't changed, exit
3. **Optimistic update**: Immediately update local state (`_state`) with new position
   - Updates `playerPosition` in state
   - Updates player entry in `players` map if exists
4. **Send to server**: Asynchronously sends movement via WebSocket

**Key Points:**
- **Optimistic update pattern**: UI updates immediately without waiting for server
- Position calculated client-side (server validates)
- Movement sent asynchronously via coroutine

---

### 3. **WebSocket Message Serialization & Sending** (Client - Network Layer)

**File:** `game-client/src/commonMain/kotlin/net/kibotu/ro/client/network/ZoneWebSocketClient.kt`

**Location:** Lines 108-117

```kotlin
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
```

**What happens:**
1. Creates `ClientMessage.Move` object with direction and position
2. Serializes to JSON using Kotlinx Serialization
3. Sends as WebSocket text frame

**Message Structure:**
```json
{
  "type": "move",
  "direction": "up",
  "position": {
    "x": 10,
    "y": 9
  }
}
```

**Key Points:**
- Uses polymorphic serialization (sealed class with discriminator)
- Sends via WebSocket text frame
- Error handling for send failures

---

### 4. **Server Receives WebSocket Message** (Server - WebSocket Handler)

**File:** `zone-server/src/main/kotlin/net/kibotu/ro/zone/websocket/WebSocketHandler.kt`

**Location:** Lines 104-112 (message loop), 160-228 (message handling)

```kotlin
// Message loop (lines 104-112)
for (frame in incoming) {
    if (frame is Frame.Text) {
        handleMessage(this, frame.readText(), zoneInstance, player, characterId)
    } else if (frame is Frame.Close) {
        println("[ZoneServer] Received close frame from $characterId")
        break
    }
}

// Message handling (lines 160-228)
private suspend fun handleMessage(
    session: WebSocketSession,
    text: String,
    zoneInstance: net.kibotu.ro.zone.model.ZoneInstance,
    player: net.kibotu.ro.zone.model.Player,
    characterId: String
) {
    try {
        println("[ZoneServer] Received message from $characterId: $text")
        val message = json.decodeFromString(ClientMessage.serializer(), text)
        
        when (message) {
            is ClientMessage.Move -> {
                // Process movement...
            }
        }
    } catch (e: Exception) {
        // Error handling...
    }
}
```

**What happens:**
1. WebSocket receives text frame
2. Deserializes JSON to `ClientMessage` object
3. Routes to appropriate handler based on message type

**Key Points:**
- Uses same JSON serialization format as client
- Error handling for malformed messages

---

### 5. **Server Validates & Updates Position** (Server - Zone Instance)

**File:** `zone-server/src/main/kotlin/net/kibotu/ro/zone/websocket/WebSocketHandler.kt`

**Location:** Lines 172-214

```kotlin
is ClientMessage.Move -> {
    // Get fresh player reference to ensure we have latest state
    val currentPlayer = zoneInstance.getPlayer(characterId)
    if (currentPlayer == null) {
        println("[ZoneServer] ERROR: Player $characterId not found in zone")
        session.sendMessage(ServerMessage.Error("Player not found in zone"))
        return
    }
    
    val oldPosition = currentPlayer.position
    println("[ZoneServer] Movement request from ${currentPlayer.name} ($characterId): ${oldPosition} -> ${message.position} (direction: ${message.direction})")
    
    // Validate and update position
    val success = zoneInstance.updatePlayerPosition(
        characterId,
        message.position,
        message.direction
    )
    
    if (success) {
        // Get updated player to ensure we have the latest position
        val updatedPlayer = zoneInstance.getPlayer(characterId)
        if (updatedPlayer != null) {
            println("[ZoneServer] Movement successful: ${updatedPlayer.name} moved to (${updatedPlayer.position.x}, ${updatedPlayer.position.y})")
            
            // Send movement confirmation back to the sender
            val movementMessage = ServerMessage.Movement(
                characterId = updatedPlayer.characterId,
                position = updatedPlayer.position,
                direction = updatedPlayer.direction
            )
            session.sendMessage(movementMessage)
            
            // Broadcast movement to all other players
            broadcastMovement(zoneInstance, updatedPlayer, updatedPlayer.position, updatedPlayer.direction, characterId)
        }
    } else {
        println("[ZoneServer] Movement REJECTED: Invalid position (${message.position.x}, ${message.position.y}) for player $characterId")
        // Send error or reject movement - client should revert optimistic update
        session.sendMessage(ServerMessage.Error("Invalid movement: position out of bounds or invalid"))
    }
}
```

**What happens:**
1. **Get player**: Retrieves current player from zone instance
2. **Validate**: Calls `zoneInstance.updatePlayerPosition()` which:
   - Checks if position is within map bounds
   - Checks collision (if collision map exists)
   - Updates player position and direction atomically (with mutex lock)
3. **On success**:
   - Sends confirmation back to moving player
   - Broadcasts movement to all other players in zone
4. **On failure**:
   - Sends error message to client

**Key Points:**
- Server is authoritative for position validation
- Thread-safe updates using mutex
- Broadcasts to all players except sender

---

### 6. **Position Validation** (Server - Zone Instance Model)

**File:** `zone-server/src/main/kotlin/net/kibotu/ro/zone/model/ZoneInstance.kt`

**Location:** Lines 93-104 (update), 141-154 (validation)

```kotlin
suspend fun updatePlayerPosition(characterId: String, position: Position, direction: String): Boolean {
    return mutex.withLock {
        val player = players[characterId] ?: return@withLock false
        if (isValidPosition(position)) {
            player.position = position
            player.direction = direction
            true
        } else {
            false
        }
    }
}

private fun isValidPosition(position: Position): Boolean {
    // Check bounds
    val isValid = position.x >= 0 && position.y >= 0 && 
                 position.x < mapData.width && position.y < mapData.height
    
    if (!isValid) {
        println("[ZoneInstance] Position validation failed: (${position.x}, ${position.y}) is out of bounds. Map size: ${mapData.width}x${mapData.height}")
    }
    
    // TODO: Check collision map if available
    // For now, just check bounds
    
    return isValid
}
```

**What happens:**
1. **Mutex lock**: Ensures thread-safe access to player data
2. **Get player**: Retrieves player from concurrent map
3. **Validate position**: Checks bounds (0 <= x < width, 0 <= y < height)
4. **Update**: If valid, updates position and direction
5. **Return**: Returns success/failure

**Key Points:**
- Thread-safe using `Mutex`
- Currently only validates bounds (collision map TODO)
- Atomic operation (all-or-nothing)

---

### 7. **Server Broadcasts Movement** (Server - WebSocket Handler)

**File:** `zone-server/src/main/kotlin/net/kibotu/ro/zone/websocket/WebSocketHandler.kt`

**Location:** Lines 252-265 (broadcastMovement), 282-300 (broadcastToOthers)

```kotlin
private suspend fun broadcastMovement(
    zoneInstance: net.kibotu.ro.zone.model.ZoneInstance,
    player: net.kibotu.ro.zone.model.Player,
    position: Position,
    direction: String,
    excludeCharacterId: String
) {
    val message = ServerMessage.Movement(
        characterId = player.characterId,
        position = position,
        direction = direction
    )
    broadcastToOthers(zoneInstance.id, message, excludeCharacterId)
}

private suspend fun broadcastToOthers(
    instanceId: String,
    message: ServerMessage,
    excludeCharacterId: String
) {
    val instanceConnections = connections[instanceId] ?: return
    val messageText = json.encodeToString(ServerMessage.serializer(), message)
    
    instanceConnections.forEach { (characterId, session) ->
        if (characterId != excludeCharacterId) {
            try {
                session.send(Frame.Text(messageText))
            } catch (e: Exception) {
                // Connection might be closed
                instanceConnections.remove(characterId)
            }
        }
    }
}
```

**What happens:**
1. Creates `ServerMessage.Movement` with player ID, position, direction
2. Serializes to JSON
3. Iterates through all connected players in zone
4. Sends to all except the moving player (they already got confirmation)

**Message Structure:**
```json
{
  "type": "movement",
  "characterId": "char-123",
  "position": {
    "x": 10,
    "y": 9
  },
  "direction": "up"
}
```

**Key Points:**
- Excludes sender (they got confirmation message)
- Error handling for closed connections
- Uses connection map: `instanceId -> characterId -> WebSocketSession`

---

### 8. **Client Receives Movement Update** (Client - Network Layer)

**File:** `game-client/src/commonMain/kotlin/net/kibotu/ro/client/network/ZoneWebSocketClient.kt`

**Location:** Lines 82-99 (receive loop)

```kotlin
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
```

**What happens:**
1. Continuously receives WebSocket frames
2. Deserializes text frames to `ServerMessage`
3. Updates `_messages` StateFlow (triggers observers)

**Key Points:**
- Runs in background coroutine
- Updates StateFlow for reactive updates
- Error handling for deserialization failures

---

### 9. **Client Processes Movement Update** (Client - ViewModel)

**File:** `game-client/src/commonMain/kotlin/net/kibotu/ro/client/viewmodel/GameViewModel.kt`

**Location:** Lines 27-31 (observer), 317-349 (handler)

```kotlin
// Observer setup (lines 27-31)
scope.launch {
    zoneWebSocketClient.messages.collect { message ->
        message?.let { handleServerMessage(it) }
    }
}

// Movement handler (lines 317-349)
is ServerMessage.Movement -> {
    ClientLogger.debug("Movement update: ${message.characterId} -> (${message.position.x}, ${message.position.y})", "WebSocket")
    _state.update { state ->
        val isOwnCharacter = state.selectedCharacter?.id == message.characterId
        
        // Update or create player entry
        val existingPlayer = state.players[message.characterId]
        val updatedPlayer = if (existingPlayer != null) {
            existingPlayer.copy(
                position = message.position,
                direction = message.direction
            )
        } else {
            // Player not in our map yet, create entry
            PlayerDTO(
                characterId = message.characterId,
                name = message.characterId, // Fallback, should be updated from zone state
                position = message.position,
                direction = message.direction
            )
        }
        
        state.copy(
            players = state.players + (message.characterId to updatedPlayer),
            // Always sync own position from server (authoritative)
            playerPosition = if (isOwnCharacter) {
                message.position
            } else {
                state.playerPosition
            }
        )
    }
}
```

**What happens:**
1. **Observer**: Collects messages from WebSocket client StateFlow
2. **Route**: Calls `handleServerMessage()` which routes by message type
3. **Update state**:
   - Updates player entry in `players` map (or creates if new)
   - **If own character**: Syncs `playerPosition` from server (authoritative)
   - **If other player**: Only updates their entry in `players` map

**Key Points:**
- **Authoritative sync**: Own position always synced from server (corrects optimistic updates)
- Handles both own movement confirmations and other players' movements
- Creates player entries if they don't exist yet

---

### 10. **UI Re-renders** (Client - UI Layer)

**File:** `game-client/src/commonMain/kotlin/net/kibotu/ro/client/ui/GameWorldScreen.kt`

**Location:** Lines 26, 106-194 (rendering)

```kotlin
// State collection (line 26)
val state by viewModel.state.collectAsState()

// Rendering (lines 106-194)
Canvas(...) {
    val tileSize = 32f
    val cameraX = (state.playerPosition?.x?.toFloat() ?: 0f) * tileSize - size.width / 2
    val cameraY = (state.playerPosition?.y?.toFloat() ?: 0f) * tileSize - size.height / 2
    
    // ... draw tiles ...
    
    // Draw other players
    state.players.values.forEach { player ->
        val screenX = player.position.x * tileSize - cameraX
        val screenY = player.position.y * tileSize - cameraY
        
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
        
        drawCircle(
            color = Color.Yellow,
            radius = tileSize / 2,
            center = Offset(screenX + tileSize / 2, screenY + tileSize / 2)
        )
    }
}
```

**What happens:**
1. **State observation**: `collectAsState()` observes ViewModel state changes
2. **Camera calculation**: Camera follows player position (centered)
3. **Rendering**:
   - Draws tiles (grass grid)
   - Draws NPCs (blue circles)
   - Draws monsters (red circles)
   - Draws other players (green circles)
   - Draws own player (yellow circle, larger)
4. **Re-composition**: When state changes, Compose automatically re-renders

**Key Points:**
- **Reactive UI**: Automatically updates when state changes
- **Camera system**: Follows player position
- **Visual distinction**: Own player is yellow and larger

---

## Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ CLIENT SIDE                                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 1. Keyboard Input                                              │
│    GameWorldScreen.onKeyEvent()                                │
│    ↓                                                            │
│ 2. ViewModel Processing                                        │
│    GameViewModel.movePlayer()                                  │
│    ├─ Calculate new position                                   │
│    ├─ Optimistic update (immediate UI update)                  │
│    └─ Send to server                                           │
│    ↓                                                            │
│ 3. Network Layer                                               │
│    ZoneWebSocketClient.sendMove()                              │
│    ├─ Serialize to JSON                                        │
│    └─ Send WebSocket frame                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ WebSocket
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ SERVER SIDE                                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 4. Receive Message                                             │
│    WebSocketHandler.handleMessage()                            │
│    ├─ Deserialize JSON                                         │
│    └─ Route by message type                                    │
│    ↓                                                            │
│ 5. Validate & Update                                           │
│    ZoneInstance.updatePlayerPosition()                         │
│    ├─ Mutex lock (thread-safe)                                 │
│    ├─ Validate bounds                                          │
│    └─ Update position                                          │
│    ↓                                                            │
│ 6. Broadcast                                                    │
│    WebSocketHandler.broadcastMovement()                        │
│    ├─ Send confirmation to sender                               │
│    └─ Broadcast to all other players                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ WebSocket
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ CLIENT SIDE                                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 7. Receive Update                                              │
│    ZoneWebSocketClient (receive loop)                          │
│    ├─ Deserialize JSON                                         │
│    └─ Update StateFlow                                         │
│    ↓                                                            │
│ 8. Process Update                                              │
│    GameViewModel.handleServerMessage()                         │
│    ├─ Update players map                                       │
│    └─ Sync own position (authoritative)                        │
│    ↓                                                            │
│ 9. UI Re-render                                                 │
│    GameWorldScreen (Compose)                                   │
│    ├─ Collect state                                            │
│    ├─ Calculate camera                                         │
│    └─ Draw all entities                                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Design Patterns

### 1. **Optimistic Updates**
- Client updates UI immediately for responsiveness
- Server validates and sends authoritative position
- Client syncs with server to correct any discrepancies

### 2. **Authoritative Server**
- Server validates all movements (bounds, collision)
- Server position is source of truth
- Client syncs own position from server updates

### 3. **State Management**
- ViewModel holds game state (`AppState`)
- StateFlow for reactive updates
- Compose observes state and re-renders automatically

### 4. **Thread Safety**
- Server uses `Mutex` for atomic position updates
- Client uses coroutines for async operations
- ConcurrentHashMap for connection management

### 5. **Message Serialization**
- Kotlinx Serialization with polymorphic sealed classes
- JSON format with type discriminator
- Same models on client and server

---

## Error Handling

### Client Side
- **Invalid position**: Server sends error, client shows error message
- **Connection loss**: WebSocket errors logged, connection can be retried
- **Deserialization errors**: Logged, message skipped

### Server Side
- **Invalid movement**: Sends error message, client should revert optimistic update
- **Player not found**: Sends error, closes connection
- **Connection errors**: Removes from connection map, broadcasts player left

---

## Performance Considerations

1. **Movement rate**: Client limits continuous movement to 150ms intervals
2. **Optimistic updates**: Immediate UI feedback without waiting for server
3. **Selective updates**: Only updates changed player positions
4. **Camera culling**: Only renders visible tiles (could be optimized further)
5. **Thread-safe operations**: Mutex ensures no race conditions

---

## Future Improvements

1. **Collision detection**: Implement collision map checking
2. **Movement interpolation**: Smooth movement between tiles
3. **Prediction**: Client-side prediction with server reconciliation
4. **Delta compression**: Only send changed data
5. **Movement queuing**: Queue movements during network lag

