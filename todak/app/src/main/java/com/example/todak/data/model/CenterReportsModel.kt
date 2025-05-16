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

// 보고서 상세 응답 모델 - summary 부분만 중점적으로 사용
data class CenterReportDetailResponse(
    val bi_user_id: String,
    val user_name: String,
    val title: String,
    val period: String,
    val start_date: String,
    val end_date: String,
    val created_at: String,
    val summary: Summary,
    val report_id: String
    // 필요 없는 필드는 생략
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