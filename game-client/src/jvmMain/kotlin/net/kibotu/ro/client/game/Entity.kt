package net.kibotu.ro.client.game

import net.kibotu.ro.client.model.Position

/**
 * Base class for all game entities
 */
abstract class Entity(
    open val id: String,
    open var position: Position,
    open var direction: String = "down"
) {
    companion object {
        const val DIRECTION_UP = "up"
        const val DIRECTION_DOWN = "down"
        const val DIRECTION_LEFT = "left"
        const val DIRECTION_RIGHT = "right"
    }
}

/**
 * Player entity
 */
data class PlayerEntity(
    override val id: String,
    val characterId: String,
    val name: String,
    override var position: Position,
    override var direction: String = Entity.DIRECTION_DOWN,
    val isOwnPlayer: Boolean = false
) : Entity(id, position, direction)

/**
 * NPC entity
 */
data class NPCEntity(
    override val id: String,
    val name: String,
    override var position: Position,
    override var direction: String = Entity.DIRECTION_DOWN,
    val type: String
) : Entity(id, position, direction)

/**
 * Monster entity
 */
data class MonsterEntity(
    override val id: String,
    val type: String,
    override var position: Position,
    override var direction: String = Entity.DIRECTION_DOWN,
    var health: Int = 100,
    var maxHealth: Int = 100
) : Entity(id, position, direction)

