package com.example.todak.data.model

// 보고서 목록 응답 모델
data class CenterReportsResponse(
    val total: Int,
    val limit: Int,
    val skip: Int,
    val reports: List<ReportSummary>
)

data class ReportSummary(
    val user_name: String,
    val title: String,
    val period: String,
    val start_date: String,
    val end_date: String,
    val created_at: String,
    val report_id: String
)

// 보고서 상세 응답 모델에 statistics 필드 추가
data class CenterReportDetailResponse(
    val bi_user_id: String,
    val user_name: String,
    val title: String,
    val period: String,
    val start_date: String,
    val end_date: String,
    val created_at: String,
    val summary: Summary,
    val statistics: Statistics,
    val report_id: String
)

// Statistics 관련 모델 추가
data class Statistics(
    val routine_stats: RoutineStats,
    val schedule_stats: ScheduleStats,
    val mission_stats: MissionStats,
    val emotion_stats: EmotionStats
)

data class RoutineStats(
    val total_logs: Int,
    val step_statuses: StepStatuses,
    val routines: Map<String, RoutineDetail>
)

data class StepStatuses(
    val completed: Int,
    val skipped: Int,
    val started: Int
)

data class RoutineDetail(
    val title: String,
    val total_steps: Int,
    val logs_count: Int,
    val completion_rate: Double
)

data class ScheduleStats(
    val total_logs: Int,
    val statuses: ScheduleStatuses,
    val daily_completion_rates: List<DailyCompletionRate>
)

data class ScheduleStatuses(
    val started: Int,
    val completed: Int,
    val missed: Int,
    val postponed: Int
)

data class DailyCompletionRate(
    val date: String,
    val total: Int,
    val completed: Int,
    val missed: Int,
    val completion_rate: Double
)

data class MissionStats(
    val total_missions: Int,
    val statuses: MissionStatuses,
    val missions: Map<String, MissionDetail>
)

data class MissionStatuses(
    val pending: Int,
    val in_progress: Int,
    val completed: Int,
    val failed: Int
)

data class MissionDetail(
    val title: String,
    val category: String,
    val difficulty: String,
    val status: String,
    val total_steps: Int,
    val completed_steps: Int,
    val progress_percentage: Double
)

data class EmotionStats(
    val total_logs: Int,
    val emotion_counts: Map<String, Int>
)

data class Summary(
    val greeting: String,
    val daily_summary: String,
    val achievements: List<String>,
    val routine_highlights: String,
    val schedule_highlights: String,
    val mission_highlights: String,
    val emotional_insights: String,
    val tomorrow_tips: List<String>,
    val closing_message: String
)