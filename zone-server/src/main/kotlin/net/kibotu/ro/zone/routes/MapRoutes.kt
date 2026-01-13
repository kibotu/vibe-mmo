package net.kibotu.ro.zone.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.kibotu.ro.zone.model.ApiResponse
import net.kibotu.ro.zone.model.MapData
import net.kibotu.ro.zone.service.MapStorageService
import net.kibotu.ro.zone.service.MapValidator
import net.kibotu.ro.zone.service.MapValidationResult

fun Route.mapRoutes(
    mapStorageService: MapStorageService,
    mapValidator: MapValidator
) {
    route("/api/maps") {
        // List all maps
        get {
            try {
                val maps = mapStorageService.listMaps()
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = maps
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<String>>(
                        success = false,
                        error = "Failed to list maps: ${e.message}"
                    )
                )
            }
        }
        
        // Get map by name
        get("{mapName}") {
            try {
                val mapName = call.parameters["mapName"]
                if (mapName == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<MapData>(
                            success = false,
                            error = "Map name is required"
                        )
                    )
                    return@get
                }
                
                val mapData = mapStorageService.loadMap(mapName)
                if (mapData == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<MapData>(
                            success = false,
                            error = "Map not found: $mapName"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            data = mapData
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<MapData>(
                        success = false,
                        error = "Failed to load map: ${e.message}"
                    )
                )
            }
        }
        
        // Create new map
        post {
            try {
                val mapData = call.receive<MapData>()
                
                // Check if map already exists
                if (mapStorageService.mapExists(mapData.name)) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Map already exists: ${mapData.name}"
                        )
                    )
                    return@post
                }
                
                // Validate map
                val validationResult = mapValidator.validate(mapData)
                if (!validationResult.isValid) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                    ApiResponse<MapValidationResult>(
                        success = false,
                        error = "Map validation failed",
                        data = validationResult
                    )
                    )
                    return@post
                }
                
                // Add timestamps
                val now = Clock.System.now().toString()
                val newMapData = mapData.copy(
                    createdAt = now,
                    updatedAt = now
                )
                
                // Save map
                val saved = mapStorageService.saveMap(newMapData)
                if (saved) {
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse<Unit>(
                            success = true,
                            message = "Map created successfully"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Failed to save map"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Invalid map data: ${e.message}"
                    )
                )
            }
        }
        
        // Update existing map
        put("{mapName}") {
            try {
                val mapName = call.parameters["mapName"]
                if (mapName == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Map name is required"
                        )
                    )
                    return@put
                }
                
                val mapData = call.receive<MapData>()
                
                // Ensure map name matches URL parameter
                if (mapData.name != mapName) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Map name in body must match URL parameter"
                        )
                    )
                    return@put
                }
                
                // Check if map exists
                if (!mapStorageService.mapExists(mapName)) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Map not found: $mapName"
                        )
                    )
                    return@put
                }
                
                // Validate map
                val validationResult = mapValidator.validate(mapData)
                if (!validationResult.isValid) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                    ApiResponse<MapValidationResult>(
                        success = false,
                        error = "Map validation failed",
                        data = validationResult
                    )
                    )
                    return@put
                }
                
                // Load existing map to preserve createdAt
                val existingMap = mapStorageService.loadMap(mapName)
                val now = Clock.System.now().toString()
                val updatedMapData = mapData.copy(
                    createdAt = existingMap?.createdAt ?: now,
                    updatedAt = now
                )
                
                // Save map
                val saved = mapStorageService.saveMap(updatedMapData)
                if (saved) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse<Unit>(
                            success = true,
                            message = "Map updated successfully"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Failed to save map"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Invalid map data: ${e.message}"
                    )
                )
            }
        }
        
        // Delete map
        delete("{mapName}") {
            try {
                val mapName = call.parameters["mapName"]
                if (mapName == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Map name is required"
                        )
                    )
                    return@delete
                }
                
                val deleted = mapStorageService.deleteMap(mapName)
                if (deleted) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse<Unit>(
                            success = true,
                            message = "Map deleted successfully"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Map not found: $mapName"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Failed to delete map: ${e.message}"
                    )
                )
            }
        }
        
        // Validate map without saving
        post("{mapName}/validate") {
            try {
                val mapName = call.parameters["mapName"]
                val mapData = call.receive<MapData>()
                
                // Ensure map name matches URL parameter if provided
                if (mapName != null && mapData.name != mapName) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<MapValidationResult>(
                            success = false,
                            error = "Map name in body must match URL parameter"
                        )
                    )
                    return@post
                }
                
                val validationResult = mapValidator.validate(mapData)
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = validationResult
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<MapValidationResult>(
                        success = false,
                        error = "Invalid map data: ${e.message}"
                    )
                )
            }
        }
    }
}

