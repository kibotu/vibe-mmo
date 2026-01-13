# Zone Server Map Modeling Specification

**Version:** 1.0  
**Last Updated:** 2024  
**Status:** Active Specification

---

## Table of Contents

1. [Overview](#overview)
2. [Map Data Structure](#map-data-structure)
3. [Coordinate System](#coordinate-system)
4. [Collision System](#collision-system)
5. [Spawn Points](#spawn-points)
6. [NPCs](#npcs)
7. [Monster Spawns](#monster-spawns)
8. [Map Storage](#map-storage)
9. [Map Validation](#map-validation)
10. [Map Loading & Caching](#map-loading--caching)
11. [Best Practices](#best-practices)
12. [File Format](#file-format)

---

## Overview

This specification defines how maps are modeled and stored in the Zone Server. Maps represent game worlds where players, NPCs, and monsters interact. Each map is a self-contained environment with its own collision data, spawn points, and entity definitions.

### Key Principles

- **Tile-Based**: Maps use a discrete tile-based coordinate system
- **Server-Authoritative**: All map data is validated and stored on the server
- **JSON Format**: Maps are stored as human-readable JSON files
- **Collision Bitmap**: Collision data is stored as a compressed bitmap for efficiency
- **Extensible**: Map structure supports future enhancements without breaking compatibility

---

## Map Data Structure

### Core MapData Class

```kotlin
@Serializable
data class MapData(
    // Basic Information
    val name: String,                    // Unique identifier (lowercase, no spaces)
    val displayName: String,             // Human-readable display name
    val description: String? = null,     // Optional description
    
    // Dimensions
    val width: Int,                      // Number of tiles horizontally
    val height: Int,                     // Number of tiles vertically
    val tileSize: Int = 32,              // Pixels per tile (default: 32)
    
    // Collision Data
    val collisionMap: CollisionMapData, // Collision bitmap data
    
    // Spawn Points
    val spawnPoints: List<Position>,     // Player spawn locations
    
    // Static Entities
    val npcs: List<NPCData>,            // NPC definitions
    
    // Monster Spawns (optional)
    val monsterSpawns: List<MonsterSpawnData>? = null,
    
    // Metadata
    val version: String = "1.0",         // Map format version
    val createdAt: String? = null,       // ISO 8601 timestamp
    val updatedAt: String? = null       // ISO 8601 timestamp
)
```

### Field Requirements

#### name
- **Type**: `String`
- **Required**: Yes
- **Format**: Lowercase, alphanumeric with underscores/hyphens only
- **Examples**: `"prontera"`, `"payon_field"`, `"geffen_dungeon_01"`
- **Uniqueness**: Must be unique across all maps
- **Validation**: Regex: `^[a-z0-9_-]+$`

#### displayName
- **Type**: `String`
- **Required**: Yes
- **Format**: Any human-readable string
- **Examples**: `"Prontera"`, `"Payon Field"`, `"Geffen Dungeon B1F"`

#### width & height
- **Type**: `Int`
- **Required**: Yes
- **Range**: 10 - 10000 tiles
- **Recommended**: 100 - 1000 tiles for optimal performance
- **Note**: Must match collision map dimensions exactly

#### tileSize
- **Type**: `Int`
- **Required**: No (default: 32)
- **Common Values**: 16, 32, 48, 64
- **Note**: Used for client-side rendering calculations

---

## Coordinate System

### Position Structure

```kotlin
@Serializable
data class Position(
    val x: Int,  // Horizontal tile coordinate (0 to width-1)
    val y: Int   // Vertical tile coordinate (0 to height-1)
)
```

### Coordinate Rules

1. **Origin**: Top-left corner is (0, 0)
2. **X-Axis**: Increases rightward (0 → width-1)
3. **Y-Axis**: Increases downward (0 → height-1)
4. **Bounds**: All positions must satisfy:
   - `0 <= x < width`
   - `0 <= y < height`

### Coordinate Validation

```kotlin
fun isValidPosition(position: Position, mapData: MapData): Boolean {
    return position.x >= 0 && 
           position.y >= 0 && 
           position.x < mapData.width && 
           position.y < mapData.height
}
```

---

## Collision System

### CollisionMapData Structure

```kotlin
@Serializable
data class CollisionMapData(
    val width: Int,        // Must match map width
    val height: Int,      // Must match map height
    val data: String      // Base64-encoded bitmap
)
```

### Collision Bitmap Format

- **Encoding**: Base64-encoded binary bitmap
- **Bit Order**: Row-major order (left-to-right, top-to-bottom)
- **Bit Values**: 
  - `1` = Walkable (players can move here)
  - `0` = Blocked (players cannot move here)
- **Compression**: Bits are packed 8 per byte (MSB first)

### Collision Map Generation

```kotlin
class CollisionMap(
    val width: Int,
    val height: Int,
    private val collisionData: BooleanArray  // true = walkable
) {
    fun isWalkable(x: Int, y: Int): Boolean {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false  // Out of bounds = not walkable
        }
        val index = y * width + x
        return collisionData[index]
    }
    
    fun toBase64(): String {
        // Pack bits into bytes and encode as Base64
        val bytes = ByteArray((width * height + 7) / 8)
        for (i in collisionData.indices) {
            if (collisionData[i]) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
            }
        }
        return Base64.getEncoder().encodeToString(bytes)
    }
    
    companion object {
        fun fromBase64(base64: String, width: Int, height: Int): CollisionMap {
            val bytes = Base64.getDecoder().decode(base64)
            val collisionData = BooleanArray(width * height)
            for (i in collisionData.indices) {
                collisionData[i] = (bytes[i / 8].toInt() and (1 shl (7 - (i % 8)))) != 0
            }
            return CollisionMap(width, height, collisionData)
        }
        
        fun createEmpty(width: Int, height: Int, defaultWalkable: Boolean = true): CollisionMap {
            val collisionData = BooleanArray(width * height) { defaultWalkable }
            return CollisionMap(width, height, collisionData)
        }
    }
}
```

### Collision Map Best Practices

1. **Default State**: New maps should default to all walkable
2. **Boundary Walls**: Consider making map edges non-walkable to prevent out-of-bounds
3. **Performance**: Collision checks are O(1) lookups
4. **Size**: For a 1000x1000 map, collision data is ~125KB (compressed)

---

## Spawn Points

### Spawn Point Requirements

```kotlin
val spawnPoints: List<Position>
```

### Rules

1. **Minimum**: At least 1 spawn point required
2. **Validation**: All spawn points must be:
   - Within map bounds
   - On walkable tiles (collision check)
3. **Selection**: Server randomly selects a spawn point when player joins
4. **Distribution**: Spread spawn points across the map for better player distribution

### Spawn Point Selection

```kotlin
fun getSpawnPosition(): Position {
    return if (spawnPoints.isNotEmpty()) {
        spawnPoints.random()
    } else {
        // Fallback to center
        Position(mapData.width / 2, mapData.height / 2)
    }
}
```

---

## NPCs

### NPCData Structure

```kotlin
@Serializable
data class NPCData(
    val id: String,                      // Unique NPC identifier
    val name: String,                    // Display name
    val position: Position,              // Spawn position
    val type: String,                    // NPC type (e.g., "merchant", "quest", "guard")
    val direction: String = "down",      // Facing direction: "up", "down", "left", "right"
    val properties: Map<String, String> = emptyMap()  // Custom properties
)
```

### NPC Requirements

1. **ID Uniqueness**: Must be unique within the map
2. **Position**: Must be walkable (NPCs don't block movement, but should spawn on walkable tiles)
3. **Type**: Common types include:
   - `"merchant"` - Shop NPC
   - `"quest"` - Quest giver
   - `"guard"` - Guard/security NPC
   - `"healer"` - Healing NPC
   - `"warp"` - Warp portal NPC
   - `"custom"` - Custom NPC type

### NPC Properties

The `properties` map allows storing custom data:

```json
{
  "id": "merchant_001",
  "name": "Weapon Shop",
  "position": { "x": 50, "y": 50 },
  "type": "merchant",
  "direction": "down",
  "properties": {
    "shopType": "weapon",
    "currency": "zeny",
    "dialog": "Welcome to my weapon shop!"
  }
}
```

---

## Monster Spawns

### MonsterSpawnData Structure

```kotlin
@Serializable
data class MonsterSpawnData(
    val id: String,              // Unique spawn identifier
    val monsterType: String,     // Monster type (e.g., "poring", "lunatic")
    val position: Position,      // Spawn center position
    val respawnTime: Int = 5000, // Respawn delay in milliseconds
    val maxCount: Int = 1,       // Maximum monsters at this spawn
    val spawnRadius: Int = 0     // Random spawn radius (0 = exact position)
)
```

### Monster Spawn Rules

1. **Position**: Must be walkable
2. **Respawn Time**: Minimum 1000ms (1 second)
3. **Max Count**: Minimum 1, recommended max 10
4. **Spawn Radius**: 
   - `0` = Spawn at exact position
   - `> 0` = Random position within radius (must all be walkable)

### Monster Spawn Example

```json
{
  "id": "poring_spawn_001",
  "monsterType": "poring",
  "position": { "x": 100, "y": 100 },
  "respawnTime": 5000,
  "maxCount": 3,
  "spawnRadius": 5
}
```

This spawn will:
- Spawn up to 3 "poring" monsters
- Respawn 5 seconds after a monster dies
- Spawn within 5 tiles of position (100, 100)

---

## Map Storage

### Storage Location

Maps are stored in: `zone-server/src/main/resources/maps/`

### File Naming

- **Format**: `{mapName}.json`
- **Example**: `prontera.json`, `payon_field.json`
- **Case**: Lowercase, matching the `name` field

### Storage Service

```kotlin
class MapStorageService {
    private val mapsDirectory: Path = Paths.get("src/main/resources/maps")
    
    fun loadMap(mapName: String): MapData?
    fun saveMap(mapData: MapData): Boolean
    fun listMaps(): List<String>
    fun deleteMap(mapName: String): Boolean
    fun mapExists(mapName: String): Boolean
}
```

### File Structure

```
maps/
├── prontera.json
├── payon_field.json
├── geffen_dungeon_01.json
└── ...
```

---

## Map Validation

### Validation Rules

1. **Name Validation**:
   - Must match regex: `^[a-z0-9_-]+$`
   - Must be unique

2. **Dimension Validation**:
   - Width and height: 10 - 10000
   - Collision map dimensions must match map dimensions

3. **Position Validation**:
   - All spawn points must be valid and walkable
   - All NPC positions must be valid and walkable
   - All monster spawn positions must be valid and walkable

4. **Required Fields**:
   - name, displayName, width, height, collisionMap, spawnPoints, npcs

5. **Collision Map Validation**:
   - Base64 string must decode correctly
   - Decoded size must match width * height

### Validation Service

```kotlin
class MapValidator {
    fun validate(mapData: MapData): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate name
        if (!mapData.name.matches(Regex("^[a-z0-9_-]+$"))) {
            errors.add("Invalid map name format")
        }
        
        // Validate dimensions
        if (mapData.width < 10 || mapData.width > 10000) {
            errors.add("Width must be between 10 and 10000")
        }
        
        // Validate collision map
        if (mapData.collisionMap.width != mapData.width ||
            mapData.collisionMap.height != mapData.height) {
            errors.add("Collision map dimensions must match map dimensions")
        }
        
        // Validate spawn points
        mapData.spawnPoints.forEachIndexed { index, pos ->
            if (!isValidPosition(pos, mapData)) {
                errors.add("Spawn point $index is out of bounds")
            }
            // Check if walkable...
        }
        
        // ... more validations
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}
```

---

## Map Loading & Caching

### Loading Strategy

1. **On-Demand Loading**: Maps are loaded when a zone instance is created
2. **Caching**: Consider caching loaded maps in memory for performance
3. **Reload**: Maps can be reloaded without server restart (hot-reload)

### Zone Instance Creation

```kotlin
suspend fun joinZone(
    characterId: String,
    zoneName: String,
    sessionToken: String
): JoinZoneResult {
    // Load map data
    val mapData = getMapDataForZone(zoneName) 
        ?: return JoinZoneResult.Error("Unknown zone: $zoneName")
    
    // Validate map
    val validationResult = mapValidator.validate(mapData)
    if (!validationResult.isValid) {
        // Log warnings but allow invalid maps in development
    }
    
    // Create zone instance
    val zoneInstance = ZoneInstance(instanceId, zoneName, mapData)
    
    // Add player...
}
```

---

## Best Practices

### Map Design

1. **Size Guidelines**:
   - Small maps: 100-200 tiles (towns, dungeons)
   - Medium maps: 200-500 tiles (fields, forests)
   - Large maps: 500-1000 tiles (open world areas)
   - Very large maps: 1000+ tiles (world maps)

2. **Spawn Point Distribution**:
   - Spread spawn points evenly across the map
   - Place spawn points in safe, walkable areas
   - Consider multiple spawn points for large maps

3. **Collision Design**:
   - Use boundaries to prevent out-of-bounds movement
   - Create natural barriers (walls, water, cliffs)
   - Ensure all spawn points are accessible

4. **NPC Placement**:
   - Place NPCs in logical locations (merchants in towns)
   - Ensure NPCs are on walkable tiles
   - Use appropriate facing directions

5. **Monster Spawn Design**:
   - Distribute spawns evenly across the map
   - Use appropriate respawn times (5-30 seconds)
   - Consider spawn density (not too crowded)

### Performance Considerations

1. **Map Size**: Larger maps use more memory
2. **Collision Checks**: O(1) lookups are fast
3. **Entity Count**: Limit NPCs and spawns for performance
4. **Caching**: Cache loaded maps to avoid repeated file I/O

### Versioning

- Use the `version` field to track map format changes
- Increment version when breaking changes occur
- Maintain backward compatibility when possible

---

## File Format

### Complete Example

```json
{
  "name": "prontera",
  "displayName": "Prontera",
  "description": "The capital city of Rune-Midgard",
  "width": 200,
  "height": 200,
  "tileSize": 32,
  "collisionMap": {
    "width": 200,
    "height": 200,
    "data": "BASE64_ENCODED_COLLISION_BITMAP_HERE"
  },
  "spawnPoints": [
    { "x": 100, "y": 100 },
    { "x": 150, "y": 150 },
    { "x": 50, "y": 50 }
  ],
  "npcs": [
    {
      "id": "merchant_weapon",
      "name": "Weapon Shop",
      "position": { "x": 120, "y": 120 },
      "type": "merchant",
      "direction": "down",
      "properties": {
        "shopType": "weapon"
      }
    },
    {
      "id": "guard_001",
      "name": "City Guard",
      "position": { "x": 100, "y": 100 },
      "type": "guard",
      "direction": "down"
    }
  ],
  "monsterSpawns": [
    {
      "id": "poring_spawn_001",
      "monsterType": "poring",
      "position": { "x": 180, "y": 180 },
      "respawnTime": 5000,
      "maxCount": 3,
      "spawnRadius": 5
    }
  ],
  "version": "1.0",
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

### Minimal Example

```json
{
  "name": "test_map",
  "displayName": "Test Map",
  "width": 100,
  "height": 100,
  "collisionMap": {
    "width": 100,
    "height": 100,
    "data": "BASE64_ENCODED_COLLISION_BITMAP"
  },
  "spawnPoints": [
    { "x": 50, "y": 50 }
  ],
  "npcs": []
}
```

---

## API Endpoints

### Map Management

- `GET /api/maps` - List all maps
- `GET /api/maps/{mapName}` - Get map data
- `POST /api/maps` - Create new map
- `PUT /api/maps/{mapName}` - Update map
- `DELETE /api/maps/{mapName}` - Delete map
- `POST /api/maps/{mapName}/validate` - Validate map without saving

---

## Future Enhancements

### Planned Features

1. **Height Maps**: Support for elevation/Z-axis
2. **Multiple Layers**: Ground, objects, decorations
3. **Warp Points**: Define map transitions
4. **Music/Sound**: Map-specific audio
5. **Weather**: Map-specific weather effects
6. **Lighting**: Dynamic lighting data
7. **Scripts**: Map-specific scripts/triggers

### Extension Points

The `properties` map in NPCData and future extension fields allow adding custom data without breaking existing maps.

---

**Document Version:** 1.0  
**Last Updated:** 2024  
**Maintainer:** Zone Server Development Team

