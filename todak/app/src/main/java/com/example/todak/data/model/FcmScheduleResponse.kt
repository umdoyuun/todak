package com.example.todak.data.model

data class FcmScheduleResponse(
    val date: String,
    val schedules: List<FcmScheduleItem>
)

data class FcmScheduleItem(
    val _id: String,
    val title: String,
    val category: String?,
    val start_date: String,
    val end_date: String?,
    val start_time: String,
    val end_time: String,
    val repeat: FcmRepeatInfo?,
    val note: String?
)

data class FcmRepeatInfo(
    val type: String,
    val days: List<String>?
)