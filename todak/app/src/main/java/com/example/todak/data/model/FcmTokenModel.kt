package com.example.todak.data.model

data class FcmTokenRequest (
    val token: String,
    val device_type: String = "android"
)

data class FcmTokenResponse(
    val message: String
)