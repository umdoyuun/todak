package com.example.todak.data.model

data class AudioResponse(
    val session_id: String,
    val bot_message: String,
    val user_message: String,
    val s3_key: String
)