package net.kibotu.ro.account.database

import net.kibotu.ro.account.model.AccountDTO
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.time.Instant
import java.util.*

class Account(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Account>(Accounts)
    
    var email by Accounts.email
    var passwordHash by Accounts.passwordHash
    var createdAtLong by Accounts.createdAt
    var lastLoginLong by Accounts.lastLogin
    
    var createdAt: Instant
        get() = Instant.ofEpochMilli(createdAtLong)
        set(value) { createdAtLong = value.toEpochMilli() }
    
    var lastLogin: Instant?
        get() = lastLoginLong?.let { Instant.ofEpochMilli(it) }
        set(value) { lastLoginLong = value?.toEpochMilli() }
    
    fun toDTO() = AccountDTO(
        id = id.value.toString(),
        email = email,
        createdAt = createdAt.toString(),
        lastLogin = lastLogin?.toString()
    )
}

class Session(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Session>(Sessions)
    
    var accountId by Sessions.accountId
    var token by Sessions.token
    var createdAtLong by Sessions.createdAt
    var expiresAtLong by Sessions.expiresAt
    var isActive by Sessions.isActive
    
    var createdAt: Instant
        get() = Instant.ofEpochMilli(createdAtLong)
        set(value) { createdAtLong = value.toEpochMilli() }
    
    var expiresAt: Instant
        get() = Instant.ofEpochMilli(expiresAtLong)
        set(value) { expiresAtLong = value.toEpochMilli() }
    
    fun isExpired(): Boolean = expiresAt.isBefore(Instant.now())
}

