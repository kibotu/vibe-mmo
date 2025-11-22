package net.kibotu.ro.account

import com.typesafe.config.ConfigFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import net.kibotu.ro.account.database.DatabaseFactory
import net.kibotu.ro.account.routes.accountRoutes
import net.kibotu.ro.account.routes.adminRoutes
import net.kibotu.ro.account.service.AccountService
import net.kibotu.ro.account.service.AdminService
import net.kibotu.ro.account.service.JwtService
import net.kibotu.ro.account.service.PasswordService

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
                put("ktor.database.url", "jdbc:h2:mem:accountdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
                put("ktor.database.user", "sa")
                put("ktor.database.password", "")
                put("ktor.jwt.secret", "your-secret-key-change-in-production-min-256-bits")
                put("ktor.jwt.issuer", "ragnarok-account-server")
                put("ktor.jwt.audience", "ragnarok-clients")
                put("ktor.jwt.expirationHours", "24")
            }
        }
    } catch (e: Exception) {
        System.err.println("Warning: Could not load application.conf, using defaults: ${e.message}")
        MapApplicationConfig().apply {
            put("ktor.database.driver", "org.h2.Driver")
            put("ktor.database.url", "jdbc:h2:mem:accountdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            put("ktor.database.user", "sa")
            put("ktor.database.password", "")
            put("ktor.jwt.secret", "your-secret-key-change-in-production-min-256-bits")
            put("ktor.jwt.issuer", "ragnarok-account-server")
            put("ktor.jwt.audience", "ragnarok-clients")
            put("ktor.jwt.expirationHours", "24")
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 28080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    
    try {
        val server = embeddedServer(Netty, port = port, host = host, module = Application::module)
        server.start(wait = false)
        
        // Determine the admin URL
        val adminUrl = if (host == "0.0.0.0") {
            "http://localhost:$port/admin"
        } else {
            "http://$host:$port/admin"
        }
        
        println("")
        println("=".repeat(70))
        println("Account Server started successfully!")
        println("=".repeat(70))
        println("Server URL: http://$host:$port")
        println("Admin Panel: $adminUrl")
        println("=".repeat(70))
        println("")
        
        // Keep the main thread alive
        Thread.currentThread().join()
    } catch (e: java.net.BindException) {
        System.err.println("ERROR: Port $port is already in use.")
        System.err.println("")
        System.err.println("Account Server allows only ONE instance per machine.")
        System.err.println("Please stop the existing Account Server before starting a new one.")
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
    
    // Initialize services
    val passwordService = PasswordService
    val jwtService = JwtService(config)
    val accountService = AccountService(passwordService, jwtService)
    val adminService = AdminService()
    
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
            call.respondText("Ragnarok Account Server - Running")
        }
        
        // Admin panel - serve HTML file
        get("/admin") {
            val htmlContent = Application::class.java.classLoader.getResourceAsStream("admin/index.html")
                ?.bufferedReader()?.readText()
                ?: throw Exception("Admin HTML file not found")
            call.respondText(htmlContent, ContentType.Text.Html)
        }
        
        // API routes
        accountRoutes(accountService)
        adminRoutes(adminService)
    }
}



