package com.example.todak.data.model

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val status: String,
    val message: String,
    val id: String,
    val name: String,
    val type: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)