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
        
        // Get minimap data for a zone instance
        get("/zones/{instanceId}/minimap") {
            try {
                val instanceId = call.parameters["instanceId"]
                
                if (instanceId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Instance ID is required"
                        )
                    )
                    return@get
                }
                
                val minimapData = adminService.getMinimapData(instanceId)
                if (minimapData != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            data = minimapData
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Zone instance not found"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Failed to fetch minimap data: ${e.message}"
                    )
                )
            }
        }
        
        // Load a map into a zone instance
        post("/zones/load-map") {
            try {
                val zoneName = call.request.queryParameters["zoneName"]
                
                if (zoneName == null || zoneName.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Zone name is required"
                        )
                    )
                    return@post
                }
                
                val result = adminService.loadMapIntoZone(zoneName)
                when (result) {
                    is net.kibotu.ro.zone.service.LoadMapResult.Success -> {
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(
                                success = true,
                                data = result,
                                message = "Map loaded successfully"
                            )
                        )
                    }
                    is net.kibotu.ro.zone.service.LoadMapResult.Error -> {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse<Unit>(
                                success = false,
                                error = result.message
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Failed to load map: ${e.message}"
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

