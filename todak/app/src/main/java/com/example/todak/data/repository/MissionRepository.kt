import android.util.Log
import com.example.todak.data.model.CompleteMissionStepRequest
import com.example.todak.data.model.MissionDetailResponse
import com.example.todak.data.model.MissionFailRequest
import com.example.todak.data.model.MissionItem
import com.example.todak.data.model.MissionJoinRequest
import com.example.todak.data.model.MissionJoinResponse
import com.example.todak.data.model.MissionParticipationDetail
import com.example.todak.data.model.MissionStartRequest
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.network.RetrofitClient
import com.example.todak.util.SessionManager

class MissionRepository {
    private val apiService = RetrofitClient.apiService
    private val TAG = "MissionRepository"


    suspend fun getAvailableMissions(): NetworkResult<List<MissionItem>> {
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
            val response = apiService.getAvailableMissions(
                userId = userId,
                authorization = "Bearer $accessToken"
            )

            if (response.isSuccessful) {
                response.body()?.let { missionResponse ->
                    val missions = missionResponse.missions
                    Log.d(TAG, "미션 목록 가져오기 성공: ${missions.size}개 항목")
                    NetworkResult.Success(missions)
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
            Log.e(TAG, "미션 목록 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun getMissionDetail(missionId: String): NetworkResult<MissionDetailResponse> {
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
            val response = apiService.getMissionDetail(
                token = "Bearer $accessToken",
                userId = userId,
                missionId = missionId
            )

            // 응답 처리
            if (response.isSuccessful) {
                response.body()?.let { missionDetail ->
                    Log.d(TAG, "미션 상세 정보 가져오기 성공: ${missionDetail._id}")
                    NetworkResult.Success(missionDetail)
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
            Log.e(TAG, "미션 상세 정보 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun joinMission(missionId: String): NetworkResult<MissionJoinResponse> {
        return try {
            val token = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (token.isNullOrEmpty() || userId.isNullOrEmpty()) {
                return NetworkResult.Error("로그인이 필요합니다.")
            }

            // API 호출
            val request = MissionJoinRequest(mission_id = missionId)
            val response = apiService.joinMission(
                token = "Bearer $token",
                userId = userId,
                request = request
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    NetworkResult.Success(it)
                } ?: NetworkResult.Error("응답 본문이 비어 있습니다")
            } else {
                NetworkResult.Error("미션 참여 실패: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun startMission(participationId: String): NetworkResult<Any> {
        return try {
            val token = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (token.isNullOrEmpty() || userId.isNullOrEmpty()) {
                return NetworkResult.Error("로그인이 필요합니다.")
            }

            val request = MissionStartRequest(participation_id = participationId)
            val response = apiService.startMission(
                token = "Bearer $token",
                userId = userId,
                request = request
            )

            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("미션 시작 실패: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun completeMissionStep(participationId: String, stepOrder: Int, note: String): NetworkResult<Any> {
        return try {
            val token = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (token.isNullOrEmpty() || userId.isNullOrEmpty()) {
                return NetworkResult.Error("로그인이 필요합니다.")
            }

            // API 호출
            val request = CompleteMissionStepRequest(
                participation_id = participationId,
                step_order = stepOrder,
                notes = note
            )

            val response = apiService.completeMissionStep(
                token = "Bearer $token",
                userId = userId,
                request = request
            )

            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("단계 완료 실패: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun getMissionParticipationDetail(participationId: String): NetworkResult<MissionParticipationDetail> {
        return try {
            val accessToken = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (accessToken.isNullOrEmpty() || userId.isNullOrEmpty()) {
                return NetworkResult.Error("로그인이 필요합니다.")
            }

            val response = apiService.getMissionParticipationDetail(
                token = "Bearer $accessToken",
                userId = userId,
                participationId = participationId
            )

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "미션 참가 상세 정보 가져오기 성공: ${it._id}")
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
            Log.e(TAG, "미션 참가 상세 정보 가져오기 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }

    suspend fun failMission(participationId: String, reason: String): NetworkResult<Any> {
        return try {
            val token = SessionManager.getAuthToken()
            val userId = SessionManager.getUserId()

            if (token.isNullOrEmpty() || userId.isNullOrEmpty()) {
                return NetworkResult.Error("로그인이 필요합니다.")
            }

            val request = MissionFailRequest(
                participation_id = participationId,
                reason = reason
            )

            val response = apiService.failMission(
                token = "Bearer $token",
                userId = userId,
                request = request
            )

            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                val errorMsg = "API 호출 실패: ${response.code()} ${response.message()}"
                Log.e(TAG, errorMsg)
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "미션 실패/포기 처리 중 오류", e)
            NetworkResult.Error("오류 발생: ${e.message}")
        }
    }
}