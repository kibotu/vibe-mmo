package net.kibotu.ro.client.game

/**
 * Manages all entities in the game world
 */
class EntityManager {
    private val players = mutableMapOf<String, PlayerEntity>()
    private val monsters = mutableMapOf<String, MonsterEntity>()
    private val npcs = mutableMapOf<String, NPCEntity>()
    
    /**
     * Get all entities sorted by Y position for depth rendering
     */
    fun getAllEntitiesSortedByY(): List<Entity> {
        return (players.values + monsters.values + npcs.values)
            .sortedBy { it.position.y }
    }
    
    /**
     * Get all players
     */
    fun getAllPlayers(): List<PlayerEntity> {
        return players.values.toList()
    }
    
    /**
     * Get all monsters
     */
    fun getAllMonsters(): List<MonsterEntity> {
        return monsters.values.toList()
    }
    
    /**
     * Get all NPCs
     */
    fun getAllNPCs(): List<NPCEntity> {
        return npcs.values.toList()
    }
    
    /**
     * Add or update a player
     */
    fun updatePlayer(player: PlayerEntity) {
        players[player.characterId] = player
    }
    
    /**
     * Remove a player
     */
    fun removePlayer(characterId: String) {
        players.remove(characterId)
    }
    
    /**
     * Get a player by character ID
     */
    fun getPlayer(characterId: String): PlayerEntity? {
        return players[characterId]
    }
    
    /**
     * Add or update a monster
     */
    fun updateMonster(monster: MonsterEntity) {
        monsters[monster.id] = monster
    }
    
    /**
     * Remove a monster
     */
    fun removeMonster(monsterId: String) {
        monsters.remove(monsterId)
    }
    
    /**
     * Add or update an NPC
     */
    fun updateNPC(npc: NPCEntity) {
        npcs[npc.id] = npc
    }
    
    /**
     * Remove an NPC
     */
    fun removeNPC(npcId: String) {
        npcs.remove(npcId)
    }
    
    /**
     * Clear all entities
     */
    fun clear() {
        players.clear()
        monsters.clear()
        npcs.clear()
    }
    
    /**
     * Update entities from server state
     */
    fun updateFromServerState(
        players: List<net.kibotu.ro.client.model.PlayerDTO>,
        npcs: List<net.kibotu.ro.client.model.NPCDTO>,
        monsters: List<net.kibotu.ro.client.model.MonsterDTO>,
        ownCharacterId: String?
    ) {
        // Update players
        val playerIds = players.map { it.characterId }.toSet()
        this.players.keys.removeAll { it !in playerIds }
        
        players.forEach { playerDTO ->
            val existing = this.players[playerDTO.characterId]
            this.players[playerDTO.characterId] = PlayerEntity(
                id = playerDTO.characterId,
                characterId = playerDTO.characterId,
                name = playerDTO.name,
                position = playerDTO.position,
                direction = playerDTO.direction,
                isOwnPlayer = playerDTO.characterId == ownCharacterId
            )
        }
        
        // Update NPCs
        val npcIds = npcs.map { it.id }.toSet()
        this.npcs.keys.removeAll { it !in npcIds }
        
        npcs.forEach { npcDTO ->
            this.npcs[npcDTO.id] = NPCEntity(
                id = npcDTO.id,
                name = npcDTO.name,
                position = npcDTO.position,
                direction = Entity.DIRECTION_DOWN,
                type = npcDTO.type
            )
        }
        
        // Update monsters
        val monsterIds = monsters.map { it.id }.toSet()
        this.monsters.keys.removeAll { it !in monsterIds }
        
        monsters.forEach { monsterDTO ->
            this.monsters[monsterDTO.id] = MonsterEntity(
                id = monsterDTO.id,
                type = monsterDTO.type,
                position = monsterDTO.position,
                direction = Entity.DIRECTION_DOWN,
                health = monsterDTO.health,
                maxHealth = 100 // Default max health
            )
        }
    }
}

