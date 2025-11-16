package net.kibotu.ro.character.model

import kotlinx.serialization.Serializable

// Request DTOs
@Serializable
data class CreateCharacterRequest(
    val name: String
)

// Response DTOs
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CharacterDTO(
    val id: String,
    val accountId: String,
    val name: String,
    val createdAt: String
)

@Serializable
data class CharacterListResponse(
    val characters: List<CharacterDTO>
)

@Serializable
data class CharacterResponse(
    val character: CharacterDTO
)

