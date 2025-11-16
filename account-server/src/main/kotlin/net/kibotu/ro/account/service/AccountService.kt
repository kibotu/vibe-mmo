package net.kibotu.ro.account.service

import net.kibotu.ro.account.database.Account
import net.kibotu.ro.account.database.Session
import net.kibotu.ro.account.database.Sessions
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class AccountService(
    private val passwordService: PasswordService,
    private val jwtService: JwtService
) {
    /**
     * Register a new account
     */
    fun register(email: String, password: String): RegisterResult {
        // Validate input
        val emailValidation = passwordService.validateEmail(email)
        if (!emailValidation.isValid) {
            return RegisterResult.Error(emailValidation.errorMessage ?: "Invalid email")
        }
        
        val passwordValidation = passwordService.validatePassword(password)
        if (!passwordValidation.isValid) {
            return RegisterResult.Error(passwordValidation.errorMessage ?: "Invalid password")
        }
        
        return transaction {
            // Check if email already exists
            val existingAccount = Account.find { net.kibotu.ro.account.database.Accounts.email eq email.lowercase() }.firstOrNull()
            if (existingAccount != null) {
                return@transaction RegisterResult.Error("Email already exists")
            }
            
            // Create new account
            val account = Account.new {
                this.email = email.lowercase()
                this.passwordHash = passwordService.hashPassword(password)
                this.createdAt = Instant.now()
                this.lastLogin = null
            }
            
            RegisterResult.Success(account.id.value.toString())
        }
    }
    
    /**
     * Login and create a session
     * Automatically invalidates all other sessions for this account to ensure single-session login
     */
    fun login(email: String, password: String): LoginResult {
        return transaction {
            val account = Account.find { net.kibotu.ro.account.database.Accounts.email eq email.lowercase() }.firstOrNull()
            
            if (account == null) {
                return@transaction LoginResult.Error("Invalid credentials")
            }
            
            if (!passwordService.verifyPassword(password, account.passwordHash)) {
                return@transaction LoginResult.Error("Invalid credentials")
            }
            
            // Invalidate all existing sessions for this account (single-session enforcement)
            val existingSessions = Session.find { Sessions.accountId eq account.id.value }
            existingSessions.forEach { session ->
                session.isActive = false
            }
            
            // Update last login
            account.lastLogin = Instant.now()
            
            // Generate JWT token
            val token = jwtService.generateToken(account.id.value.toString())
            val expiresAt = jwtService.getExpirationTime()
            
            // Create new session record
            val session = Session.new {
                this.accountId = account.id.value
                this.token = token
                this.createdAt = Instant.now()
                this.expiresAt = expiresAt
                this.isActive = true
            }
            
            LoginResult.Success(
                sessionToken = token,
                userId = account.id.value.toString(),
                expiresAt = expiresAt
            )
        }
    }
    
    /**
     * Validate a session token
     */
    fun validateSession(token: String): ValidateResult {
        // First validate JWT
        val jwtValidation = jwtService.validateToken(token)
        if (!jwtValidation.isValid) {
            return ValidateResult.Invalid("Invalid token")
        }
        
        return transaction {
            // Check if session exists and is active
            val session = Session.find {
                Sessions.token eq token and (Sessions.isActive eq true)
            }.firstOrNull()
            
            if (session == null) {
                return@transaction ValidateResult.Invalid("Session not found")
            }
            
            if (session.isExpired()) {
                // Mark session as inactive
                session.isActive = false
                return@transaction ValidateResult.Invalid("Session expired")
            }
            
            ValidateResult.Valid(
                userId = session.accountId.toString(),
                expiresAt = session.expiresAt
            )
        }
    }
    
    /**
     * Get account by ID
     */
    fun getAccountById(accountId: String): Account? {
        return transaction {
            try {
                Account.findById(UUID.fromString(accountId))
            } catch (e: Exception) {
                null
            }
        }
    }
}

sealed class RegisterResult {
    data class Success(val userId: String) : RegisterResult()
    data class Error(val message: String) : RegisterResult()
}

sealed class LoginResult {
    data class Success(
        val sessionToken: String,
        val userId: String,
        val expiresAt: Instant
    ) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

sealed class ValidateResult {
    data class Valid(val userId: String, val expiresAt: Instant) : ValidateResult()
    data class Invalid(val message: String) : ValidateResult()
}

