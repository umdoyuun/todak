// BudgetRepository.kt
package com.example.todak.data.repository

import android.util.Log
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.SetWeeklyBudgetRequest
import com.example.todak.data.model.SetWeeklyBudgetResponse
import com.example.todak.data.model.WeeklyBudgetResponse
import com.example.todak.data.network.RetrofitClient
import com.example.todak.util.SessionManager
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.IOException

class BudgetRepository {
    private val apiService = RetrofitClient.apiService
    private val TAG = "BudgetRepository"

    suspend fun getWeeklyBudget(): NetworkResult<WeeklyBudgetResponse> {
        return try {
            // 인증 토큰 확인
            val userId = SessionManager.getUserId() ?: return NetworkResult.Error("사용자 ID가 없습니다")
            val token = SessionManager.getAuthToken() ?: return NetworkResult.Error("인증 토큰이 없습니다")

            // API 호출
            val response = executeWithRetry { apiService.getWeeklyBudget(userId, token) }

            if (response.isSuccessful) {
                response.body()?.let {
                    NetworkResult.Success(it)
                } ?: NetworkResult.Error("데이터가 없습니다")
            } else {
                NetworkResult.Error("API 오류: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "주간 예산 정보 조회 실패", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun setWeeklyBudget(amount: Int): NetworkResult<SetWeeklyBudgetResponse> {
        return try {
            // 인증 토큰 확인
            val userId = SessionManager.getUserId() ?: return NetworkResult.Error("사용자 ID가 없습니다")
            val token = SessionManager.getAuthToken() ?: return NetworkResult.Error("인증 토큰이 없습니다")

            // API 요청 생성
            val request = SetWeeklyBudgetRequest(amount)

            // API 호출
            val response = executeWithRetry { apiService.setWeeklyBudget(userId, token, request) }

            if (response.isSuccessful) {
                response.body()?.let {
                    NetworkResult.Success(it)
                } ?: NetworkResult.Error("응답 데이터가 없습니다")
            } else {
                NetworkResult.Error("API 오류: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "주간 예산 설정 실패", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    // 재시도 로직이 포함된 API 호출 래퍼
    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        initialDelayMillis: Long = 1000,
        maxDelayMillis: Long = 5000,
        factor: Double = 2.0,
        block: suspend () -> Response<T>
    ): Response<T> {
        var currentDelay = initialDelayMillis
        repeat(maxRetries) { attempt ->
            try {
                // API 호출 시도
                val response = block()

                // 성공 또는 클라이언트 오류면 바로 반환
                if (response.isSuccessful || response.code() in 400..499) {
                    return response
                }

                Log.w(TAG, "API 호출 실패 (${response.code()}), ${attempt + 1}/$maxRetries 재시도 중...")
            } catch (e: IOException) {
                // 네트워크 오류면 재시도
                Log.w(TAG, "네트워크 오류 발생, ${attempt + 1}/$maxRetries 재시도 중...", e)
            }

            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
        }

        // 모든 재시도 실패 시 마지막 시도
        return block()
    }
}