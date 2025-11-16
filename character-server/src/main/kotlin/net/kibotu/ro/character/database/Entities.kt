package net.kibotu.ro.character.database

import net.kibotu.ro.character.model.CharacterDTO
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.time.Instant
import java.util.*

class Character(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Character>(Characters)
    
    var accountId by Characters.accountId
    var name by Characters.name
    var createdAtLong by Characters.createdAt
    
    var createdAt: Instant
        get() = Instant.ofEpochMilli(createdAtLong)
        set(value) { createdAtLong = value.toEpochMilli() }
    
    fun toDTO() = CharacterDTO(
        id = id.value.toString(),
        accountId = accountId.toString(),
        name = name,
        createdAt = createdAt.toString()
    )
}

