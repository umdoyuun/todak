package com.example.todak.data.network

import com.example.todak.data.model.AudioResponse
import com.example.todak.data.model.Center
import com.example.todak.data.model.CenterReportDetailResponse
import com.example.todak.data.model.CenterReportsResponse
import com.example.todak.data.model.ChatRequest
import com.example.todak.data.model.ChatResponse
import com.example.todak.data.model.CompleteMissionStepRequest
import com.example.todak.data.model.EmotionResponse
import com.example.todak.data.model.FcmScheduleActionResponse
import com.example.todak.data.model.FcmScheduleResponse
import com.example.todak.data.model.FcmTokenRequest
import com.example.todak.data.model.FcmTokenResponse
import com.example.todak.data.model.LoginRequest
import com.example.todak.data.model.LoginResponse
import com.example.todak.data.model.ManualDetailResponse
import com.example.todak.data.model.MenuListResponse
import com.example.todak.data.model.MissionDetailResponse
import com.example.todak.data.model.MissionFailRequest
import com.example.todak.data.model.MissionJoinRequest
import com.example.todak.data.model.MissionJoinResponse
import com.example.todak.data.model.MissionParticipationDetail
import com.example.todak.data.model.MissionStartRequest
import com.example.todak.data.model.MissionsResponse
import com.example.todak.data.model.OcrResponse
import com.example.todak.data.model.RoutineActionResponse
import com.example.todak.data.model.RoutineResponse
import com.example.todak.data.model.RoutineStartRequest
import com.example.todak.data.model.RoutineStepActionRequest
import com.example.todak.data.model.ScheduleListResponse
import com.example.todak.data.model.ScheduleRequest
import com.example.todak.data.model.ScheduleRequestResponse
import com.example.todak.data.model.SetWeeklyBudgetRequest
import com.example.todak.data.model.SetWeeklyBudgetResponse
import com.example.todak.data.model.SignupRequest
import com.example.todak.data.model.SignupResponse
import com.example.todak.data.model.SpendingBatchRequest
import com.example.todak.data.model.SpendingItem
import com.example.todak.data.model.SpendingResponse
import com.example.todak.data.model.StoreListResponse
import com.example.todak.data.model.TokenResponse
import com.example.todak.data.model.WakeupInfoResponse
import com.example.todak.data.model.UserProfile
import com.example.todak.data.model.WeeklyBudgetResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @Multipart
    @POST("api/voice")
    suspend fun uploadAudio( @Header("X-User-ID") userId: String,
                             @Header("Authorization") token: String, // 추가
                             @Part audioFile: MultipartBody.Part,
                             @Part("session_id") sessionId: okhttp3.RequestBody? = null
    ): Response<AudioResponse>

    @POST("api/voice/chat")
    suspend fun sendChatMessage(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Body requestBody: ChatRequest
    ): Response<ChatResponse>

    @Multipart
    @POST("auth/signup")
    suspend fun signup(
        @Part("request") requestBody: RequestBody
    ): Response<SignupResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("profile/getUserProfile/{userId}")
    suspend fun getUserProfile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String
    ): Response<UserProfile>

    @GET("relation/biuser/getCenters")
    suspend fun getUserCenters(
        @Header("Authorization") authorization: String,
        @Header("X-User-ID") userId: String
    ): Response<List<Center>>

    @GET("profile/getRelationToken/")
    suspend fun getRelationToken(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String
    ): Response<TokenResponse>

    @GET("api/my-manuals/stores")
    suspend fun getStores(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String
    ): Response<StoreListResponse>

    @GET("api/my-manuals/stores/{store_id}")
    suspend fun getMenus(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String,
        @Path("store_id") storeId: String
    ): Response<MenuListResponse>

    @GET("api/my-manuals/stores/{store_id}/{title}")
    suspend fun getManualDetail(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String,
        @Path("store_id") storeId: String,
        @Path("title") title: String
    ): Response<ManualDetailResponse>

    @Multipart
    @POST("api/ocr")
    suspend fun postOcrRequest(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String,
        @Part file: MultipartBody.Part,
        @Part("store_id") storeId: RequestBody
    ): Response<OcrResponse>

    @GET("/api/schedule/today")
    suspend fun getTodayScheduleList(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") authorization: String,
        @Query("target_date") targetDate: String? = null
    ): Response<ScheduleListResponse>

    @GET("/api/schedule/self/list")
    suspend fun getAllSchedules(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") authorization: String
    ): Response<ScheduleListResponse>

    @DELETE("/api/schedule/self/{schedule_id}")
    suspend fun deleteSchedule(
        @Path("schedule_id") scheduleId: String,
        @Header("X-User-ID") userId: String,
        @Header("Authorization") authorization: String,
    ): Response<Any>

    @PUT("/api/schedule/self/{schedule_id}")
    suspend fun updateSchedule(
        @Path("schedule_id") scheduleId: String,
        @Header("X-User-ID") userId: String,
        @Header("Authorization") authorization: String,
        @Body requestBody: RequestBody
    ): Response<Any>

    @POST("/api/schedule/self")
    suspend fun createSchedule(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") authorization: String,
        @Body scheduleRequest: ScheduleRequest
    ): Response<ScheduleRequestResponse>

    @GET("/api/mission-participations/available-missions")
    suspend fun getAvailableMissions(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") authorization: String
    ): Response<MissionsResponse>

    @GET("api/mission-participations/mission/{mission_id}")
    suspend fun getMissionDetail(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String,
        @Path("mission_id") missionId: String
    ): Response<MissionDetailResponse>

    @POST("api/mission-participations/join")
    suspend fun joinMission(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String,
        @Body request: MissionJoinRequest
    ): Response<MissionJoinResponse>

    @POST("api/mission-participations/start")
    suspend fun startMission(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String,
        @Body request: MissionStartRequest
    ): Response<Any>

    @POST("api/mission-participations/complete-step")
    suspend fun completeMissionStep(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String,
        @Body request: CompleteMissionStepRequest
    ): Response<Any>

    @GET("api/mission-participations/detail/{participation_id}")
    suspend fun getMissionParticipationDetail(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String,
        @Path("participation_id") participationId: String
    ): Response<MissionParticipationDetail>

    @POST("api/mission-participations/fail")
    suspend fun failMission(
        @Header("Authorization") token: String,
        @Header("X-User-ID") userId: String,
        @Body request: MissionFailRequest
    ): Response<Any>

    // FCM API
    @POST("api/devices/register")
    suspend fun registerDeviceToken(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Body request: FcmTokenRequest
    ): Response<FcmTokenResponse>

    @DELETE("api/devices/unregister")
    suspend fun unregisterDeviceToken(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Body request: FcmTokenRequest
    ): Response<FcmTokenResponse>

    @POST("api/schedule-actions/start/{scheduleId}")
    suspend fun startSchedule(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Path("scheduleId") scheduleId: String,
        @Query("notes") notes: String? = null
    ): Response<FcmScheduleActionResponse>

    @POST("api/schedule-actions/complete/{scheduleId}")
    suspend fun completeSchedule(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Path("scheduleId") scheduleId: String,
        @Query("notes") notes: String? = null
    ): Response<FcmScheduleActionResponse>

    @POST("api/schedule-actions/postpone/{scheduleId}")
    suspend fun postponeSchedule(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Path("scheduleId") scheduleId: String,
        @Query("notes") notes: String? = null
    ): Response<FcmScheduleActionResponse>

    @GET("api/schedule/today")
    suspend fun getTodaySchedules(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Query("target_date") targetDate: String? = null
    ): Response<FcmScheduleResponse>

    @GET("api/morning-routines/me")
    suspend fun getMyRoutines(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String
    ): Response<RoutineResponse>

    @POST("api/routine-actions/complete-step")
    suspend fun completeRoutineStep(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Body request: RoutineStepActionRequest
    ): Response<RoutineActionResponse>

    @POST("api/routine-actions/skip-step")
    suspend fun skipRoutineStep(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") authorization: String,
        @Body request: RoutineStepActionRequest
    ): Response<RoutineActionResponse>

    @POST("api/routine-actions/start")
    suspend fun startRoutine(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") authorization: String,
        @Body request: RoutineStartRequest
    ): Response<RoutineActionResponse>

    @Multipart
    @POST("api/emotion")
    suspend fun submitEmotion(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Part("emotion") emotion: RequestBody,
        @Part("context") context: RequestBody? = null,
        @Part("note") note: RequestBody? = null,
        @Part audioFile: MultipartBody.Part? = null
    ): Response<EmotionResponse>

    @POST("api/spending/")
    suspend fun submitSpending(@Body request: SpendingItem): Response<SpendingResponse>

    @POST("api/spending/batch")
    suspend fun submitSpendingBatch(@Body items: List<SpendingItem>): Response<SpendingResponse>

    @GET("api/weekly-budget/")
    suspend fun getWeeklyBudget(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String
    ): Response<WeeklyBudgetResponse>

    @POST("api/weekly-budget/")
    suspend fun setWeeklyBudget(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String,
        @Body request: SetWeeklyBudgetRequest
    ): Response<SetWeeklyBudgetResponse>

    @GET("api/morning-routines/wakeup-info")
    suspend fun getWakeupInfo(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String
    ): Response<WakeupInfoResponse>

    @GET("api/center-reports/bi-user")
    suspend fun getCenterReports(
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String
    ): Response<CenterReportsResponse>

    @GET("api/center-reports/bi-user/{report_id}")
    suspend fun getCenterReportDetail(
        @Path("report_id") reportId: String,
        @Header("X-User-ID") userId: String,
        @Header("Authorization") token: String
    ): Response<CenterReportDetailResponse>
}
