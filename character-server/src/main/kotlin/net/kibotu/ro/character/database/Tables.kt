package net.kibotu.ro.character.database

import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object Characters : UUIDTable("characters") {
    val accountId = uuid("account_id")
    val name = varchar("name", 50).uniqueIndex()
    val createdAt = long("created_at")
}

