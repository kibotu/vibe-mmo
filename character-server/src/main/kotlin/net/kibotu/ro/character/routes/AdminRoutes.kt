package net.kibotu.ro.character.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kibotu.ro.character.model.ApiResponse
import net.kibotu.ro.character.service.AdminService

fun Route.adminRoutes(adminService: AdminService) {
    route("/api/admin") {
        // Get all characters
        get("/characters") {
            try {
                val characters = adminService.getAllCharacters()
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = characters
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<net.kibotu.ro.character.service.CharacterInfo>>(
                        success = false,
                        error = "Failed to fetch characters: ${e.message}"
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
                    ApiResponse<net.kibotu.ro.character.service.CharacterServerStatistics>(
                        success = false,
                        error = "Failed to fetch statistics: ${e.message}"
                    )
                )
            }
        }
        
        // Delete a character
        delete("/characters/{characterId}") {
            try {
                val characterId = call.parameters["characterId"]
                if (characterId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Character ID is required"
                        )
                    )
                    return@delete
                }
                
                val deleted = adminService.deleteCharacter(characterId)
                if (deleted) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse<Unit>(
                            success = true,
                            message = "Character deleted successfully"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Character not found"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(
                        success = false,
                        error = "Failed to delete character: ${e.message}"
                    )
                )
            }
        }
        
        // Get characters by account ID
        get("/characters/account/{accountId}") {
            try {
                val accountId = call.parameters["accountId"]
                if (accountId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<List<net.kibotu.ro.character.service.CharacterInfo>>(
                            success = false,
                            error = "Account ID is required"
                        )
                    )
                    return@get
                }
                
                val characters = adminService.getCharactersByAccountId(accountId)
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = characters
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<net.kibotu.ro.character.service.CharacterInfo>>(
                        success = false,
                        error = "Failed to fetch characters: ${e.message}"
                    )
                )
            }
        }
    }
}

