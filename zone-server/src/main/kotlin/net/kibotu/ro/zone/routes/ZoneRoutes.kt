package net.kibotu.ro.zone.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kibotu.ro.zone.model.*
import net.kibotu.ro.zone.service.ZoneService
import net.kibotu.ro.zone.service.ValidationResult as SessionValidationResult

fun Route.zoneRoutes(zoneService: ZoneService) {
    route("/api/zone") {
        // Helper function to validate session and get userId
        suspend fun ApplicationCall.validateSessionAndGetUserId(): String? {
            val authHeader = request.headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return null
            }
            
            val token = authHeader.removePrefix("Bearer ").trim()
            val validationResult = zoneService.sessionValidationService.validateSession(token)
            
            return when (validationResult) {
                is SessionValidationResult.Valid -> validationResult.userId
                is SessionValidationResult.Invalid -> null
            }
        }
        
        // Join zone endpoint
        post("/join") {
            val userId = call.validateSessionAndGetUserId()
            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<JoinZoneResponse>(
                        success = false,
                        error = "Missing or invalid Authorization header"
                    )
                )
                return@post
            }
            
            try {
                val request = call.receive<JoinZoneRequest>()
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim() ?: ""
                
                when (val result = zoneService.joinZone(request.characterId, request.zoneName, token)) {
                    is net.kibotu.ro.zone.service.JoinZoneResult.Success -> {
                        // Build full WebSocket URL
                        val host = call.request.host()
                        val port = call.request.port()
                        // Determine protocol based on port (443 = HTTPS/WSS, others = HTTP/WS)
                        // In production, you might want to check X-Forwarded-Proto header
                        val protocol = if (port == 443) "wss" else "ws"
                        val websocketUrl = "$protocol://$host:$port${result.websocketUrl}"
                        
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse(
                                success = true,
                                data = JoinZoneResponse(
                                    zoneInstanceId = result.zoneInstanceId,
                                    websocketUrl = websocketUrl,
                                    spawnPosition = result.spawnPosition
                                )
                            )
                        )
                    }
                    is net.kibotu.ro.zone.service.JoinZoneResult.Error -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<JoinZoneResponse>(
                                success = false,
                                error = result.message
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<JoinZoneResponse>(
                        success = false,
                        error = "Invalid request: ${e.message}"
                    )
                )
            }
        }
    }
}

