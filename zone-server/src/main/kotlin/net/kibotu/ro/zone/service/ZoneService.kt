package net.kibotu.ro.zone.service

import net.kibotu.ro.zone.model.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ZoneService(
    val sessionValidationService: SessionValidationService,
    private val characterValidationService: CharacterValidationService,
    private val mapStorageService: MapStorageService,
    private val mapValidator: MapValidator
) {
    private val zoneInstances = ConcurrentHashMap<String, ZoneInstance>()
    private val mutex = Mutex()
    
    /**
     * Get all zone instances (for admin purposes)
     */
    fun getAllZoneInstances(): List<ZoneInstance> {
        return zoneInstances.values.toList()
    }
    
    // Maps are loaded on-demand when zones are created
    // No initialization needed
    
    /**
     * Join a zone with a character
     */
    suspend fun joinZone(
        characterId: String,
        zoneName: String,
        sessionToken: String
    ): JoinZoneResult {
        // Validate session
        val sessionValidation = sessionValidationService.validateSession(sessionToken)
        if (sessionValidation !is ValidationResult.Valid) {
            return JoinZoneResult.Error("Invalid session: ${(sessionValidation as ValidationResult.Invalid).message}")
        }
        
        // Validate character
        val characterValidation = characterValidationService.validateCharacter(characterId, sessionToken)
        if (characterValidation !is CharacterValidationResult.Valid) {
            return JoinZoneResult.Error("Invalid character: ${(characterValidation as CharacterValidationResult.Invalid).message}")
        }
        
        val character = characterValidation.character
        
        // Find or create zone instance
        val zoneInstance = mutex.withLock {
            // For now, find existing instance or create new one
            // In production, you might want to limit instances per zone
            val instance = zoneInstances.values.find { it.zoneName == zoneName }
            if (instance != null) {
                instance
            } else {
                // Create new instance for this zone
                val mapData = getMapDataForZone(zoneName) ?: return JoinZoneResult.Error("Unknown zone: $zoneName")
                val instanceId = UUID.randomUUID().toString()
                val newInstance = ZoneInstance(instanceId, zoneName, mapData)
                zoneInstances[instanceId] = newInstance
                newInstance
            }
        }
        
        // Add player to zone
        val spawnPosition = zoneInstance.getSpawnPosition()
        val player = Player(
            characterId = characterId,
            name = character.name,
            position = spawnPosition,
            direction = "down",
            sessionId = sessionToken
        )
        
        val added = zoneInstance.addPlayer(player)
        if (!added) {
            return JoinZoneResult.Error("Player already in zone")
        }
        
        return JoinZoneResult.Success(
            zoneInstanceId = zoneInstance.id,
            websocketUrl = "/ws/zone/${zoneInstance.id}",
            spawnPosition = spawnPosition,
            characterName = character.name
        )
    }
    
    /**
     * Get zone instance by ID
     */
    fun getZoneInstance(instanceId: String): ZoneInstance? {
        return zoneInstances[instanceId]
    }
    
    /**
     * Leave a zone
     */
    suspend fun leaveZone(instanceId: String, characterId: String): Boolean {
        val instance = zoneInstances[instanceId] ?: return false
        return instance.removePlayer(characterId) != null
    }
    
    /**
     * Create a zone instance for a map (admin function)
     */
    suspend fun createZoneInstance(zoneName: String): ZoneInstance? {
        return mutex.withLock {
            // Check if zone already exists
            val existing = zoneInstances.values.find { it.zoneName == zoneName }
            if (existing != null) {
                return@withLock existing
            }
            
            // Load map data
            val mapData = getMapDataForZone(zoneName) ?: return@withLock null
            
            // Create new instance
            val instanceId = UUID.randomUUID().toString()
            val newInstance = ZoneInstance(instanceId, zoneName, mapData)
            zoneInstances[instanceId] = newInstance
            newInstance
        }
    }
    
    /**
     * Get map data for a zone name
     */
    private fun getMapDataForZone(zoneName: String): net.kibotu.ro.zone.model.MapData? {
        val mapData = mapStorageService.loadMap(zoneName.lowercase())
        
        if (mapData == null) {
            return null
        }
        
        // Validate map
        val validationResult = mapValidator.validate(mapData)
        if (!validationResult.isValid) {
            // Log validation errors but don't fail - allow invalid maps for development
            System.err.println("Warning: Map validation failed for $zoneName:")
            validationResult.errors.forEach { error ->
                System.err.println("  - $error")
            }
        }
        
        return mapData
    }
}

sealed class JoinZoneResult {
    data class Success(
        val zoneInstanceId: String,
        val websocketUrl: String,
        val spawnPosition: Position,
        val characterName: String
    ) : JoinZoneResult()
    
    data class Error(val message: String) : JoinZoneResult()
}

