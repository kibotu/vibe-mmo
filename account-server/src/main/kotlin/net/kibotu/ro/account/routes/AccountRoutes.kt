package net.kibotu.ro.account.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kibotu.ro.account.model.*
import net.kibotu.ro.account.service.AccountService

fun Route.accountRoutes(accountService: AccountService) {
    route("/api/account") {
        // Register endpoint
        post("/register") {
            try {
                val request = call.receive<RegisterRequest>()
                
                when (val result = accountService.register(request.email, request.password)) {
                    is net.kibotu.ro.account.service.RegisterResult.Success -> {
                        call.respond(
                            HttpStatusCode.Created,
                            ApiResponse(
                                success = true,
                                data = RegisterResponse(
                                    userId = result.userId,
                                    message = "Account created successfully"
                                )
                            )
                        )
                    }
                    is net.kibotu.ro.account.service.RegisterResult.Error -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<RegisterResponse>(
                                success = false,
                                error = result.message
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<RegisterResponse>(
                        success = false,
                        error = "Invalid request: ${e.message}"
                    )
                )
            }
        }
        
        // Login endpoint
        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                
                when (val result = accountService.login(request.email, request.password)) {
                    is net.kibotu.ro.account.service.LoginResult.Success -> {
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(
                                success = true,
                                data = LoginResponse(
                                    sessionToken = result.sessionToken,
                                    userId = result.userId,
                                    expiresAt = result.expiresAt.toString()
                                )
                            )
                        )
                    }
                    is net.kibotu.ro.account.service.LoginResult.Error -> {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiResponse<LoginResponse>(
                                success = false,
                                error = result.message
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<LoginResponse>(
                        success = false,
                        error = "Invalid request: ${e.message}"
                    )
                )
            }
        }
        
        // Validate session endpoint
        post("/validate") {
            try {
                val request = call.receive<ValidateRequest>()
                
                when (val result = accountService.validateSession(request.sessionToken)) {
                    is net.kibotu.ro.account.service.ValidateResult.Valid -> {
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(
                                success = true,
                                data = ValidateResponse(
                                    valid = true,
                                    userId = result.userId,
                                    expiresAt = result.expiresAt.toString()
                                )
                            )
                        )
                    }
                    is net.kibotu.ro.account.service.ValidateResult.Invalid -> {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiResponse(
                                success = true,
                                data = ValidateResponse(
                                    valid = false
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<ValidateResponse>(
                        success = false,
                        error = "Invalid request: ${e.message}"
                    )
                )
            }
        }
    }
}

