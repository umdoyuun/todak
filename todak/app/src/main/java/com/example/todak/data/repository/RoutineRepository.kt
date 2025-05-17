package com.example.todak.data.repository

import android.util.Log
import com.example.todak.data.network.RetrofitClient
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.RoutineActionResponse
import com.example.todak.data.model.RoutineResponse
import com.example.todak.data.model.RoutineStartRequest
import com.example.todak.data.model.RoutineStepActionRequest
import com.example.todak.util.SessionManager

class RoutineRepository {
    private val apiService = RetrofitClient.apiService
    private val TAG = "RoutineRepository"

    suspend fun getRoutines(): NetworkResult<RoutineResponse> {
        return try {
            // 저장된 액세스 토큰과 사용자 ID 가져오기
            val accessToken = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "토큰이 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("토큰이 없습니다. 로그인이 필요합니다.")
            }

            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            // API 호출
            val response = apiService.getMyRoutines(
                userId = userId,
                token = "Bearer $accessToken"
            )

            when {
                response.isSuccessful -> {
                    response.body()?.let { routineResponse ->
                        Log.d(TAG, "루틴 목록 가져오기 성공: ${routineResponse.routines.size}개 항목")
                        NetworkResult.Success(routineResponse)
                    } ?: run {
                        Log.e(TAG, "응답 본문이 비어 있습니다")
                        NetworkResult.Error("응답 본문이 비어 있습니다")
                    }
                }
                response.code() == 401 -> {
                    // 토큰이 만료된 경우
                    Log.e(TAG, "토큰이 만료되었습니다. 재로그인이 필요합니다.")
                    SessionManager.clearSession()  // 세션 정보 삭제
                    NetworkResult.Error("토큰이 만료되었습니다. 다시 로그인해주세요.")
                }
                else -> {
                    val errorMsg = "API 호출 실패: ${response.code()} ${response.message()}"
                    Log.e(TAG, errorMsg)
                    NetworkResult.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "루틴 목록 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun completeRoutineStep(routineId: String, stepIndex: Int, notes: String): NetworkResult<RoutineActionResponse> {
        Log.d(TAG, "completeRoutineStep 호출됨 - 루틴 ID: $routineId, 단계 인덱스: $stepIndex")

        return try {
            // routineId가 비어 있는지 확인
            if (routineId.isEmpty()) {
                Log.e(TAG, "루틴 ID가 비어 있습니다.")
                return NetworkResult.Error("루틴 ID가 비어 있습니다.")
            }

            // 저장된 액세스 토큰과 사용자 ID 가져오기
            val accessToken = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "토큰이 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("토큰이 없습니다. 로그인이 필요합니다.")
            }

            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            // 요청 바디 생성
            val request = RoutineStepActionRequest(
                routine_id = routineId,
                step_index = stepIndex,
                notes = notes
            )

            Log.d(TAG, "API 요청 데이터: routine_id=$routineId, step_index=$stepIndex, notes=$notes")

            // API 호출
            val response = apiService.completeRoutineStep(
                userId = userId,
                token = "Bearer $accessToken",
                request = request
            )

            when {
                response.isSuccessful -> {
                    response.body()?.let { result ->
                        Log.d(TAG, "API 호출 성공: ${result.message}")
                        NetworkResult.Success(result)
                    } ?: run {
                        Log.e(TAG, "응답 본문이 비어 있습니다")
                        NetworkResult.Error("응답 본문이 비어 있습니다")
                    }
                }
                response.code() == 401 -> {
                    // 토큰이 만료된 경우
                    Log.e(TAG, "토큰이 만료되었습니다. 재로그인이 필요합니다.")
                    SessionManager.clearSession()  // 세션 정보 삭제
                    NetworkResult.Error("토큰이 만료되었습니다. 다시 로그인해주세요.")
                }
                else -> {
                    val errorMsg = "API 호출 실패: ${response.code()} ${response.message()}"
                    Log.e(TAG, errorMsg)
                    NetworkResult.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "루틴 단계 완료 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun startRoutine(routineId: String, notes: String? = null): NetworkResult<RoutineActionResponse> {
        Log.d(TAG, "startRoutine 호출됨 - 루틴 ID: $routineId")

        return try {
            // routineId가 비어 있는지 확인
            if (routineId.isEmpty()) {
                Log.e(TAG, "루틴 ID가 비어 있습니다.")
                return NetworkResult.Error("루틴 ID가 비어 있습니다.")
            }

            // 저장된 액세스 토큰과 사용자 ID 가져오기
            val accessToken = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "토큰이 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("토큰이 없습니다. 로그인이 필요합니다.")
            }

            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            // 요청 바디 생성
            val request = RoutineStartRequest(
                routine_id = routineId,
                notes = null  // 항상 null 설정
            )

            Log.d(TAG, "API 요청 데이터: routine_id=$routineId")

            // API 호출
            val response = apiService.startRoutine(
                userId = userId,
                authorization = "Bearer $accessToken",
                request = request
            )

            when {
                response.isSuccessful -> {
                    response.body()?.let { result ->
                        Log.d(TAG, "API 호출 성공: ${result.message}")
                        NetworkResult.Success(result)
                    } ?: run {
                        Log.e(TAG, "응답 본문이 비어 있습니다")
                        NetworkResult.Error("응답 본문이 비어 있습니다")
                    }
                }
                response.code() == 401 -> {
                    // 토큰이 만료된 경우
                    Log.e(TAG, "토큰이 만료되었습니다. 재로그인이 필요합니다.")
                    SessionManager.clearSession()  // 세션 정보 삭제
                    NetworkResult.Error("토큰이 만료되었습니다. 다시 로그인해주세요.")
                }
                else -> {
                    val errorMsg = "API 호출 실패: ${response.code()} ${response.message()}"
                    Log.e(TAG, errorMsg)
                    NetworkResult.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "루틴 시작 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun skipRoutineStep(routineId: String, stepIndex: Int, notes: String? = null): NetworkResult<RoutineActionResponse> {
        Log.d(TAG, "skipRoutineStep 호출됨 - 루틴 ID: $routineId, 단계 인덱스: $stepIndex")

        return try {
            // routineId가 비어 있는지 확인
            if (routineId.isEmpty()) {
                Log.e(TAG, "루틴 ID가 비어 있습니다.")
                return NetworkResult.Error("루틴 ID가 비어 있습니다.")
            }

            // 저장된 액세스 토큰과 사용자 ID 가져오기
            val accessToken = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (accessToken.isNullOrEmpty()) {
                Log.e(TAG, "토큰이 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("토큰이 없습니다. 로그인이 필요합니다.")
            }

            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            // 요청 바디 생성
            val request = RoutineStepActionRequest(
                routine_id = routineId,
                step_index = stepIndex,
                notes = null // 항상 null 설정
            )

            Log.d(TAG, "API 요청 데이터: routine_id=$routineId, step_index=$stepIndex")

            // API 호출
            val response = apiService.skipRoutineStep(
                userId = userId,
                authorization = "Bearer $accessToken",
                request = request
            )

            when {
                response.isSuccessful -> {
                    response.body()?.let { result ->
                        Log.d(TAG, "API 호출 성공: ${result.message}")
                        NetworkResult.Success(result)
                    } ?: run {
                        Log.e(TAG, "응답 본문이 비어 있습니다")
                        NetworkResult.Error("응답 본문이 비어 있습니다")
                    }
                }
                response.code() == 401 -> {
                    // 토큰이 만료된 경우
                    Log.e(TAG, "토큰이 만료되었습니다. 재로그인이 필요합니다.")
                    SessionManager.clearSession()  // 세션 정보 삭제
                    NetworkResult.Error("토큰이 만료되었습니다. 다시 로그인해주세요.")
                }
                else -> {
                    val errorMsg = "API 호출 실패: ${response.code()} ${response.message()}"
                    Log.e(TAG, errorMsg)
                    NetworkResult.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "루틴 단계 건너뛰기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }
}