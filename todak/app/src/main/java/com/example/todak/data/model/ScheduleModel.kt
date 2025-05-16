package com.example.todak.data.model

data class ScheduleRequest(
    val title: String,
    val category: String,
    val start_date: String,
    val end_date: String,
    val start_time: String,
    val end_time: String,
    val repeat: ScheduleRepeatInfo,
    val note: String
)

data class ScheduleRepeatInfo(
    val type: String,
    val days: List<String>
)

data class ScheduleRequestResponse(
    val message: String,
    val id: String
)

data class ScheduleListResponse(
    val date: String,
    val schedules: List<ScheduleResponse>
)

// 개별 일정 항목
data class ScheduleResponse(
    val _id: String,
    val user_id: String,
    val creator_id: String,
    val title: String,
    val category: String,
    val start_date: String,
    val end_date: String,
    val start_time: String,
    val end_time: String,
    val repeat: RepeatInfo,
    val note: String,
    val created_at: String,
    val status: String,
    val last_status_update: String,
    val status_notes: String
)

// 반복 정보
data class RepeatInfo(
    val type: String,
    val days: List<String>
)

// UI에 표시할 형태로 변환한 일정 항목
data class ScheduleItem(
    val id: String,
    val title: String,
    val startTime: String,
    val endTime: String,
    val category: String,
    val isCompleted: Boolean,
    val showTimeline: Boolean,
    val note: String,
    val status: ScheduleStatus = ScheduleStatus.NOT_STARTED
)

enum class ScheduleStatus {
    NOT_STARTED,    // 시작 전
    IN_PROGRESS,    // 진행 중
    COMPLETED,      // 완료됨
    POSTPONED       // 연기됨
}