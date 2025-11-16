package net.kibotu.ro.zone

import com.typesafe.config.ConfigFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import net.kibotu.ro.zone.routes.zoneRoutes
import net.kibotu.ro.zone.service.CharacterValidationService
import net.kibotu.ro.zone.service.SessionValidationService
import net.kibotu.ro.zone.service.ZoneService
import net.kibotu.ro.zone.websocket.WebSocketHandler

private fun loadConfigFromFile(): ApplicationConfig {
    return try {
        val resource = Application::class.java.classLoader.getResource("application.conf")
        if (resource != null) {
            val hoconConfig = ConfigFactory.parseURL(resource).resolve()
            HoconApplicationConfig(hoconConfig)
        } else {
            // Fallback: create minimal config
            MapApplicationConfig().apply {
                put("ktor.accountServer.url", "http://localhost:28080")
                put("ktor.characterServer.url", "http://localhost:28081")
            }
        }
    } catch (e: Exception) {
        System.err.println("Warning: Could not load application.conf, using defaults: ${e.message}")
        MapApplicationConfig().apply {
            put("ktor.accountServer.url", "http://localhost:28080")
            put("ktor.characterServer.url", "http://localhost:28081")
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 28082
    val host = System.getenv("HOST") ?: "0.0.0.0"
    
    try {
        embeddedServer(Netty, port = port, host = host, module = Application::module)
            .start(wait = true)
    } catch (e: java.net.BindException) {
        System.err.println("ERROR: Port $port is already in use.")
        System.err.println("")
        System.err.println("Zone Server allows only ONE instance per machine.")
        System.err.println("Please stop the existing Zone Server before starting a new one.")
        System.err.println("")
        System.err.println("To find and stop the process:")
        System.err.println("  netstat -ano | findstr :$port")
        System.err.println("  taskkill /PID <PID> /F")
        System.exit(1)
    }
}

fun Application.module() {
    // Load config from application.conf file
    val config = loadConfigFromFile()
    
    // Get server URLs from config
    val accountServerUrl = config.property("ktor.accountServer.url").getString()
    val characterServerUrl = config.property("ktor.characterServer.url").getString()
    
    // Initialize services
    val sessionValidationService = SessionValidationService(accountServerUrl)
    val characterValidationService = CharacterValidationService(characterServerUrl)
    val zoneService = ZoneService(sessionValidationService, characterValidationService)
    val webSocketHandler = WebSocketHandler(zoneService)
    
    // Configure content negotiation
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    // Install WebSockets plugin
    install(WebSockets)
    
    // Configure routing
    routing {
        get("/") {
            call.respondText("Ragnarok Zone Server - Running")
        }
        
        zoneRoutes(zoneService)
        webSocketHandler.setupRoutes(this)
    }
}

