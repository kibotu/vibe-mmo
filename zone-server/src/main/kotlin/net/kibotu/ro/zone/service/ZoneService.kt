package net.kibotu.ro.zone.service

import net.kibotu.ro.zone.model.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ZoneService(
    val sessionValidationService: SessionValidationService,
    private val characterValidationService: CharacterValidationService
) {
    private val zoneInstances = ConcurrentHashMap<String, ZoneInstance>()
    private val mutex = Mutex()
    
    /**
     * Get all zone instances (for admin purposes)
     */
    fun getAllZoneInstances(): List<ZoneInstance> {
        return zoneInstances.values.toList()
    }
    
    init {
        // Initialize default zones
        initializeDefaultZones()
    }
    
    /**
     * Initialize default zone maps
     */
    private fun initializeDefaultZones() {
        // Prontera - default starting town
        val pronteraMap = MapData(
            name = "prontera",
            width = 1000,
            height = 1000,
            spawnPoints = listOf(
                Position(150, 150),
                Position(160, 160),
                Position(170, 170)
            ),
            npcs = listOf(
                NPC("npc-1", "Merchant", Position(200, 200), "merchant"),
                NPC("npc-2", "Guard", Position(300, 300), "guard")
            )
        )
        
        // Create a default instance for prontera
        val instanceId = UUID.randomUUID().toString()
        zoneInstances[instanceId] = ZoneInstance(instanceId, "prontera", pronteraMap)
    }
    
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
     * Get map data for a zone name
     */
    private fun getMapDataForZone(zoneName: String): MapData? {
        return when (zoneName.lowercase()) {
            "prontera" -> MapData(
                name = "prontera",
                width = 1000,
                height = 1000,
                spawnPoints = listOf(
                    Position(150, 150),
                    Position(160, 160),
                    Position(170, 170)
                ),
                npcs = listOf(
                    NPC("npc-1", "Merchant", Position(200, 200), "merchant"),
                    NPC("npc-2", "Guard", Position(300, 300), "guard")
                )
            )
            // Add more zones as needed
            else -> null
        }
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

