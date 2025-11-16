package net.kibotu.ro.account.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kibotu.ro.account.model.ApiResponse
import net.kibotu.ro.account.service.AdminService

fun Route.adminRoutes(adminService: AdminService) {
    route("/api/admin") {
        // Get all accounts
        get("/accounts") {
            try {
                val accounts = adminService.getAllAccounts()
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = accounts
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<net.kibotu.ro.account.model.AccountDTO>>(
                        success = false,
                        error = "Failed to fetch accounts: ${e.message}"
                    )
                )
            }
        }
        
        // Get all sessions
        get("/sessions") {
            try {
                val sessions = adminService.getAllSessions()
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = sessions
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<net.kibotu.ro.account.service.SessionInfo>>(
                        success = false,
                        error = "Failed to fetch sessions: ${e.message}"
                    )
                )
            }
        }
        
        // Get server statistics
        get("/statistics") {
            try {
                val stats = adminService.getStatistics()
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = stats
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<net.kibotu.ro.account.service.ServerStatistics>(
                        success = false,
                        error = "Failed to fetch statistics: ${e.message}"
                    )
                )
            }
        }
        
        // Delete an account
        delete("/accounts/{accountId}") {
            try {
                val accountId = call.parameters["accountId"]
                if (accountId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Account ID is required"
                        )
                    )
                    return@delete
                }
                
                val deleted = adminService.deleteAccount(accountId)
                if (deleted) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse<Unit>(
                            success = true,
                            message = "Account deleted successfully"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Account not found"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Failed to delete account: ${e.message}"
                    )
                )
            }
        }
        
        // Invalidate a session
        delete("/sessions/{sessionId}") {
            try {
                val sessionId = call.parameters["sessionId"]
                if (sessionId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Session ID is required"
                        )
                    )
                    return@delete
                }
                
                val invalidated = adminService.invalidateSession(sessionId)
                if (invalidated) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse<Unit>(
                            success = true,
                            message = "Session invalidated successfully"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Session not found"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Failed to invalidate session: ${e.message}"
                    )
                )
            }
        }
        
        // Invalidate all sessions for an account
        delete("/accounts/{accountId}/sessions") {
            try {
                val accountId = call.parameters["accountId"]
                if (accountId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Account ID is required"
                        )
                    )
                    return@delete
                }
                
                val count = adminService.invalidateAllSessionsForAccount(accountId)
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Unit>(
                        success = true,
                        message = "Invalidated $count session(s)"
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Failed to invalidate sessions: ${e.message}"
                    )
                )
            }
        }
    }
}

