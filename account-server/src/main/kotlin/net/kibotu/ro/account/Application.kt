package net.kibotu.ro.account

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.kibotu.ro.account.database.DatabaseFactory
import net.kibotu.ro.account.routes.accountRoutes
import net.kibotu.ro.account.service.AccountService
import net.kibotu.ro.account.service.JwtService
import net.kibotu.ro.account.service.PasswordService

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    
    embeddedServer(Netty, port = port, host = host, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val config = environment.config
    
    // Initialize database
    DatabaseFactory.init(config)
    
    // Initialize services
    val passwordService = PasswordService
    val jwtService = JwtService(config)
    val accountService = AccountService(passwordService, jwtService)
    
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
        
        accountRoutes(accountService)
    }
}
