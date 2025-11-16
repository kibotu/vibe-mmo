# Keyboard Input Debugging Guide

## Issue
Keyboard input (WASD/Arrow keys) is not working - nothing happens when keys are pressed.

## Changes Made

### 1. Added Comprehensive Logging

**File:** `game-client/src/commonMain/kotlin/net/kibotu/ro/client/ui/GameWorldScreen.kt`

- **Focus Request Logging**: Logs when focus is requested and if it succeeds/fails
- **Focus State Tracking**: Tracks and logs when Canvas gains/loses focus
- **All Keyboard Events**: Logs ALL keyboard events (not just movement keys) to see if ANY events are received
- **Key Details**: Logs key type, key code, and native key code
- **Movement Key Detection**: Logs when movement keys are detected
- **Continuous Movement Loop**: Logs when continuous movement loop runs and which keys are pressed

**File:** `game-client/src/commonMain/kotlin/net/kibotu/ro/client/viewmodel/GameViewModel.kt`

- **movePlayer Entry**: Logs when `movePlayer()` is called
- **State Validation**: Logs if `playerPosition` or `selectedCharacter` is null
- **Position Details**: Logs current position and character name

### 2. Focus Management Improvements

- **Focus State Tracking**: Added `isFocused` state variable to track focus
- **Focus Change Callback**: `onFocusChanged` logs when focus changes
- **Click to Focus**: Added clickable modifier to request focus when Canvas is clicked
- **UI Overlay Non-Focusable**: Made UI overlay Column explicitly non-focusable to prevent intercepting events

## Debugging Steps

### Step 1: Check Focus
1. Launch the game and enter the game world
2. Check the terminal/logs for:
   - `"GameWorldScreen: Requesting focus for keyboard input"`
   - `"GameWorldScreen: Focus requested successfully"` OR error message
   - `"Canvas focus changed: isFocused=true"` (should be true)

**If focus is not gained:**
- The Canvas might not be able to receive focus in Compose Desktop
- Try clicking on the Canvas - should see `"Canvas clicked - requesting focus"`
- Check if `isFocused` becomes true after clicking

### Step 2: Check Keyboard Events
1. Press ANY key (not just WASD)
2. Check logs for:
   - `"Keyboard event received: type=..."` (should appear for ANY key press)

**If NO keyboard events are logged:**
- Keyboard events are not reaching the Canvas
- Possible causes:
  - Canvas doesn't support keyboard input in Compose Desktop
  - Focus is not actually on the Canvas
  - Window/application doesn't have focus
  - Platform-specific keyboard input issue

**If keyboard events ARE logged but not movement keys:**
- Check what keys are being detected
- Verify key codes match expected values
- Check if key mapping is correct for your platform

### Step 3: Check Movement Processing
1. Press WASD keys
2. Check logs for:
   - `"KeyDown event: key=..."` (should show W, A, S, or D)
   - `"Movement key detected: ..."`
   - `"Calling movePlayer('...')"`
   - `"movePlayer() called with direction: ..."`

**If movePlayer is not called:**
- Key detection logic might be wrong
- Check key codes/logs to see what keys are actually being received

**If movePlayer is called but nothing happens:**
- Check if `playerPosition` or `selectedCharacter` is null
- Check if position calculation is working
- Check if WebSocket is connected and sending messages

### Step 4: Check Continuous Movement
1. Hold down a movement key
2. Check logs for:
   - `"Continuous movement loop started"`
   - `"Continuous movement: keys pressed=..."`
   - `"Continuous movement: moving ..."`

**If continuous movement doesn't work:**
- Check if `keysPressed` set is being updated correctly
- Verify KeyUp events are being received to remove keys from set

## Potential Issues & Solutions

### Issue 1: Canvas Doesn't Support Keyboard Input
**Symptom:** No keyboard events logged at all

**Solution:** Wrap Canvas in a Box with focusable modifier, or use a different composable for keyboard input

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .focusable()
        .focusRequester(focusRequester)
        .onKeyEvent { event ->
            // Handle keyboard
        }
) {
    Canvas(...) {
        // Drawing code
    }
}
```

### Issue 2: Focus Not Gained
**Symptom:** Focus requested but `isFocused` stays false

**Solution:** 
- Ensure window has focus
- Try clicking Canvas to manually request focus
- Check if other UI elements are stealing focus
- Consider using `FocusRequester.requestFocus()` with delay

### Issue 3: Key Codes Don't Match
**Symptom:** Keyboard events logged but movement keys not detected

**Solution:**
- Check logs for actual key codes received
- Platform-specific key mapping might be needed
- Use native key codes instead of Key enum

### Issue 4: Modifier Order Issue
**Symptom:** Focus works but keyboard events don't

**Solution:** Try reordering modifiers:
```kotlin
.focusable()
.focusRequester(focusRequester)
.onFocusChanged { ... }
.clickable { ... }
.onKeyEvent { ... }
```

## Log Messages Reference

### Input Logs (Tag: "Input")
- `"GameWorldScreen: Requesting focus for keyboard input"`
- `"GameWorldScreen: Focus requested successfully"`
- `"Canvas focus changed: isFocused=true/false"`
- `"Canvas clicked - requesting focus"`
- `"Keyboard event received: type=..., key=..., nativeKeyCode=..."`
- `"KeyDown event: key=..."`
- `"KeyUp event: key=..."`
- `"Movement key detected: ..."`
- `"Calling movePlayer('...')"`
- `"Continuous movement loop started"`
- `"Continuous movement: keys pressed=..."`

### Movement Logs (Tag: "Movement")
- `"movePlayer() called with direction: ..."`
- `"Current position: (x, y), Character: ..."`
- `"Moving player: ... from (...) to (...)"`
- `"movePlayer() called but playerPosition is null"`
- `"movePlayer() called but selectedCharacter is null"`

## Next Steps

1. **Run the game** and check the terminal/logs
2. **Try pressing keys** and observe which logs appear
3. **Click on Canvas** to manually request focus
4. **Report findings**:
   - Are focus logs appearing?
   - Are keyboard event logs appearing?
   - Are movement logs appearing?
   - What's the first log that's missing?

Based on the logs, we can identify exactly where the flow breaks and fix the specific issue.

