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
    
    suspend fun getAvailableMaps(): Result<List<String>> {
        return try {
            val response = client.get("$baseUrl/api/maps")
            val apiResponse = response.body<ApiResponse<List<String>>>()
            
            if (apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Failed to get available maps"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMapData(mapName: String): Result<ServerMapData> {
        return try {
            val response = client.get("$baseUrl/api/maps/$mapName")
            val apiResponse = response.body<ApiResponse<ServerMapData>>()
            
            if (apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data)
            } else {
                Result.failure(Exception(apiResponse.error ?: "Failed to get map data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun joinZone(sessionToken: String, characterId: String, zoneName: String): Result<JoinZoneResponse> {
        return try {
            val response = client.post("$baseUrl/api/zone/join") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $sessionToken")
                setBody(JoinZoneRequest(characterId, zoneName))
            }
            
            // Check for 401 Unauthorized (invalid session)
            if (response.status == HttpStatusCode.Unauthorized) {
                return Result.failure(SessionInvalidException("Session expired or invalid"))
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

