package com.example.todak.data.model;

data class MissionsResponse(
    val total: Int,
    val missions: List<MissionItem>
)

data class MissionItem(
    val _id: String,
    val title: String,
    val description: String,
    val category: String,
    val difficulty: String,
    val tips: List<String>,
    val center_id: String,
    val creator_id: String,
    val materials: List<String>,
    val estimated_time: Int,
    val status: String,
    val created_at: String,
    val updated_at: String,
    val is_participated: Boolean,
    val participation_id: String? = null,
    val participation_status: String? = null
)

data class MissionDetailResponse(
    val _id: String,
    val title: String,
    val description: String,
    val category: String,
    val difficulty: String,
    val steps: List<MissionStep>,
    val tips: List<String>,
    val center_id: String,
    val creator_id: String,
    val materials: List<String>,
    val estimated_time: Int,
    val status: String,
    val created_at: String,
    val updated_at: String,
    val is_participated: Boolean,
)

data class MissionStep(
    val order: Int,
    val title: String,
    val content: String
)

data class MissionJoinRequest(
    val mission_id: String
)

data class MissionJoinResponse(
    val _id: String,
    val mission_id: String,
    val bi_user_id: String,
    val center_id: String,
    val status: String,
    val start_date: String?,
    val end_date: String?,
    val step_progress: List<StepProgress>,
    val feedback: String?,
    val created_at: String,
    val updated_at: String
)

// 단계 진행 상태 모델
data class StepProgress(
    val step_order: Int,
    val status: String,
    val started_at: String?,
    val completed_at: String?
)

data class MissionStartRequest(
    val participation_id: String
)

data class CompleteMissionStepRequest(
    val participation_id: String,
    val step_order: Int,
    val notes: String
)

data class MissionParticipationDetail(
    val _id: String,
    val mission_id: String,
    val bi_user_id: String,
    val center_id: String,
    val status: String,
    val start_date: String?,
    val end_date: String?,
    val step_progress: List<ParticipationStepProgress>,
    val feedback: String?,
    val created_at: String,
    val updated_at: String
)

data class ParticipationStepProgress(
    val step_order: Int,
    val status: String,
    val started_at: String?,
    val completed_at: String?,
    val notes: String?
)

data class MissionFailRequest(
    val participation_id: String,
    val reason: String
)