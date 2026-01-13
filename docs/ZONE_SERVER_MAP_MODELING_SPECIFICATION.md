# Zone Server Map Modeling Specification

**Version:** 1.0  
**Last Updated:** 2024  
**Status:** Specification for Zone Server Map System

---

## Table of Contents

1. [Overview](#overview)
2. [Map Data Model](#map-data-model)
3. [Map Storage](#map-storage)
4. [Map Loading](#map-loading)
5. [Map Validation](#map-validation)
6. [Map API](#map-api)
7. [Level Editor Integration](#level-editor-integration)
8. [Implementation Details](#implementation-details)

---

## Overview

This specification defines how maps are modeled, stored, and managed in the Zone Server. Maps define the spatial layout, collision boundaries, spawn points, and static entities (NPCs) for game zones.

### Core Principles

- **Server-Authoritative**: All map data is validated and stored on the server
- **Persistent Storage**: Maps are stored as JSON files for easy editing and version control
- **Modular Design**: Maps can be created, edited, and loaded independently
- **Validation**: All maps are validated before being used in game
- **Editor Support**: Maps can be created and edited via a web-based level editor

### Map Lifecycle

1. **Creation**: Map created via level editor or manually
2. **Storage**: Map saved as JSON file in `maps/` directory
3. **Loading**: Map loaded from storage when zone instance is created
4. **Validation**: Map validated for correctness (bounds, spawn points, etc.)
5. **Runtime**: Map data used by ZoneInstance for collision, spawning, NPCs

---

## Map Data Model

### MapData Structure

```kotlin
@Serializable
data class MapData(
    // Basic Information
    val name: String,                    // Unique map identifier (e.g., "prontera", "payon")
    val displayName: String,             // Human-readable name (e.g., "Prontera - Capital City")
    val description: String? = null,     // Optional map description
    
    // Dimensions
    val width: Int,                      // Map width in tiles (must be > 0)
    val height: Int,                     // Map height in tiles (must be > 0)
    val tileSize: Int = 32,              // Pixels per tile (default: 32)
    
    // Collision Data
    val collisionMap: CollisionMapData,  // Collision/walkability data
    
    // Spawn Points
    val spawnPoints: List<Position>,      // Player spawn locations (must have at least 1)
    
    // Static Entities
    val npcs: List<NPCData>,             // NPCs placed on the map
    
    // Monster Spawns (optional)
    val monsterSpawns: List<MonsterSpawnData>? = null,
    
    // Metadata
    val version: String = "1.0",          // Map format version
    val createdAt: String? = null,       // ISO 8601 timestamp
    val updatedAt: String? = null        // ISO 8601 timestamp
)
```

### CollisionMapData

```kotlin
@Serializable
data class CollisionMapData(
    val width: Int,                      // Must match map width
    val height: Int,                     // Must match map height
    val data: String                     // Base64-encoded bitmap (1 bit per tile)
                                         // true = walkable, false = blocked
)

// Helper class for collision checking
class CollisionMap(
    val width: Int,
    val height: Int,
    private val collisionData: BooleanArray
) {
    fun isWalkable(x: Int, y: Int): Boolean {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false
        }
        val index = y * width + x
        return collisionData[index]
    }
    
    fun setWalkable(x: Int, y: Int, walkable: Boolean) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            val index = y * width + x
            collisionData[index] = walkable
        }
    }
    
    fun toBase64(): String {
        // Convert BooleanArray to bitmap, then to Base64
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
    }
}
```

### NPCData

```kotlin
@Serializable
data class NPCData(
    val id: String,                      // Unique NPC identifier within the map
    val name: String,                    // Display name
    val position: Position,              // NPC position (must be walkable)
    val type: String,                    // NPC type (e.g., "merchant", "quest", "guard")
    val direction: String = "down",      // Facing direction: "up", "down", "left", "right"
    val properties: Map<String, String> = emptyMap()  // Additional properties (e.g., shop type, quest ID)
)
```

### MonsterSpawnData

```kotlin
@Serializable
data class MonsterSpawnData(
    val id: String,                      // Unique spawn identifier
    val monsterType: String,              // Monster type (e.g., "poring", "lunatic")
    val position: Position,              // Spawn position (must be walkable)
    val respawnTime: Int = 5000,         // Respawn time in milliseconds
    val maxCount: Int = 1,               // Maximum monsters at this spawn point
    val spawnRadius: Int = 0             // Random spawn radius (0 = exact position)
)
```

### Position

```kotlin
@Serializable
data class Position(
    val x: Int,                          // X coordinate (0 to width-1)
    val y: Int                           // Y coordinate (0 to height-1)
)
```

---

## Map Storage

### Storage Location

Maps are stored in the `zone-server/src/main/resources/maps/` directory as JSON files.

**File Naming Convention:**
- Format: `{map-name}.json`
- Example: `prontera.json`, `payon.json`
- Must be valid JSON and match the MapData structure

### Storage Format

Maps are stored as JSON files using Kotlinx Serialization:

```json
{
  "name": "prontera",
  "displayName": "Prontera - Capital City",
  "description": "The capital city of Rune-Midgard",
  "width": 1000,
  "height": 1000,
  "tileSize": 32,
  "collisionMap": {
    "width": 1000,
    "height": 1000,
    "data": "base64-encoded-bitmap-string..."
  },
  "spawnPoints": [
    { "x": 150, "y": 150 },
    { "x": 160, "y": 160 },
    { "x": 170, "y": 170 }
  ],
  "npcs": [
    {
      "id": "npc-1",
      "name": "Merchant",
      "position": { "x": 200, "y": 200 },
      "type": "merchant",
      "direction": "down",
      "properties": {
        "shopType": "weapon"
      }
    }
  ],
  "monsterSpawns": [
    {
      "id": "spawn-1",
      "monsterType": "poring",
      "position": { "x": 500, "y": 500 },
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

### Map Storage Service

```kotlin
class MapStorageService {
    private val mapsDirectory = Paths.get("src/main/resources/maps")
    
    /**
     * Load a map by name
     */
    fun loadMap(mapName: String): MapData? {
        val file = mapsDirectory.resolve("$mapName.json")
        if (!file.toFile().exists()) {
            return null
        }
        
        return try {
            val json = file.toFile().readText()
            Json.decodeFromString<MapData>(json)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Save a map
     */
    fun saveMap(mapData: MapData): Boolean {
        val file = mapsDirectory.resolve("${mapData.name}.json")
        
        return try {
            val json = Json.encodeToString(mapData)
            file.toFile().writeText(json)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * List all available maps
     */
    fun listMaps(): List<String> {
        return mapsDirectory.toFile()
            .listFiles { _, name -> name.endsWith(".json") }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }
    
    /**
     * Delete a map
     */
    fun deleteMap(mapName: String): Boolean {
        val file = mapsDirectory.resolve("$mapName.json")
        return file.toFile().delete()
    }
    
    /**
     * Check if a map exists
     */
    fun mapExists(mapName: String): Boolean {
        val file = mapsDirectory.resolve("$mapName.json")
        return file.toFile().exists()
    }
}
```

---

## Map Loading

### Loading Process

1. **Request**: ZoneService requests map by name
2. **Load**: MapStorageService loads map from JSON file
3. **Validate**: MapValidator validates map data
4. **Convert**: MapData converted to runtime CollisionMap
5. **Use**: MapData used to create ZoneInstance

### Integration with ZoneService

```kotlin
class ZoneService(
    val sessionValidationService: SessionValidationService,
    private val characterValidationService: CharacterValidationService,
    private val mapStorageService: MapStorageService,
    private val mapValidator: MapValidator
) {
    /**
     * Get map data for a zone name
     */
    private fun getMapDataForZone(zoneName: String): MapData? {
        val mapData = mapStorageService.loadMap(zoneName.lowercase())
        
        if (mapData == null) {
            return null
        }
        
        // Validate map
        val validationResult = mapValidator.validate(mapData)
        if (!validationResult.isValid) {
            logger.error("Map validation failed for $zoneName: ${validationResult.errors}")
            return null
        }
        
        return mapData
    }
}
```

---

## Map Validation

### Validation Rules

A map is valid if:

1. **Basic Validation**:
   - `name` is not empty and contains only alphanumeric characters and hyphens
   - `width` > 0 and `height` > 0
   - `tileSize` > 0

2. **Collision Map Validation**:
   - Collision map dimensions match map dimensions
   - Collision map data is valid Base64
   - Collision map decodes to correct size

3. **Spawn Points Validation**:
   - At least one spawn point exists
   - All spawn points are within map bounds
   - All spawn points are on walkable tiles

4. **NPC Validation**:
   - All NPC IDs are unique within the map
   - All NPC positions are within map bounds
   - All NPC positions are on walkable tiles
   - NPC directions are valid ("up", "down", "left", "right")

5. **Monster Spawn Validation** (if present):
   - All spawn IDs are unique
   - All spawn positions are within map bounds
   - All spawn positions are on walkable tiles
   - Respawn time > 0
   - Max count > 0

### MapValidator Implementation

```kotlin
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)

class MapValidator {
    fun validate(mapData: MapData): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Basic validation
        if (mapData.name.isBlank()) {
            errors.add("Map name cannot be empty")
        }
        if (!mapData.name.matches(Regex("[a-z0-9-]+"))) {
            errors.add("Map name must contain only lowercase letters, numbers, and hyphens")
        }
        if (mapData.width <= 0) {
            errors.add("Map width must be greater than 0")
        }
        if (mapData.height <= 0) {
            errors.add("Map height must be greater than 0")
        }
        if (mapData.tileSize <= 0) {
            errors.add("Tile size must be greater than 0")
        }
        
        // Collision map validation
        val collisionMap = try {
            CollisionMap.fromBase64(
                mapData.collisionMap.data,
                mapData.collisionMap.width,
                mapData.collisionMap.height
            )
        } catch (e: Exception) {
            errors.add("Invalid collision map data: ${e.message}")
            null
        }
        
        if (collisionMap != null) {
            if (collisionMap.width != mapData.width || collisionMap.height != mapData.height) {
                errors.add("Collision map dimensions must match map dimensions")
            }
        }
        
        // Spawn points validation
        if (mapData.spawnPoints.isEmpty()) {
            errors.add("Map must have at least one spawn point")
        }
        
        mapData.spawnPoints.forEachIndexed { index, spawn ->
            if (spawn.x < 0 || spawn.x >= mapData.width || 
                spawn.y < 0 || spawn.y >= mapData.height) {
                errors.add("Spawn point $index is out of bounds")
            } else if (collisionMap != null && !collisionMap.isWalkable(spawn.x, spawn.y)) {
                errors.add("Spawn point $index is not on a walkable tile")
            }
        }
        
        // NPC validation
        val npcIds = mutableSetOf<String>()
        mapData.npcs.forEach { npc ->
            if (npcIds.contains(npc.id)) {
                errors.add("Duplicate NPC ID: ${npc.id}")
            }
            npcIds.add(npc.id)
            
            if (npc.position.x < 0 || npc.position.x >= mapData.width ||
                npc.position.y < 0 || npc.position.y >= mapData.height) {
                errors.add("NPC ${npc.id} is out of bounds")
            } else if (collisionMap != null && !collisionMap.isWalkable(npc.position.x, npc.position.y)) {
                errors.add("NPC ${npc.id} is not on a walkable tile")
            }
            
            if (npc.direction !in listOf("up", "down", "left", "right")) {
                errors.add("NPC ${npc.id} has invalid direction: ${npc.direction}")
            }
        }
        
        // Monster spawn validation
        mapData.monsterSpawns?.forEach { spawn ->
            if (spawn.position.x < 0 || spawn.position.x >= mapData.width ||
                spawn.position.y < 0 || spawn.position.y >= mapData.height) {
                errors.add("Monster spawn ${spawn.id} is out of bounds")
            } else if (collisionMap != null && !collisionMap.isWalkable(spawn.position.x, spawn.position.y)) {
                errors.add("Monster spawn ${spawn.id} is not on a walkable tile")
            }
            
            if (spawn.respawnTime <= 0) {
                errors.add("Monster spawn ${spawn.id} has invalid respawn time")
            }
            if (spawn.maxCount <= 0) {
                errors.add("Monster spawn ${spawn.id} has invalid max count")
            }
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}
```

---

## Map API

### REST Endpoints

#### GET /api/maps
List all available maps.

**Response:**
```json
{
  "success": true,
  "data": ["prontera", "payon", "geffen"]
}
```

#### GET /api/maps/{mapName}
Get map data by name.

**Response:**
```json
{
  "success": true,
  "data": {
    "name": "prontera",
    "displayName": "Prontera - Capital City",
    ...
  }
}
```

#### POST /api/maps
Create a new map.

**Request Body:** MapData JSON

**Response:**
```json
{
  "success": true,
  "message": "Map created successfully"
}
```

#### PUT /api/maps/{mapName}
Update an existing map.

**Request Body:** MapData JSON

**Response:**
```json
{
  "success": true,
  "message": "Map updated successfully"
}
```

#### DELETE /api/maps/{mapName}
Delete a map.

**Response:**
```json
{
  "success": true,
  "message": "Map deleted successfully"
}
```

#### POST /api/maps/{mapName}/validate
Validate a map without saving.

**Request Body:** MapData JSON

**Response:**
```json
{
  "success": true,
  "data": {
    "isValid": true,
    "errors": []
  }
}
```

---

## Level Editor Integration

### Editor Features

The level editor provides:

1. **Visual Map Editor**:
   - Grid-based tile editing
   - Collision map painting (walkable/blocked)
   - Visual feedback for collision tiles

2. **Entity Placement**:
   - Place NPCs on the map
   - Set NPC properties (type, direction, etc.)
   - Place spawn points
   - Place monster spawns

3. **Map Properties**:
   - Set map name, dimensions
   - Configure tile size
   - Add map description

4. **Save/Load**:
   - Save map to server
   - Load existing maps for editing
   - Validate before saving

### Editor API Integration

The level editor communicates with the Map API endpoints to:
- Load existing maps
- Save new maps
- Update existing maps
- Validate maps before saving

---

## Implementation Details

### File Structure

```
zone-server/
├── src/main/kotlin/net/kibotu/ro/zone/
│   ├── model/
│   │   ├── MapData.kt          # Map data models
│   │   └── CollisionMap.kt     # Collision map implementation
│   ├── service/
│   │   ├── MapStorageService.kt # Map file I/O
│   │   └── MapValidator.kt      # Map validation
│   └── routes/
│       └── MapRoutes.kt        # Map API endpoints
└── src/main/resources/
    └── maps/                   # Map JSON files
        ├── prontera.json
        ├── payon.json
        └── ...
```

### Dependencies

- **Kotlinx Serialization**: JSON serialization/deserialization
- **Kotlinx Datetime**: Timestamp handling
- **Ktor**: HTTP server and routing

### Error Handling

- Invalid map data: Return validation errors
- Missing map: Return 404 Not Found
- Storage errors: Return 500 Internal Server Error with error message
- Invalid requests: Return 400 Bad Request with error details

### Performance Considerations

- Maps are loaded on-demand when zone instances are created
- Collision maps are decoded once and cached in memory
- Large maps (>1000x1000) may require optimization (chunking, compression)

---

## Migration from Hardcoded Maps

### Current State

Maps are currently hardcoded in `ZoneService.initializeDefaultZones()`.

### Migration Steps

1. Extract hardcoded maps to JSON files
2. Update ZoneService to use MapStorageService
3. Remove hardcoded map initialization
4. Test map loading and validation

### Example Migration

**Before:**
```kotlin
private fun initializeDefaultZones() {
    val pronteraMap = MapData(
        name = "prontera",
        width = 1000,
        height = 1000,
        ...
    )
    // ...
}
```

**After:**
```kotlin
init {
    // Maps are loaded on-demand when zones are created
    // No initialization needed
}
```

---

**Document Version:** 1.0  
**Last Updated:** 2024  
**Maintainer:** Development Team

