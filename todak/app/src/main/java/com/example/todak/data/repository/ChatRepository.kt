package com.example.todak.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.todak.data.model.ChatMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ChatRepository {
    private val PREFS_NAME = "chat_prefs"
    private val KEY_MESSAGES = "chat_messages"
    private val gson = Gson()

    fun saveMessage(context: Context, message: ChatMessage) {
        val messages = getSavedMessages(context).toMutableList()
        messages.add(message)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val messagesJson = gson.toJson(messages)

        prefs.edit().putString(KEY_MESSAGES, messagesJson).apply()
    }

    fun getSavedMessages(context: Context): List<ChatMessage> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val messagesJson = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()

        val type = object : TypeToken<List<ChatMessage>>() {}.type
        return gson.fromJson(messagesJson, type)
    }

    fun clearMessages(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_MESSAGES).apply()
    }
}