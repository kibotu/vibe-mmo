package net.kibotu.ro.dbviewer

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
import net.kibotu.ro.dbviewer.database.DatabaseConnections
import net.kibotu.ro.dbviewer.routes.databaseViewerRoutes
import net.kibotu.ro.dbviewer.service.DatabaseViewerService
import net.kibotu.ro.dbviewer.service.ZoneServerService
import java.io.File

private fun loadConfigFromFile(): ApplicationConfig {
    return try {
        val resource = Application::class.java.classLoader.getResource("application.conf")
        if (resource != null) {
            val hoconConfig = ConfigFactory.parseURL(resource).resolve()
            HoconApplicationConfig(hoconConfig)
        } else {
            // Fallback: create minimal config
            MapApplicationConfig().apply {
                put("ktor.databases.account.driver", "org.h2.Driver")
                put("ktor.databases.account.url", "jdbc:h2:file:./account-server/data/accountdb;AUTO_SERVER=TRUE;MODE=PostgreSQL")
                put("ktor.databases.account.user", "sa")
                put("ktor.databases.account.password", "")
                put("ktor.databases.character.driver", "org.h2.Driver")
                put("ktor.databases.character.url", "jdbc:h2:file:./character-server/data/characterdb;AUTO_SERVER=TRUE;MODE=PostgreSQL")
                put("ktor.databases.character.user", "sa")
                put("ktor.databases.character.password", "")
                put("ktor.servers.zoneServer.url", "http://localhost:28082")
            }
        }
    } catch (e: Exception) {
        println("Warning: Could not load application.conf, using defaults: ${e.message}")
        MapApplicationConfig().apply {
            put("ktor.databases.account.driver", "org.h2.Driver")
            put("ktor.databases.account.url", "jdbc:h2:file:./account-server/data/accountdb;AUTO_SERVER=TRUE;MODE=PostgreSQL")
            put("ktor.databases.account.user", "sa")
            put("ktor.databases.account.password", "")
            put("ktor.databases.character.driver", "org.h2.Driver")
            put("ktor.databases.character.url", "jdbc:h2:file:./character-server/data/characterdb;AUTO_SERVER=TRUE;MODE=PostgreSQL")
            put("ktor.databases.character.user", "sa")
            put("ktor.databases.character.password", "")
            put("ktor.servers.zoneServer.url", "http://localhost:28082")
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 28083
    val host = System.getenv("HOST") ?: "0.0.0.0"
    
    // Set working directory to project root (where Gradle runs from)
    // This ensures database paths resolve correctly
    val projectRoot = File(System.getProperty("user.dir"))
    if (projectRoot.name == "db-viewer-server") {
        // If running from db-viewer-server directory, go up to project root
        System.setProperty("user.dir", projectRoot.parent)
    }
    
    try {
        embeddedServer(Netty, port = port, host = host, module = Application::module)
            .start(wait = true)
    } catch (e: java.net.BindException) {
        System.err.println("ERROR: Port $port is already in use.")
        System.err.println("")
        System.err.println("Database Viewer Server allows only ONE instance per machine.")
        System.err.println("Please stop the existing Database Viewer Server before starting a new one.")
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
    
    // Initialize database connections
    DatabaseConnections.init(config)
    
    // Get server URLs from config
    val zoneServerUrl = config.property("ktor.servers.zoneServer.url").getString()
    
    // Initialize services
    val databaseViewerService = DatabaseViewerService()
    val zoneServerService = ZoneServerService(zoneServerUrl)
    
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
        get("/health") {
            call.respondText("Database Viewer Server - Running")
        }
        
        databaseViewerRoutes(databaseViewerService, zoneServerService)
    }
}

