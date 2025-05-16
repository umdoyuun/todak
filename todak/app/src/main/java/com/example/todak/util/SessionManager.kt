package com.example.todak.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 사용자 세션 관리를 위한 유틸리티 클래스
 */
object SessionManager {
    private const val PREF_NAME = "TodakAppSession"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_CHAT_SESSION_ID = "chat_session_id"

    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun validateInitialization() {
        if (!::sharedPreferences.isInitialized) {
            throw IllegalStateException("SessionManager가 초기화되지 않았습니다. init(context)를 먼저 호출하세요.")
        }
    }

    fun saveAuthToken(accessToken: String, refreshToken: String? = null, userId: String? = null, userName: String? = null) {
        validateInitialization()
        sharedPreferences.edit().apply {
            putString(KEY_AUTH_TOKEN, accessToken)
            refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
            userId?.let { putString(KEY_USER_ID, it) }
            userName?.let { putString(KEY_USER_NAME, it) }
            putBoolean(KEY_IS_LOGGED_IN, true)
        }.apply()
    }

    fun getAuthToken(): String? {
        validateInitialization()
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        validateInitialization()
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getUserId(): String? {
        validateInitialization()
        return sharedPreferences.getString(KEY_USER_ID, null)
    }

    fun getUserName(): String? {
        validateInitialization()
        return sharedPreferences.getString(KEY_USER_NAME, null)
    }

    fun isLoggedIn(): Boolean {
        validateInitialization()
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    // 채팅 세션 ID 저장
    fun saveChatSessionId(sessionId: String) {
        validateInitialization()
        sharedPreferences.edit().putString(KEY_CHAT_SESSION_ID, sessionId).apply()
    }

    // 채팅 세션 ID 조회
    fun getChatSessionId(): String? {
        validateInitialization()
        return sharedPreferences.getString(KEY_CHAT_SESSION_ID, null)
    }

    // 채팅 세션 ID 삭제
    fun clearChatSessionId() {
        validateInitialization()
        sharedPreferences.edit().remove(KEY_CHAT_SESSION_ID).apply()
    }

    fun clearSession() {
        validateInitialization()
        sharedPreferences.edit().clear().apply()
    }
}