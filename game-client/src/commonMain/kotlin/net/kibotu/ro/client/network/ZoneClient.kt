package net.kibotu.ro.client.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import net.kibotu.ro.client.model.*

class ZoneClient(
    private val baseUrl: String = "http://localhost:28082"
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
    
    suspend fun joinZone(sessionToken: String, characterId: String, zoneName: String): Result<JoinZoneResponse> {
        return try {
            val response = client.post("$baseUrl/api/zone/join") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $sessionToken")
                setBody(JoinZoneRequest(characterId, zoneName))
            }
            
            val apiResponse = response.body<ApiResponse<JoinZoneResponse>>()
            
            if (apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Failed to join zone"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun close() {
        client.close()
    }
}

