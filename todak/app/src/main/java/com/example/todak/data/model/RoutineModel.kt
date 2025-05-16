package com.example.todak.data.model

import java.io.Serializable

data class RoutineResponse(
    val user_id: String,
    val date: String,
    val routines: List<RoutineItem>,
    val total: Int
)

data class RoutineItem(
    val routine_id: String,
    val title: String,
    val description: String?,
    val overall_status: String,
    val current_step: Int,
    val started: Boolean,
    val start_time: String?,
    val progress: RoutineProgress,
    val steps: List<RoutineStep>,
): java.io.Serializable

data class RoutineProgress(
    val total_steps: Int,
    val completed_steps: Int,
    val skipped_steps: Int,
    val pending_steps: Int,
    val progress_percentage: Double
): Serializable

data class RoutineStep(
    val step_index: Int,
    val title: String,
    val description: String?,
    val duration_minutes: Int,
    val status: String,
    val logged_at: String?,
    val notes: String?
): Serializable

data class RoutineStepActionRequest(
    val routine_id: String,
    val step_index: Int,
    val notes: String? = null
)

data class RoutineStartRequest(
    val routine_id: String,
    val notes: String? = null  // null 가능하도록 수정
)

data class RoutineActionResponse(
    val message: String
)

// 루틴 생성/수정 요청 모델
data class RoutineCreateUpdateRequest(
    val title: String,
    val description: String,
    val steps: List<RoutineStepRequest>,
    val is_active: Boolean? = null // 수정 시에만 사용, 생성 시에는 null
)

data class RoutineStepRequest(
    val step_index: Int,
    val title: String,
    val description: String,
    val duration_minutes: Int
)

// 응답 모델
data class RoutineCreateResponse(
    val message: String,
    val id: String
)