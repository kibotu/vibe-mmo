package net.kibotu.ro.client.model

import kotlinx.serialization.Serializable

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

