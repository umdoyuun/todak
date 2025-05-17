package com.example.todak.data.model

data class SpendingItem(
    val amount: Int,
    val category: String,
    val store_name: String,
    val region: String,
    val method: String,
    val timestamp: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

data class SpendingBatchRequest(
    val items: List<SpendingItem>
)

data class SpendingResponse(
    val status: String,
    val message: String,
    val count: Int
)

data class WakeupInfoResponse(
    val has_info: Boolean,
    val target_date: String,
    val recommended_time: String,
    val message: String,
    val has_schedule: Boolean,
    val next_schedule: NextSchedule?,
    val budget_status: WeeklyBudgetResponse,
    val daily_spending: Map<String, Int>
)

data class NextSchedule(
    val schedule_id: String,
    val title: String,
    val start_time: String,
    val category: String,
    val date: String,
    val is_today: Boolean
)

data class WeeklyBudgetResponse(
    val has_budget: Boolean,
    val budget_id: String,
    val total_budget: Int,
    val start_date: String,
    val end_date: String,
    val days_passed: Int,
    val days_remaining: Int,
    val current_spending: Int,
    val remaining: Int,
    val usage_percent: Float,
    val daily_avg_spent: Int,
    val recommended_daily_budget: Int,
    val status: String
)

data class SetWeeklyBudgetRequest(
    val amount: Int
)

data class SetWeeklyBudgetResponse(
    val message: String,
    val budget_id: String
)

