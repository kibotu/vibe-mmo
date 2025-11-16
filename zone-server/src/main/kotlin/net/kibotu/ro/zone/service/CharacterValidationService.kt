package net.kibotu.ro.zone.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class CharacterValidationService(
    private val characterServerUrl: String
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
    data class CharacterResponse(
        val success: Boolean,
        val data: CharacterData? = null,
        val error: String? = null
    )
    
    @kotlinx.serialization.Serializable
    data class CharacterData(
        val character: CharacterInfo
    )
    
    @kotlinx.serialization.Serializable
    data class CharacterInfo(
        val id: String,
        val accountId: String,
        val name: String,
        val createdAt: String
    )
    
    /**
     * Validate a character exists and belongs to the account
     */
    suspend fun validateCharacter(characterId: String, sessionToken: String): CharacterValidationResult {
        return try {
            val response: CharacterResponse = client.get("$characterServerUrl/api/character/$characterId") {
                header("Authorization", "Bearer $sessionToken")
            }.body()
            
            if (response.success && response.data != null) {
                CharacterValidationResult.Valid(response.data.character)
            } else {
                CharacterValidationResult.Invalid(response.error ?: "Character not found")
            }
        } catch (e: Exception) {
            CharacterValidationResult.Invalid("Failed to validate character: ${e.message}")
        }
    }
    
    fun close() {
        client.close()
    }
}

sealed class CharacterValidationResult {
    data class Valid(val character: CharacterValidationService.CharacterInfo) : CharacterValidationResult()
    data class Invalid(val message: String) : CharacterValidationResult()
}

