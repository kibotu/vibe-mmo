package net.kibotu.ro.dbviewer.database

import org.jetbrains.exposed.dao.id.UUIDTable

// Replicate table definitions from character-server
object Characters : UUIDTable("characters") {
    val accountId = uuid("account_id")
    val name = varchar("name", 50).uniqueIndex()
    val createdAt = long("created_at")
}

