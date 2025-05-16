package com.example.todak.data.network

import com.example.todak.util.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 토큰이 없으면 원래 요청 그대로 진행
        val token = SessionManager.getAuthToken() ?: return chain.proceed(originalRequest)

        // 토큰이 있으면 헤더에 추가
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(newRequest)
    }
}