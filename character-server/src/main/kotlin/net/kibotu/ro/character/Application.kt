package net.kibotu.ro.character

import com.typesafe.config.ConfigFactory
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

private fun loadConfigFromFile(): ApplicationConfig {
    return try {
        val resource = Application::class.java.classLoader.getResource("application.conf")
        if (resource != null) {
            val hoconConfig = ConfigFactory.parseURL(resource).resolve()
            HoconApplicationConfig(hoconConfig)
        } else {
            // Fallback: create minimal config
            MapApplicationConfig().apply {
                put("ktor.database.driver", "org.h2.Driver")
                put("ktor.database.url", "jdbc:h2:mem:characterdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
                put("ktor.database.user", "sa")
                put("ktor.database.password", "")
                put("ktor.accountServer.url", "http://localhost:28080")
            }
        }
    } catch (e: Exception) {
        System.err.println("Warning: Could not load application.conf, using defaults: ${e.message}")
        MapApplicationConfig().apply {
            put("ktor.database.driver", "org.h2.Driver")
            put("ktor.database.url", "jdbc:h2:mem:characterdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            put("ktor.database.user", "sa")
            put("ktor.database.password", "")
            put("ktor.accountServer.url", "http://localhost:28080")
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 28081
    val host = System.getenv("HOST") ?: "0.0.0.0"
    
    try {
        embeddedServer(Netty, port = port, host = host, module = Application::module)
            .start(wait = true)
    } catch (e: java.net.BindException) {
        System.err.println("ERROR: Port $port is already in use.")
        System.err.println("")
        System.err.println("Character Server allows only ONE instance per machine.")
        System.err.println("Please stop the existing Character Server before starting a new one.")
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

