package net.kibotu.ro.character.service

import net.kibotu.ro.character.database.Character
import net.kibotu.ro.character.database.Characters
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class CharacterService(
    val sessionValidationService: SessionValidationService
) {
    /**
     * Validate character name
     */
    fun validateCharacterName(name: String): NameValidationResult {
        val trimmedName = name.trim()
        
        if (trimmedName.length < 3) {
            return NameValidationResult(false, "Character name must be at least 3 characters long")
        }
        
        if (trimmedName.length > 20) {
            return NameValidationResult(false, "Character name must be at most 20 characters long")
        }
        
        // Alphanumeric + spaces only
        val nameRegex = "^[a-zA-Z0-9 ]+$".toRegex()
        if (!trimmedName.matches(nameRegex)) {
            return NameValidationResult(false, "Character name can only contain alphanumeric characters and spaces")
        }
        
        // Cannot be all spaces
        if (trimmedName.all { it == ' ' }) {
            return NameValidationResult(false, "Character name cannot be all spaces")
        }
        
        return NameValidationResult(true, null)
    }
    
    /**
     * Create a new character for an account
     */
    suspend fun createCharacter(accountId: String, name: String): CreateCharacterResult {
        // Validate name
        val nameValidation = validateCharacterName(name)
        if (!nameValidation.isValid) {
            return CreateCharacterResult.Error(nameValidation.errorMessage ?: "Invalid character name")
        }
        
        val trimmedName = name.trim()
        
        return transaction {
            // Check if name already exists (case-insensitive)
            // Query all characters and compare lowercase names
            val existingCharacter = Character.all().find { 
                it.name.lowercase() == trimmedName.lowercase() 
            }
            if (existingCharacter != null) {
                return@transaction CreateCharacterResult.Error("Character name already taken")
            }
            
            // Check if account already has a character (one character per account initially)
            val accountCharacters = Character.find { Characters.accountId eq UUID.fromString(accountId) }.toList()
            if (accountCharacters.isNotEmpty()) {
                return@transaction CreateCharacterResult.Error("Account already has a character")
            }
            
            // Create new character
            val character = Character.new {
                this.accountId = UUID.fromString(accountId)
                this.name = trimmedName
                this.createdAt = Instant.now()
            }
            
            CreateCharacterResult.Success(character)
        }
    }
    
    /**
     * Get all characters for an account
     */
    fun getCharactersByAccountId(accountId: String): List<Character> {
        return transaction {
            try {
                Character.find { Characters.accountId eq UUID.fromString(accountId) }.toList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Get character by ID
     */
    fun getCharacterById(characterId: String): Character? {
        return transaction {
            try {
                Character.findById(UUID.fromString(characterId))
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Get character by ID and verify it belongs to the account
     */
    fun getCharacterByIdAndAccountId(characterId: String, accountId: String): Character? {
        return transaction {
            try {
                Character.find {
                    Characters.id eq UUID.fromString(characterId) and (Characters.accountId eq UUID.fromString(accountId))
                }.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class NameValidationResult(
    val isValid: Boolean,
    val errorMessage: String?
)

sealed class CreateCharacterResult {
    data class Success(val character: Character) : CreateCharacterResult()
    data class Error(val message: String) : CreateCharacterResult()
}

