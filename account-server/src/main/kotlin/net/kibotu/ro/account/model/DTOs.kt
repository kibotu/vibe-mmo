package net.kibotu.ro.account.model

import kotlinx.serialization.Serializable

// Request DTOs
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class ValidateRequest(
    val sessionToken: String
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
data class RegisterResponse(
    val userId: String,
    val message: String = "Account created successfully"
)

@Serializable
data class LoginResponse(
    val sessionToken: String,
    val userId: String,
    val expiresAt: String
)

@Serializable
data class ValidateResponse(
    val valid: Boolean,
    val userId: String? = null,
    val expiresAt: String? = null
)

@Serializable
data class AccountDTO(
    val id: String,
    val email: String,
    val createdAt: String,
    val lastLogin: String? = null
)

