package net.kibotu.ro.zone

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.kibotu.ro.zone.routes.zoneRoutes
import net.kibotu.ro.zone.service.CharacterValidationService
import net.kibotu.ro.zone.service.SessionValidationService
import net.kibotu.ro.zone.service.ZoneService
import net.kibotu.ro.zone.websocket.WebSocketHandler

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8082
    val host = System.getenv("HOST") ?: "0.0.0.0"
    
    embeddedServer(Netty, port = port, host = host, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val config = environment.config
    
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
    
    // Configure routing
    routing {
        get("/") {
            call.respondText("Ragnarok Zone Server - Running")
        }
        
        zoneRoutes(zoneService)
        webSocketHandler.setupRoutes(this)
    }
}

