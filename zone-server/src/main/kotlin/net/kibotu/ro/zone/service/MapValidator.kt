package net.kibotu.ro.zone.service

import kotlinx.serialization.Serializable
import net.kibotu.ro.zone.model.CollisionMap
import net.kibotu.ro.zone.model.MapData

/**
 * Validation result for map data
 */
@Serializable
data class MapValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)

/**
 * Service for validating map data
 */
class MapValidator {
    fun validate(mapData: MapData): MapValidationResult {
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
                errors.add("Collision map dimensions (${collisionMap.width}x${collisionMap.height}) must match map dimensions (${mapData.width}x${mapData.height})")
            }
        }
        
        // Spawn points validation
        if (mapData.spawnPoints.isEmpty()) {
            errors.add("Map must have at least one spawn point")
        }
        
        mapData.spawnPoints.forEachIndexed { index, spawn ->
            if (spawn.x < 0 || spawn.x >= mapData.width || 
                spawn.y < 0 || spawn.y >= mapData.height) {
                errors.add("Spawn point $index ($spawn) is out of bounds")
            } else if (collisionMap != null && !collisionMap.isWalkable(spawn.x, spawn.y)) {
                errors.add("Spawn point $index ($spawn) is not on a walkable tile")
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
                errors.add("NPC ${npc.id} at position $npc.position is out of bounds")
            } else if (collisionMap != null && !collisionMap.isWalkable(npc.position.x, npc.position.y)) {
                errors.add("NPC ${npc.id} at position $npc.position is not on a walkable tile")
            }
            
            if (npc.direction !in listOf("up", "down", "left", "right")) {
                errors.add("NPC ${npc.id} has invalid direction: ${npc.direction}")
            }
        }
        
        // Monster spawn validation
        mapData.monsterSpawns?.forEach { spawn ->
            if (spawn.position.x < 0 || spawn.position.x >= mapData.width ||
                spawn.position.y < 0 || spawn.position.y >= mapData.height) {
                errors.add("Monster spawn ${spawn.id} at position ${spawn.position} is out of bounds")
            } else if (collisionMap != null && !collisionMap.isWalkable(spawn.position.x, spawn.position.y)) {
                errors.add("Monster spawn ${spawn.id} at position ${spawn.position} is not on a walkable tile")
            }
            
            if (spawn.respawnTime <= 0) {
                errors.add("Monster spawn ${spawn.id} has invalid respawn time: ${spawn.respawnTime}")
            }
            if (spawn.maxCount <= 0) {
                errors.add("Monster spawn ${spawn.id} has invalid max count: ${spawn.maxCount}")
            }
        }
        
        return MapValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}

