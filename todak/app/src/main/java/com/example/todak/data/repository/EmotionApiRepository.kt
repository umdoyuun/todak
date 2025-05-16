package com.example.todak.data.repository

import android.util.Log
import com.example.todak.data.model.EmotionRequest
import com.example.todak.data.model.EmotionResponse
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.network.RetrofitClient
import com.example.todak.util.SessionManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class EmotionApiRepository {
    private val apiService = RetrofitClient.apiService
    private val TAG = "EmotionApiRepository"

    suspend fun submitEmotion(
        emotion: String,
        context: String? = null,
        note: String? = null,
        audioFile: File? = null
    ): NetworkResult<EmotionResponse> {
        return try {
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

            // RequestBody 생성
            val emotionPart = emotion.toRequestBody("text/plain".toMediaTypeOrNull())
            val contextPart = context?.toRequestBody("text/plain".toMediaTypeOrNull())
            val notePart = note?.toRequestBody("text/plain".toMediaTypeOrNull())

            // 오디오 파일이 있는 경우 MultipartBody.Part 생성
            val audioFilePart = audioFile?.let {
                if (it.exists() && it.length() > 0) {
                    val requestFile = it.asRequestBody("audio/wav".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("audio_file", it.name, requestFile)
                } else null
            }

            // API 호출
            val response = apiService.submitEmotion(
                userId = userId,
                token = "Bearer $accessToken",
                emotion = emotionPart,
                context = contextPart,
                note = notePart,
                audioFile = audioFilePart
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "감정 등록 성공: $it")
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
            Log.e(TAG, "감정 등록 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }
}