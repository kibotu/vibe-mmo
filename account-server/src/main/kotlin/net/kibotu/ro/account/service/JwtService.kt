package net.kibotu.ro.account.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.config.*
import java.time.Instant
import java.util.*

class JwtService(config: ApplicationConfig) {
    private val jwtConfig = config.config("ktor.jwt")
    private val secret = jwtConfig.property("secret").getString()
    private val issuer = jwtConfig.property("issuer").getString()
    private val audience = jwtConfig.property("audience").getString()
    private val expirationHours = jwtConfig.property("expirationHours").getString().toLong()
    
    private val algorithm = Algorithm.HMAC256(secret)
    
    /**
     * Generate a JWT token for a user
     */
    fun generateToken(userId: String): String {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(expirationHours * 3600)
        
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)
    }
    
    /**
     * Validate a JWT token and extract user ID
     */
    fun validateToken(token: String): TokenValidationResult {
        return try {
            val verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
            
            val decoded = verifier.verify(token)
            val userId = decoded.subject
            val expiresAt = decoded.expiresAt?.toInstant()
            
            TokenValidationResult(
                isValid = true,
                userId = userId,
                expiresAt = expiresAt
            )
        } catch (e: Exception) {
            TokenValidationResult(
                isValid = false,
                userId = null,
                expiresAt = null,
                error = e.message
            )
        }
    }
    
    /**
     * Get expiration time for a token (in hours from now)
     */
    fun getExpirationTime(): Instant {
        return Instant.now().plusSeconds(expirationHours * 3600)
    }
}

data class TokenValidationResult(
    val isValid: Boolean,
    val userId: String?,
    val expiresAt: Instant?,
    val error: String? = null
)

