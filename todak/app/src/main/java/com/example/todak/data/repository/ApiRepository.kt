package com.example.todak.data.repository

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.util.Log
import com.example.todak.data.model.AudioResponse
import com.example.todak.data.model.ChatRequest
import com.example.todak.data.model.ChatResponse
import com.example.todak.data.model.LoginRequest
import com.example.todak.data.model.LoginResponse
import com.example.todak.data.model.ManualDetailResponse
import com.example.todak.data.model.MenuResponse
import com.example.todak.data.model.SignupResponse
import com.example.todak.data.network.RetrofitClient
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.OcrResponse
import com.example.todak.data.model.ScheduleItem
import com.example.todak.data.model.ScheduleResponse
import com.example.todak.data.model.SignupRequest
import com.example.todak.data.model.StoreItem
import com.example.todak.util.SessionManager
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale

class ApiRepository {

    private val apiService = RetrofitClient.apiService
    private val TAG = "ApiRepository"

    suspend fun uploadAudio(audioFile: File): NetworkResult<AudioResponse> {
        return try {
            Log.d(TAG, "오디오 파일 업로드 시작: ${audioFile.absolutePath}, 크기: ${audioFile.length()} 바이트")

            // SessionManager에서 저장된 userId 가져오기
            val userId = SessionManager.getUserId()
            val accessToken = SessionManager.getAuthToken()
            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            // 파일을 MultipartBody.Part로 변환
            val requestFile = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            val audioPart  = MultipartBody.Part.createFormData("audio_file", audioFile.name, requestFile)

            // 저장된 세션 ID가 있으면 요청에 포함
            val sessionId = SessionManager.getChatSessionId()
            val sessionIdPart = sessionId?.let {
                val requestBody = it.toRequestBody("text/plain".toMediaTypeOrNull())
                requestBody // 세션 ID가 있으면 RequestBody 사용
            }

            Log.d(TAG, "API 호출 시작: userId=$userId, accessToken=$accessToken, sessionId=$sessionId")
            // API 호출 - userId를 헤더에 포함하여 전송
            val response = apiService.uploadAudio(
                userId,
                "Bearer $accessToken", // JWT 토큰
                audioPart,
                sessionIdPart
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "오디오 파일 업로드 성공: ${it}")

                    it.session_id?.let { newSessionId ->
                        SessionManager.saveChatSessionId(newSessionId)
                        Log.d(TAG, "세션 ID 저장: $newSessionId")
                    }

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
            Log.e(TAG, "오디오 업로드 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun sendChatMessage(message: String): NetworkResult<ChatResponse> {
        return try {
            Log.d(TAG, "채팅 메시지 전송 시작: $message")

            val userId = SessionManager.getUserId()
            val accessToken = SessionManager.getAuthToken()

            if (userId.isNullOrEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.")
                return NetworkResult.Error("사용자 ID가 없습니다. 로그인이 필요합니다.")
            }

            val sessionId = SessionManager.getChatSessionId()

            // 요청 본문 생성
            val requestBody = ChatRequest(
                message = message,
                session_id = sessionId
            )

            Log.d(TAG, "API 호출 시작: userId=$userId, accessToken=$accessToken, sessionId=$sessionId")

            // API 호출
            val response = apiService.sendChatMessage(
                userId,
                "Bearer $accessToken",
                requestBody
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "채팅 메시지 전송 성공: $it")

                    // 새로운 세션 ID가 있으면 저장
                    it.session_id?.let { newSessionId ->
                        SessionManager.saveChatSessionId(newSessionId)
                        Log.d(TAG, "세션 ID 저장: $newSessionId")
                    }

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
            Log.e(TAG, "채팅 메시지 전송 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun signup(request: SignupRequest): NetworkResult<SignupResponse> {
        return try {
            // JSON 문자열로 변환할 맵 생성 (null이 아닌 필드만 포함)
            val requestMap = mutableMapOf<String, Any>()

            // 필수 필드 추가
            requestMap["name"] = request.name
            requestMap["email"] = request.email
            requestMap["password"] = request.password
            requestMap["type"] = request.type

            // 선택 필드 추가 (null이 아닌 경우만)
            request.gender?.let {
                if (it.isNotEmpty()) requestMap["gender"] = it
            }
            request.phone?.let {
                if (it.isNotEmpty()) requestMap["phone"] = it
            }
            request.address?.let {
                if (it.isNotEmpty()) requestMap["address"] = it
            }
            request.birthdate?.let {
                if (it.isNotEmpty()) {
                    // 날짜 형식 변환 (YYYY.MM.DD -> YYYY-MM-DD)
                    val formattedDate = it.replace(".", "-")
                    requestMap["birthDate"] = formattedDate
                }
            }

            // JSON 문자열로 변환
            val gson = Gson()
            val jsonRequest = gson.toJson(requestMap)

            // RequestBody 생성
            val requestBody = jsonRequest.toRequestBody("application/json".toMediaTypeOrNull())

            // API 호출
            val response = apiService.signup(requestBody)

            if (response.isSuccessful) {
                response.body()?.let {
                    NetworkResult.Success(it)
                } ?: NetworkResult.Error("응답 본문이 비어 있습니다")
            } else {
                NetworkResult.Error("API 호출 실패: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "회원가입 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    // 멀티파트 파트 생성 헬퍼 메서드
    private fun createPart(name: String, value: String): MultipartBody.Part {
        return MultipartBody.Part.createFormData(name, value)
    }

    suspend fun login(request: LoginRequest): NetworkResult<LoginResponse> {
        return try {
            val response = apiService.login(request)

            if (response.isSuccessful) {
                response.body()?.let {
                    // 토큰 저장
                    it.accessToken?.let { accessToken -> SessionManager.saveAuthToken(accessToken) }
                    it.refreshToken?.let { refreshToken -> SessionManager.saveAuthToken(refreshToken)}

                    NetworkResult.Success(it)
                } ?: NetworkResult.Error("응답 본문이 비어 있습니다")
            } else {
                // 에러 본문 추출
                val errorBody = response.errorBody()?.string()
                NetworkResult.Error(
                    message = "API 호출 실패: ${response.code()} ${response.message()}",
                    errorBody = errorBody
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "로그인 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun getRelationToken(): NetworkResult<String> {
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
            val response = apiService.getRelationToken(
                token = "Bearer $accessToken",
                userId = userId
            )

            if (response.isSuccessful) {
                response.body()?.let { tokenResponse ->
                    val token = tokenResponse.tokenId
                    Log.d(TAG, "인증 토큰 가져오기 성공")
                    NetworkResult.Success(token)
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
            Log.e(TAG, "인증 토큰 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun getStores(): NetworkResult<List<StoreItem>> {
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
            val response = apiService.getStores(
                token = "Bearer $accessToken",
                userId = userId
            )

            if (response.isSuccessful) {
                response.body()?.let { storeListResponse ->
                    Log.d(TAG, "가게 목록 가져오기 성공: ${storeListResponse.stores.size}개 항목")
                    NetworkResult.Success(storeListResponse.stores)
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
            Log.e(TAG, "가게 목록 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun getMenus(storeId: String): NetworkResult<List<MenuResponse>> {
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
            val response = apiService.getMenus(
                token = "Bearer $accessToken",
                userId = userId,
                storeId = storeId
            )

            if (response.isSuccessful) {
                response.body()?.let { menuListResponse ->
                    val manuals = menuListResponse.manuals
                    Log.d(TAG, "메뉴 목록 가져오기 성공: ${manuals.size}개 항목")
                    NetworkResult.Success(manuals)
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
            Log.e(TAG, "메뉴 목록 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun getManualDetail(storeId: String, title: String): NetworkResult<ManualDetailResponse> {
        return try {
            // 토큰과 사용자 ID 가져오기
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
            val response = apiService.getManualDetail(
                token = "Bearer $accessToken",
                userId = userId,
                storeId = storeId,
                title = title
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "매뉴얼 상세 정보 가져오기 성공")
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
            Log.e(TAG, "매뉴얼 상세 정보 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun performOcr(imageFile: File, storeId: String): NetworkResult<OcrResponse> {
        return try {
            // 토큰과 사용자 ID 가져오기
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

            // 이미지 파일을 MultipartBody.Part로 변환
            val requestFile = RequestBody.create(
                "image/jpeg".toMediaTypeOrNull(), // 또는 "image/png"
                imageFile
            )

            val filePart = MultipartBody.Part.createFormData(
                "file",
                imageFile.name,
                requestFile
            )

            // store_id를 RequestBody로 변환
            val storeIdBody = RequestBody.create(
                "text/plain".toMediaTypeOrNull(),
                storeId
            )

            // API 호출
            val response = apiService.postOcrRequest(
                token = "Bearer $accessToken",
                userId = userId,
                file = filePart,
                storeId = storeIdBody
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "OCR 처리 성공: ${it.menus.size}개 메뉴 인식됨")
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
            Log.e(TAG, "OCR 처리 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }
}