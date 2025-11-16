package net.kibotu.ro.account.service

import org.mindrot.jbcrypt.BCrypt

object PasswordService {
    private const val BCRYPT_ROUNDS = 12
    
    /**
     * Hash a password using BCrypt
     */
    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS))
    }
    
    /**
     * Verify a password against a hash
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        return try {
            BCrypt.checkpw(password, hash)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validate password strength
     */
    fun validatePassword(password: String): ValidationResult {
        if (password.length < 8) {
            return ValidationResult(false, "Password must be at least 8 characters long")
        }
        if (password.length > 128) {
            return ValidationResult(false, "Password must be less than 128 characters")
        }
        return ValidationResult(true, null)
    }
    
    /**
     * Validate email format
     */
    fun validateEmail(email: String): ValidationResult {
        if (email.isBlank()) {
            return ValidationResult(false, "Email cannot be empty")
        }
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
        if (!email.matches(emailRegex)) {
            return ValidationResult(false, "Invalid email format")
        }
        if (email.length > 255) {
            return ValidationResult(false, "Email must be less than 255 characters")
        }
        return ValidationResult(true, null)
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String?
)

