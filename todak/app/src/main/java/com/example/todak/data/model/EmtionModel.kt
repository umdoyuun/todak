package com.example.todak.data.model

data class EmotionRequest(
    val emotion: String,
    val context: String? = null,
    val note: String? = null
    // audio_file은 MultipartBody.Part로 별도 처리
)

data class EmotionResponse(
    val message: String,
    val id: String,
    val note: String? = null
)