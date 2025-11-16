package net.kibotu.ro.account.database

import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object Accounts : UUIDTable("accounts") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = long("created_at")
    val lastLogin = long("last_login").nullable()
}

object Sessions : UUIDTable("sessions") {
    val accountId = uuid("account_id").references(Accounts.id)
    val token = varchar("token", 512).uniqueIndex()
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val isActive = bool("is_active").default(true)
}

