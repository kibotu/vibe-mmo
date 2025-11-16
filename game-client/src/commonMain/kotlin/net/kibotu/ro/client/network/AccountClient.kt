package net.kibotu.ro.client.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import net.kibotu.ro.client.model.*

class AccountClient(
    private val baseUrl: String = "http://localhost:28080"
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
    
    suspend fun register(email: String, password: String): Result<RegisterResponse> {
        return try {
            val response = client.post("$baseUrl/api/account/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email, password))
            }
            
            val apiResponse = response.body<ApiResponse<RegisterResponse>>()
            
            // Check if response is successful
            if (response.status.isSuccess() && apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data)
            } else {
                // Extract error message from response
                Result.failure(Exception(apiResponse.error ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Registration failed: ${e.message ?: e.toString()}"))
        }
    }
    
    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val response = client.post("$baseUrl/api/account/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
            
            val apiResponse = response.body<ApiResponse<LoginResponse>>()
            
            // Check if response is successful
            if (response.status.isSuccess() && apiResponse.success && apiResponse.data != null) {
                Result.success(apiResponse.data)
            } else {
                // Extract error message from response
                Result.failure(Exception(apiResponse.error ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Login failed: ${e.message ?: e.toString()}"))
        }
    }
    
    fun close() {
        client.close()
    }
}

