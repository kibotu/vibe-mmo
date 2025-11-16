package net.kibotu.ro.client.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import net.kibotu.ro.client.model.*

/**
 * Exception thrown when session is invalid (401 Unauthorized)
 */
class SessionInvalidException(message: String) : Exception(message)

class CharacterClient(
    private val baseUrl: String = "http://localhost:28081"
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    suspend fun listCharacters(sessionToken: String): Result<List<CharacterDTO>> {
        return try {
            val response = client.get("$baseUrl/api/character/list") {
                header("Authorization", "Bearer $sessionToken")
            }
            
            // Check for 401 Unauthorized (invalid session)
            if (response.status == HttpStatusCode.Unauthorized) {
                return Result.failure(SessionInvalidException("Session expired or invalid"))
            }
            
            val apiResponse = response.body<ApiResponse<CharacterListResponse>>()
            
            if (response.status.isSuccess() && apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data.characters)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Failed to list characters"))
            }
        } catch (e: Exception) {
            if (e is SessionInvalidException) {
                Result.failure(e)
            } else {
                Result.failure(Exception("Failed to list characters: ${e.message ?: e.toString()}"))
            }
        }
    }
    
    suspend fun createCharacter(sessionToken: String, name: String): Result<CharacterDTO> {
        return try {
            val response = client.post("$baseUrl/api/character/create") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $sessionToken")
                setBody(CreateCharacterRequest(name))
            }
            
            // Check for 401 Unauthorized (invalid session)
            if (response.status == HttpStatusCode.Unauthorized) {
                return Result.failure(SessionInvalidException("Session expired or invalid"))
            }
            
            val apiResponse = response.body<ApiResponse<CharacterResponse>>()
            
            if (response.status.isSuccess() && apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data.character)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Failed to create character"))
            }
        } catch (e: Exception) {
            if (e is SessionInvalidException) {
                Result.failure(e)
            } else {
                Result.failure(Exception("Failed to create character: ${e.message ?: e.toString()}"))
            }
        }
    }
    
    suspend fun getCharacter(sessionToken: String, characterId: String): Result<CharacterDTO> {
        return try {
            val response = client.get("$baseUrl/api/character/$characterId") {
                header("Authorization", "Bearer $sessionToken")
            }
            
            // Check for 401 Unauthorized (invalid session)
            if (response.status == HttpStatusCode.Unauthorized) {
                return Result.failure(SessionInvalidException("Session expired or invalid"))
            }
            
            val apiResponse = response.body<ApiResponse<CharacterResponse>>()
            
            if (response.status.isSuccess() && apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data.character)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Character not found"))
            }
        } catch (e: Exception) {
            if (e is SessionInvalidException) {
                Result.failure(e)
            } else {
                Result.failure(Exception("Failed to get character: ${e.message ?: e.toString()}"))
            }
        }
    }
    
    fun close() {
        client.close()
    }
}

