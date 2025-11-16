package net.kibotu.ro.zone.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class SessionValidationService(
    private val accountServerUrl: String
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    @kotlinx.serialization.Serializable
    data class ValidateRequest(
        val sessionToken: String
    )
    
    @kotlinx.serialization.Serializable
    data class ValidateResponse(
        val success: Boolean,
        val data: ValidateData? = null,
        val error: String? = null
    )
    
    @kotlinx.serialization.Serializable
    data class ValidateData(
        val valid: Boolean,
        val userId: String? = null,
        val expiresAt: String? = null
    )
    
    /**
     * Validate a session token with the account server
     */
    suspend fun validateSession(token: String): ValidationResult {
        return try {
            val response: ValidateResponse = client.post("$accountServerUrl/api/account/validate") {
                contentType(ContentType.Application.Json)
                setBody(ValidateRequest(sessionToken = token))
            }.body()
            
            if (response.success && response.data?.valid == true && response.data.userId != null) {
                ValidationResult.Valid(response.data.userId)
            } else {
                ValidationResult.Invalid(response.error ?: "Invalid session")
            }
        } catch (e: Exception) {
            ValidationResult.Invalid("Failed to validate session: ${e.message}")
        }
    }
    
    fun close() {
        client.close()
    }
}

sealed class ValidationResult {
    data class Valid(val userId: String) : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}

