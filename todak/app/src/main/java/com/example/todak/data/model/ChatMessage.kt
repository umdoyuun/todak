package com.example.todak.data.model

data class ChatMessage(
    val id: Long,
    val message: String,
    val sender: Sender,
    val timestamp: Long
) {
    enum class Sender {
        USER, TODAK
    }
}

data class ChatRequest(
    val message: String,
    val session_id: String? = null
)

data class ChatResponse (
    val session_id: String,
    val bot_message: String
)