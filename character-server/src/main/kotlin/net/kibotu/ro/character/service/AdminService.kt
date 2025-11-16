package net.kibotu.ro.character.service

import kotlinx.serialization.Serializable
import net.kibotu.ro.character.database.Character
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class AdminService {
    /**
     * Get all characters
     */
    fun getAllCharacters(): List<CharacterInfo> {
        return transaction {
            Character.all().map { character ->
                CharacterInfo(
                    id = character.id.value.toString(),
                    accountId = character.accountId.toString(),
                    name = character.name,
                    createdAt = character.createdAt.toString()
                )
            }
        }
    }
    
    /**
     * Get server statistics
     */
    fun getStatistics(): CharacterServerStatistics {
        return transaction {
            val totalCharacters = Character.count().toInt()
            val charactersByAccount = Character.all()
                .groupBy { it.accountId }
                .mapValues { it.value.size }
            
            val accountsWithCharacters = charactersByAccount.size
            val averageCharactersPerAccount = if (accountsWithCharacters > 0) {
                totalCharacters.toDouble() / accountsWithCharacters
            } else {
                0.0
            }
            
            val recentCharacters = Character.all()
                .sortedByDescending { it.createdAt }
                .take(10)
                .map { it.toDTO() }
            
            CharacterServerStatistics(
                totalCharacters = totalCharacters,
                accountsWithCharacters = accountsWithCharacters,
                averageCharactersPerAccount = averageCharactersPerAccount,
                recentCharacters = recentCharacters
            )
        }
    }
    
    /**
     * Delete a character by ID
     */
    fun deleteCharacter(characterId: String): Boolean {
        return transaction {
            try {
                val character = Character.findById(UUID.fromString(characterId))
                if (character != null) {
                    character.delete()
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Get characters by account ID
     */
    fun getCharactersByAccountId(accountId: String): List<CharacterInfo> {
        return transaction {
            try {
                Character.find { net.kibotu.ro.character.database.Characters.accountId eq UUID.fromString(accountId) }
                    .map { character ->
                        CharacterInfo(
                            id = character.id.value.toString(),
                            accountId = character.accountId.toString(),
                            name = character.name,
                            createdAt = character.createdAt.toString()
                        )
                    }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

@Serializable
data class CharacterInfo(
    val id: String,
    val accountId: String,
    val name: String,
    val createdAt: String
)

@Serializable
data class CharacterServerStatistics(
    val totalCharacters: Int,
    val accountsWithCharacters: Int,
    val averageCharactersPerAccount: Double,
    val recentCharacters: List<net.kibotu.ro.character.model.CharacterDTO>
)

