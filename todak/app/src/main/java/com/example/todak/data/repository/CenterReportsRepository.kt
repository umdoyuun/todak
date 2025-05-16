package com.example.todak.data.repository

import android.util.Log
import com.example.todak.data.model.CenterReportDetailResponse
import com.example.todak.data.model.CenterReportsResponse
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.network.RetrofitClient
import com.example.todak.util.SessionManager

class CenterReportsRepository {
    private val apiService = RetrofitClient.apiService
    private val TAG = "CenterReportsRepository"

    suspend fun getCenterReports(): NetworkResult<CenterReportsResponse> {
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
            val response = apiService.getCenterReports(
                userId = userId,
                token = "Bearer $accessToken"
            )

            when {
                response.isSuccessful -> {
                    response.body()?.let { reportsResponse ->
                        Log.d(TAG, "보고서 목록 가져오기 성공: ${reportsResponse.reports.size}개 항목")
                        NetworkResult.Success(reportsResponse)
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
            Log.e(TAG, "보고서 목록 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun getCenterReportDetail(reportId: String): NetworkResult<CenterReportDetailResponse> {
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
            val response = apiService.getCenterReportDetail(
                reportId = reportId,
                userId = userId,
                token = "Bearer $accessToken"
            )

            when {
                response.isSuccessful -> {
                    response.body()?.let { reportDetail ->
                        Log.d(TAG, "보고서 상세 가져오기 성공: ${reportDetail.title}")
                        NetworkResult.Success(reportDetail)
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
            Log.e(TAG, "보고서 상세 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }
}