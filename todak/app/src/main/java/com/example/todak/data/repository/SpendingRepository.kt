package com.example.todak.data.repository

import android.util.Log
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.SpendingBatchRequest
import com.example.todak.data.model.SpendingItem
import com.example.todak.data.model.SpendingResponse
import com.example.todak.data.network.RetrofitClient
import com.example.todak.util.SessionManager
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.IOException

class SpendingRepository {
    private val apiService = RetrofitClient.apiService
    private val TAG = "SpendingRepository"

    // 단일 소비 데이터 전송
    suspend fun submitSpending(item: SpendingItem): NetworkResult<SpendingResponse> {
        return try {
            // 인증 토큰 확인
            val userId = SessionManager.getUserId()
            val accessToken = SessionManager.getAuthToken()

            if (userId.isNullOrEmpty() || accessToken.isNullOrEmpty()) {
                Log.e(TAG, "사용자 인증 정보가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 인증 정보가 없습니다. 로그인이 필요합니다.")
            }

            // 재시도 메커니즘
            val response = executeWithRetry { apiService.submitSpending(item) }

            processResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "소비 데이터 전송 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    // 소비 데이터 배치 전송
    suspend fun submitBatch(request: SpendingBatchRequest): NetworkResult<SpendingResponse> {
        return try {
            // 인증 토큰 확인
            val userId = SessionManager.getUserId()
            val accessToken = SessionManager.getAuthToken()

            if (userId.isNullOrEmpty() || accessToken.isNullOrEmpty()) {
                Log.e(TAG, "사용자 인증 정보가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 인증 정보가 없습니다. 로그인이 필요합니다.")
            }

            // 재시도 메커니즘
            val response = executeWithRetry { apiService.submitSpendingBatch(request.items) }

            processResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "소비 데이터 배치 전송 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    // 응답 처리
    private fun processResponse(response: Response<SpendingResponse>): NetworkResult<SpendingResponse> {
        return if (response.isSuccessful) {
            response.body()?.let {
                Log.d(TAG, "API 호출 성공: $it")
                NetworkResult.Success(it)
            } ?: run {
                Log.e(TAG, "응답 본문이 비어 있습니다")
                NetworkResult.Error("응답 본문이 비어 있습니다")
            }
        } else {
            val errorMsg = "API 호출 실패: ${response.code()} ${response.message()}"
            Log.e(TAG, errorMsg)
            NetworkResult.Error(errorMsg)
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

                // 서버 오류(5xx)면 재시도
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