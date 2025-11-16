package net.kibotu.ro.client.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateCharacterRequest(
    val name: String
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

