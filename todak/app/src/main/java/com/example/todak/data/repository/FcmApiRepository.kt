package com.example.todak.data.repository

import android.util.Log
import com.example.todak.data.model.FcmTokenResponse
import com.example.todak.data.model.FcmTokenRequest
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.FcmScheduleResponse
import com.example.todak.data.model.FcmScheduleActionResponse
import com.example.todak.data.model.FcmScheduleItem
import com.example.todak.data.network.RetrofitClient
import com.example.todak.util.SessionManager

// FCM 관련 API 레포지토리
class FcmApiRepository {
    private val apiService = RetrofitClient.apiService
    private val TAG = "FCMRepository"

    suspend fun registerDeviceToken(token: String): NetworkResult<FcmTokenResponse> {
        return try {
            Log.d(TAG, "FCM 토큰 등록 시작: $token")

            val userId = SessionManager.getUserId()
            val accessToken = SessionManager.getAuthToken()

            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "토큰이 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("토큰이 없습니다. 로그인이 필요합니다.")
            }

            val requestBody = FcmTokenRequest(token = token)

            val response = apiService.registerDeviceToken(
                userId = userId,
                token = "Bearer $accessToken",
                request = requestBody
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "FCM 토큰 등록 성공: $it")
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
        } catch (e: Exception) {
            Log.e(TAG, "FCM 토큰 등록 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun unregisterDeviceToken(token: String): NetworkResult<FcmTokenResponse> {
        return try {
            Log.d(TAG, "FCM 토큰 등록 해제 시작: $token")

            val userId = SessionManager.getUserId()
            val accessToken = SessionManager.getAuthToken()

            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "토큰이 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("토큰이 없습니다. 로그인이 필요합니다.")
            }

            val requestBody = FcmTokenRequest(token = token)

            val response = apiService.unregisterDeviceToken(
                userId = userId,
                token = "Bearer $accessToken",
                request = requestBody
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "FCM 토큰 등록 해제 성공: $it")
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
        } catch (e: Exception) {
            Log.e(TAG, "FCM 토큰 등록 해제 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun getTodaySchedules(targetDate: String? = null): NetworkResult<List<FcmScheduleItem>> {
        return try {
            Log.d(TAG, "오늘 일정 조회 시작")

            val userId = SessionManager.getUserId()
            val accessToken = SessionManager.getAuthToken()

            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "토큰이 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("토큰이 없습니다. 로그인이 필요합니다.")
            }

            val response = apiService.getTodaySchedules(
                userId = userId,
                token = "Bearer $accessToken",
                targetDate = targetDate
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "오늘 일정 조회 성공: ${it.schedules.size}개 일정")
                    NetworkResult.Success(it.schedules)
                } ?: run {
                    Log.e(TAG, "응답 본문이 비어 있습니다")
                    NetworkResult.Error("응답 본문이 비어 있습니다")
                }
            } else {
                val errorMsg = "API 호출 실패: ${response.code()} ${response.message()}"
                Log.e(TAG, errorMsg)
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "오늘 일정 조회 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun performScheduleAction(
        scheduleId: String,
        action: String,
        notes: String? = null
    ): NetworkResult<FcmScheduleActionResponse> {
        return try {
            Log.d(TAG, "일정 액션 수행 시작: $action, 일정 ID: $scheduleId")

            val userId = SessionManager.getUserId()
            val accessToken = SessionManager.getAuthToken()

            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "토큰이 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("토큰이 없습니다. 로그인이 필요합니다.")
            }

            val response = when (action) {
                "start" -> apiService.startSchedule(
                    userId = userId,
                    token = "Bearer $accessToken",
                    scheduleId = scheduleId,
                    notes = notes
                )
                "complete" -> apiService.completeSchedule(
                    userId = userId,
                    token = "Bearer $accessToken",
                    scheduleId = scheduleId,
                    notes = notes
                )
                "postpone" -> apiService.postponeSchedule(
                    userId = userId,
                    token = "Bearer $accessToken",
                    scheduleId = scheduleId,
                    notes = notes
                )
                else -> {
                    Log.e(TAG, "지원하지 않는 액션: $action")
                    return NetworkResult.Error("지원하지 않는 액션: $action")
                }
            }

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "일정 액션 수행 성공: $it")
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
        } catch (e: Exception) {
            Log.e(TAG, "일정 액션 수행 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }
}