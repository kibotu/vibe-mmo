package net.kibotu.ro.account.service

import kotlinx.serialization.Serializable
import net.kibotu.ro.account.database.Account
import net.kibotu.ro.account.database.Session
import net.kibotu.ro.account.database.Sessions
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class AdminService {
    /**
     * Get all accounts
     */
    fun getAllAccounts(): List<net.kibotu.ro.account.model.AccountDTO> {
        return transaction {
            Account.all().map { it.toDTO() }
        }
    }
    
    /**
     * Get all sessions
     */
    fun getAllSessions(): List<SessionInfo> {
        return transaction {
            Session.all().map { session ->
                SessionInfo(
                    id = session.id.value.toString(),
                    accountId = session.accountId.toString(),
                    token = session.token.take(20) + "...", // Truncate token for display
                    createdAt = session.createdAt.toString(),
                    expiresAt = session.expiresAt.toString(),
                    isActive = session.isActive,
                    isExpired = session.isExpired()
                )
            }
        }
    }
    
    /**
     * Get server statistics
     */
    fun getStatistics(): ServerStatistics {
        return transaction {
            val totalAccounts = Account.count().toInt()
            val totalSessions = Session.count().toInt()
            val activeSessions = Session.find { Sessions.isActive eq true }.count().toInt()
            val expiredSessions = Session.find { Sessions.isActive eq false }.count().toInt()
            
            val recentAccounts = Account.all()
                .sortedByDescending { it.createdAt }
                .take(10)
                .map { it.toDTO() }
            
            ServerStatistics(
                totalAccounts = totalAccounts,
                totalSessions = totalSessions,
                activeSessions = activeSessions,
                expiredSessions = expiredSessions,
                recentAccounts = recentAccounts
            )
        }
    }
    
    /**
     * Delete an account by ID
     */
    fun deleteAccount(accountId: String): Boolean {
        return transaction {
            try {
                val account = Account.findById(UUID.fromString(accountId))
                if (account != null) {
                    // Delete all sessions for this account first
                    Session.find { Sessions.accountId eq account.id.value }.forEach { it.delete() }
                    account.delete()
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Invalidate a session by ID
     */
    fun invalidateSession(sessionId: String): Boolean {
        return transaction {
            try {
                val session = Session.findById(UUID.fromString(sessionId))
                if (session != null) {
                    session.isActive = false
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Invalidate all sessions for an account
     */
    fun invalidateAllSessionsForAccount(accountId: String): Int {
        return transaction {
            try {
                val account = Account.findById(UUID.fromString(accountId))
                if (account != null) {
                    val sessions = Session.find { Sessions.accountId eq account.id.value }
                    var count = 0
                    sessions.forEach {
                        it.isActive = false
                        count++
                    }
                    count
                } else {
                    0
                }
            } catch (e: Exception) {
                0
            }
        }
    }
}

@Serializable
data class SessionInfo(
    val id: String,
    val accountId: String,
    val token: String,
    val createdAt: String,
    val expiresAt: String,
    val isActive: Boolean,
    val isExpired: Boolean
)

@Serializable
data class ServerStatistics(
    val totalAccounts: Int,
    val totalSessions: Int,
    val activeSessions: Int,
    val expiredSessions: Int,
    val recentAccounts: List<net.kibotu.ro.account.model.AccountDTO>
)

