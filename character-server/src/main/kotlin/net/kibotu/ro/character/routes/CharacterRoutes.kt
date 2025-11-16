package net.kibotu.ro.character.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.kibotu.ro.character.model.*
import net.kibotu.ro.character.service.CharacterService
import net.kibotu.ro.character.service.ValidationResult as SessionValidationResult

fun Route.characterRoutes(characterService: CharacterService) {
    route("/api/character") {
        // Helper function to validate session and get userId
        suspend fun ApplicationCall.validateSessionAndGetUserId(): String? {
            val authHeader = request.headers["Authorization"]
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return null
            }
            
            val token = authHeader.removePrefix("Bearer ").trim()
            val validationResult = characterService.sessionValidationService.validateSession(token)
            
            return when (validationResult) {
                is SessionValidationResult.Valid -> validationResult.userId
                is SessionValidationResult.Invalid -> null
            }
        }
        
        // List characters endpoint
        get("/list") {
            val userId = call.validateSessionAndGetUserId()
            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<CharacterListResponse>(
                        success = false,
                        error = "Missing or invalid Authorization header"
                    )
                )
                return@get
            }
            
            val characters = characterService.getCharactersByAccountId(userId)
            val characterDTOs = characters.map { it.toDTO() }
            
            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = CharacterListResponse(characterDTOs)
                )
            )
        }
        
        // Create character endpoint
        post("/create") {
            val userId = call.validateSessionAndGetUserId()
            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<CharacterResponse>(
                        success = false,
                        error = "Missing or invalid Authorization header"
                    )
                )
                return@post
            }
            
            try {
                val request = call.receive<CreateCharacterRequest>()
                
                when (val result = characterService.createCharacter(userId, request.name)) {
                    is net.kibotu.ro.character.service.CreateCharacterResult.Success -> {
                        call.respond(
                            HttpStatusCode.Created,
                            ApiResponse(
                                success = true,
                                data = CharacterResponse(character = result.character.toDTO())
                            )
                        )
                    }
                    is net.kibotu.ro.character.service.CreateCharacterResult.Error -> {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiResponse<CharacterResponse>(
                                success = false,
                                error = result.message
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<CharacterResponse>(
                        success = false,
                        error = "Invalid request: ${e.message}"
                    )
                )
            }
        }
        
        // Get character by ID endpoint
        get("/{characterId}") {
            val userId = call.validateSessionAndGetUserId()
            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<CharacterResponse>(
                        success = false,
                        error = "Missing or invalid Authorization header"
                    )
                )
                return@get
            }
            
            val characterId = call.parameters["characterId"] ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<CharacterResponse>(
                        success = false,
                        error = "Missing character ID"
                    )
                )
                return@get
            }
            
            val character = characterService.getCharacterByIdAndAccountId(characterId, userId)
            
            if (character == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<CharacterResponse>(
                        success = false,
                        error = "Character not found"
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = CharacterResponse(character = character.toDTO())
                    )
                )
            }
        }
    }
}

