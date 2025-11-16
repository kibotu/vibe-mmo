package net.kibotu.ro.dbviewer.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ZoneServerStatus(
    val status: String,
    val message: String,
    val zones: List<ZoneInfo> = emptyList()
)

@Serializable
data class ZoneInfo(
    val zoneId: String,
    val zoneName: String,
    val playerCount: Int = 0
)

class ZoneServerService(private val zoneServerUrl: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    suspend fun getStatus(): ZoneServerStatus {
        return try {
            val response = client.get("$zoneServerUrl/")
            val message = response.body<String>()
            ZoneServerStatus(
                status = "online",
                message = message,
                zones = emptyList() // Zone server doesn't expose zone info via HTTP yet
            )
        } catch (e: Exception) {
            ZoneServerStatus(
                status = "offline",
                message = "Zone server is not responding: ${e.message}",
                zones = emptyList()
            )
        }
    }
    
    fun close() {
        client.close()
    }
}

