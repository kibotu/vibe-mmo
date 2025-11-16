package net.kibotu.ro.dbviewer.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.kibotu.ro.dbviewer.database.Accounts
import net.kibotu.ro.dbviewer.database.Characters
import net.kibotu.ro.dbviewer.database.DatabaseConnections
import net.kibotu.ro.dbviewer.database.Sessions
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("net.kibotu.ro.dbviewer.service.DatabaseViewerService")

@Serializable
data class AccountView(
    val id: String,
    val email: String,
    val createdAt: Long,
    val lastLogin: Long?
)

@Serializable
data class SessionView(
    val id: String,
    val accountId: String,
    val token: String,
    val createdAt: Long,
    val expiresAt: Long,
    val isActive: Boolean
)

@Serializable
data class CharacterView(
    val id: String,
    val accountId: String,
    val name: String,
    val createdAt: Long
)

@Serializable
data class DatabaseStats(
    val accounts: Int,
    val sessions: Int,
    val activeSessions: Int,
    val characters: Int
)

class DatabaseViewerService {
    suspend fun getAccounts(): List<AccountView> = withContext(Dispatchers.IO) {
        val originalDb = TransactionManager.defaultDatabase
        try {
            TransactionManager.defaultDatabase = DatabaseConnections.accountDatabase
            transaction {
                try {
                    Accounts.selectAll().map {
                        AccountView(
                            id = it[Accounts.id].value.toString(),
                            email = it[Accounts.email],
                            createdAt = it[Accounts.createdAt],
                            lastLogin = it[Accounts.lastLogin]
                        )
                    }
                } catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
                    if (e.message?.contains("Table \"ACCOUNTS\" not found", ignoreCase = true) == true ||
                        e.message?.contains("does not exist", ignoreCase = true) == true) {
                        logger.warn("Accounts table does not exist yet. Returning empty list.")
                        emptyList()
                    } else {
                        logger.error("Error querying accounts table", e)
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error accessing account database: ${e.message}", e)
            // Return empty list instead of throwing to allow UI to load
            emptyList()
        } finally {
            TransactionManager.defaultDatabase = originalDb
        }
    }
    
    suspend fun getSessions(): List<SessionView> = withContext(Dispatchers.IO) {
        val originalDb = TransactionManager.defaultDatabase
        try {
            TransactionManager.defaultDatabase = DatabaseConnections.accountDatabase
            transaction {
                try {
                    Sessions.selectAll().map {
                        SessionView(
                            id = it[Sessions.id].value.toString(),
                            accountId = it[Sessions.accountId].toString(),
                            token = it[Sessions.token],
                            createdAt = it[Sessions.createdAt],
                            expiresAt = it[Sessions.expiresAt],
                            isActive = it[Sessions.isActive]
                        )
                    }
                } catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
                    if (e.message?.contains("Table \"SESSIONS\" not found", ignoreCase = true) == true ||
                        e.message?.contains("does not exist", ignoreCase = true) == true) {
                        logger.warn("Sessions table does not exist yet. Returning empty list.")
                        emptyList()
                    } else {
                        logger.error("Error querying sessions table", e)
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error accessing account database for sessions: ${e.message}", e)
            // Return empty list instead of throwing to allow UI to load
            emptyList()
        } finally {
            TransactionManager.defaultDatabase = originalDb
        }
    }
    
    suspend fun getCharacters(): List<CharacterView> = withContext(Dispatchers.IO) {
        val originalDb = TransactionManager.defaultDatabase
        try {
            TransactionManager.defaultDatabase = DatabaseConnections.characterDatabase
            transaction {
                try {
                    Characters.selectAll().map {
                        CharacterView(
                            id = it[Characters.id].value.toString(),
                            accountId = it[Characters.accountId].toString(),
                            name = it[Characters.name],
                            createdAt = it[Characters.createdAt]
                        )
                    }
                } catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
                    if (e.message?.contains("Table \"CHARACTERS\" not found", ignoreCase = true) == true ||
                        e.message?.contains("does not exist", ignoreCase = true) == true) {
                        logger.warn("Characters table does not exist yet. Returning empty list.")
                        emptyList()
                    } else {
                        logger.error("Error querying characters table", e)
                        throw e
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error accessing character database: ${e.message}", e)
            // Return empty list instead of throwing to allow UI to load
            emptyList()
        } finally {
            TransactionManager.defaultDatabase = originalDb
        }
    }
    
    suspend fun getStats(): DatabaseStats {
        val accounts = getAccounts()
        val sessions = getSessions()
        val characters = getCharacters()
        
        return DatabaseStats(
            accounts = accounts.size,
            sessions = sessions.size,
            activeSessions = sessions.count { it.isActive },
            characters = characters.size
        )
    }
}

