package com.example.todak.data.repository

import android.util.Log
import com.example.todak.data.model.FcmScheduleActionResponse
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.ScheduleItem
import com.example.todak.data.model.ScheduleRequest
import com.example.todak.data.model.ScheduleResponse
import com.example.todak.data.model.ScheduleStatus
import com.example.todak.data.network.RetrofitClient
import com.example.todak.util.SessionManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleRepository {
    private val apiService = RetrofitClient.apiService
    private val TAG = "ScheduleRepository"

    // 전체 일정을 가져오는 함수
    suspend fun getAllSchedules(): NetworkResult<List<ScheduleItem>> {
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
            val response = apiService.getAllSchedules(
                userId = userId,
                authorization = "Bearer $accessToken"
            )

            if (response.isSuccessful) {
                response.body()?.let { scheduleListResponse ->
                    val schedules = scheduleListResponse.schedules
                    Log.d(TAG, "전체 일정 목록 가져오기 성공: ${schedules.size}개 항목")

                    // ScheduleItem으로 변환
                    val scheduleItems = convertToScheduleItems(schedules)
                    NetworkResult.Success(scheduleItems)
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
            Log.e(TAG, "전체 일정 목록 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    // 특정 날짜의 일정을 가져오는 함수
    suspend fun getSchedulesByDate(targetDate: String): NetworkResult<List<ScheduleItem>> {
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
            val response = apiService.getTodayScheduleList(
                userId = userId,
                authorization = "Bearer $accessToken",
                targetDate = targetDate
            )

            if (response.isSuccessful) {
                response.body()?.let { scheduleListResponse ->
                    val schedules = scheduleListResponse.schedules
                    Log.d(TAG, "$targetDate 일정 목록 가져오기 성공: ${schedules.size}개 항목")

                    // 시간 순으로 정렬
                    val sortedSchedules = schedules.sortedBy { it.start_time }

                    // ScheduleItem으로 변환
                    val scheduleItems = convertToScheduleItems(sortedSchedules)

                    // 상태별로 정렬 (완료와 연기는 같은 그룹, 진행 중, 시작 전 순서)
                    val sortedByStatus = scheduleItems.sortedWith(compareBy {
                        when (it.status) {
                            ScheduleStatus.COMPLETED, ScheduleStatus.POSTPONED -> 0  // 완료와 연기를 같은 우선순위로
                            ScheduleStatus.IN_PROGRESS -> 1  // 진행 중
                            ScheduleStatus.NOT_STARTED -> 2  // 시작 전
                        }
                    })

                    NetworkResult.Success(sortedByStatus)
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
            Log.e(TAG, "일정 목록 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun getTodaySchedules(): NetworkResult<List<ScheduleItem>> {
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
            val response = apiService.getTodayScheduleList(
                userId = userId,
                authorization = "Bearer $accessToken"
            )

            if (response.isSuccessful) {
                response.body()?.let { scheduleListResponse ->
                    val schedules = scheduleListResponse.schedules
                    Log.d(TAG, "일정 목록 가져오기 성공: ${schedules.size}개 항목")

                    // 시간 순으로 정렬
                    val sortedSchedules = schedules.sortedBy { it.start_time }

                    // ScheduleItem으로 변환
                    val scheduleItems = convertToScheduleItems(sortedSchedules)

                    // 상태별로 정렬 (완료와 연기는 같은 그룹, 진행 중, 시작 전 순서)
                    val sortedByStatus = scheduleItems.sortedWith(compareBy {
                        when (it.status) {
                            ScheduleStatus.COMPLETED, ScheduleStatus.POSTPONED -> 0  // 완료와 연기를 같은 우선순위로
                            ScheduleStatus.IN_PROGRESS -> 1  // 진행 중
                            ScheduleStatus.NOT_STARTED -> 2  // 시작 전
                        }
                    })

                    NetworkResult.Success(sortedByStatus)
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
            Log.e(TAG, "일정 목록 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun deleteSchedule(scheduleId: String): NetworkResult<Any> {
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
            val response = apiService.deleteSchedule(
                scheduleId = scheduleId,
                userId = userId,
                authorization = "Bearer $accessToken"
            )

            if (response.isSuccessful) {
                Log.d(TAG, "일정 삭제 성공: 일정 ID $scheduleId")
                NetworkResult.Success(response.body() ?: Unit)
            } else {
                val errorMsg = "API 호출 실패: ${response.code()} ${response.message()}"
                Log.e(TAG, errorMsg)
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "일정 삭제 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun updateSchedule(
        scheduleId: String,
        userId: String,
        authorization: String,
        requestBody: RequestBody
    ): NetworkResult<Any> {
        return try {
            val response = apiService.updateSchedule(
                scheduleId = scheduleId,
                userId = userId,
                authorization = authorization,
                requestBody = requestBody
            )

            if (response.isSuccessful) {
                NetworkResult.Success(response.body() ?: Unit)
            } else {
                NetworkResult.Error("API 호출 실패: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "일정 업데이트 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    // 일정 수정을 위한 간편 메서드
    suspend fun updateSchedule(
        scheduleId: String,
        title: String,
        category: String,
        startDate: String,
        endDate: String,
        startTime: String,
        endTime: String,
        repeatType: String,
        repeatDays: List<String>,
        note: String
    ): NetworkResult<Any> {
        try {
            // 저장된 액세스 토큰과 사용자 ID 가져오기
            val accessToken = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (accessToken.isNullOrEmpty() || userId.isNullOrEmpty()) {
                Log.e(TAG, "토큰 또는 사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("토큰 또는 사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            // API 요청 본문 생성
            val jsonBody = """
                {
                    "title": "$title",
                    "category": "$category",
                    "start_date": "$startDate",
                    "end_date": "$endDate",
                    "start_time": "$startTime",
                    "end_time": "$endTime",
                    "repeat": {
                        "type": "$repeatType",
                        "days": ${repeatDays.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}
                    },
                    "note": "$note"
                }
            """.trimIndent()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            return updateSchedule(
                scheduleId = scheduleId,
                userId = userId,
                authorization = "Bearer $accessToken",
                requestBody = requestBody
            )
        } catch (e: Exception) {
            Log.e(TAG, "일정 업데이트 오류", e)
            return NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    private fun convertToScheduleItems(schedules: List<ScheduleResponse>): List<ScheduleItem> {
        return schedules.mapIndexed { index, schedule ->
            val formattedStartTime = formatTime(schedule.start_time)
            val formattedEndTime = formatTime(schedule.end_time)

            ScheduleItem(
                id = schedule._id,
                title = schedule.title,
                startDate = schedule.start_date,
                endDate = schedule.end_date,
                startTime = formattedStartTime,
                endTime = formattedEndTime,
                category = schedule.category ?: "기타",  // null 체크
                isCompleted = schedule.status == "completed",  // status가 null이면 false가 됨
                showTimeline = true,
                note = schedule.note ?: "",  // null 체크
                status = convertStatusFromString(schedule.status),  // status가 null일 수 있음
                repeat = schedule.repeat  // repeat가 null일 수 있음
            )
        }
    }

    private fun convertStatusFromString(status: String?): ScheduleStatus {
        return when (status) {
            "started" -> ScheduleStatus.IN_PROGRESS
            "completed" -> ScheduleStatus.COMPLETED
            "postponed" -> ScheduleStatus.POSTPONED
            else -> ScheduleStatus.NOT_STARTED
        }
    }

    private fun formatTime(time: String): String {
        try {
            val inputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = inputFormat.parse(time)

            // 분이 0인 경우와 아닌 경우를 구분하여 다른 형식 사용
            return date?.let {
                val calendar = Calendar.getInstance()
                calendar.time = it
                val minute = calendar.get(Calendar.MINUTE)

                if (minute == 0) {
                    // 분이 0인 경우 "시"까지만 표시
                    val outputFormat = SimpleDateFormat("a h시", Locale.KOREAN)
                    outputFormat.format(it)
                } else {
                    // 분이 0이 아닌 경우 "분"까지 표시
                    val outputFormat = SimpleDateFormat("a h시 m분", Locale.KOREAN)
                    outputFormat.format(it)
                }
            } ?: time
        } catch (e: Exception) {
            return time
        }
    }

    suspend fun createSchedule(scheduleRequest: ScheduleRequest): NetworkResult<Boolean> {
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
            val response = apiService.createSchedule(
                userId = userId,
                authorization = "Bearer $accessToken",
                scheduleRequest = scheduleRequest
            )

            if (response.isSuccessful) {
                response.body()?.let { scheduleRequestResponse ->
                    Log.d(TAG, "일정 생성 성공: ${scheduleRequestResponse.message}, ID: ${scheduleRequestResponse.id}")
                    NetworkResult.Success(true)
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
            Log.e(TAG, "일정 생성 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun startSchedule(scheduleId: String): NetworkResult<FcmScheduleActionResponse> {
        return try {
            val response = apiService.startSchedule(
                userId = SessionManager.getUserId() ?: "",
                token = "Bearer ${SessionManager.getAuthToken() ?: ""}",
                scheduleId = scheduleId
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    NetworkResult.Success(it)
                } ?: NetworkResult.Error("응답 본문이 비어 있습니다")
            } else {
                NetworkResult.Error("API 호출 실패: ${response.code()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun completeSchedule(scheduleId: String): NetworkResult<FcmScheduleActionResponse> {
        return try {
            val response = apiService.completeSchedule(
                userId = SessionManager.getUserId() ?: "",
                token = "Bearer ${SessionManager.getAuthToken() ?: ""}",
                scheduleId = scheduleId
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    NetworkResult.Success(it)
                } ?: NetworkResult.Error("응답 본문이 비어 있습니다")
            } else {
                NetworkResult.Error("API 호출 실패: ${response.code()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun postponeSchedule(scheduleId: String): NetworkResult<FcmScheduleActionResponse> {
        return try {
            val response = apiService.postponeSchedule(
                userId = SessionManager.getUserId() ?: "",
                token = "Bearer ${SessionManager.getAuthToken() ?: ""}",
                scheduleId = scheduleId
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    NetworkResult.Success(it)
                } ?: NetworkResult.Error("응답 본문이 비어 있습니다")
            } else {
                NetworkResult.Error("API 호출 실패: ${response.code()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }
}