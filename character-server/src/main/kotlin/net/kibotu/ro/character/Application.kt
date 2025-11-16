package net.kibotu.ro.character

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.kibotu.ro.character.database.DatabaseFactory
import net.kibotu.ro.character.routes.characterRoutes
import net.kibotu.ro.character.service.CharacterService
import net.kibotu.ro.character.service.SessionValidationService

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
    val host = System.getenv("HOST") ?: "0.0.0.0"
    
    embeddedServer(Netty, port = port, host = host, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val config = environment.config
    
    // Initialize database
    DatabaseFactory.init(config)
    
    // Get account server URL from config
    val accountServerUrl = config.property("ktor.accountServer.url").getString()
    
    // Initialize services
    val sessionValidationService = SessionValidationService(accountServerUrl)
    val characterService = CharacterService(sessionValidationService)
    
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
            call.respondText("Ragnarok Character Server - Running")
        }
        
        characterRoutes(characterService)
    }
}

