package net.kibotu.ro.zone.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kibotu.ro.zone.model.ApiResponse
import net.kibotu.ro.zone.service.AdminService

fun Route.adminRoutes(adminService: AdminService) {
    route("/api/admin") {
        // Get all zone instances
        get("/zones") {
            try {
                val zones = adminService.getAllZoneInstances()
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = zones
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<net.kibotu.ro.zone.service.ZoneInstanceInfo>>(
                        success = false,
                        error = "Failed to fetch zones: ${e.message}"
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
                    ApiResponse<net.kibotu.ro.zone.service.ZoneServerStatistics>(
                        success = false,
                        error = "Failed to fetch statistics: ${e.message}"
                    )
                )
            }
        }
        
        // Kick a player from a zone
        delete("/zones/{instanceId}/players/{characterId}") {
            try {
                val instanceId = call.parameters["instanceId"]
                val characterId = call.parameters["characterId"]
                
                if (instanceId == null || characterId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Instance ID and Character ID are required"
                        )
                    )
                    return@delete
                }
                
                val kicked = adminService.kickPlayer(instanceId, characterId)
                if (kicked) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse<Unit>(
                            success = true,
                            message = "Player kicked successfully"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Player or zone instance not found"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Failed to kick player: ${e.message}"
                    )
                )
            }
        }
    }
}

