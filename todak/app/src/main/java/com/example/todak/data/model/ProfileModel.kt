package com.example.todak.data.model

// 사용자 프로필 모델
data class UserProfile(
    val id: String,
    val type: String,
    val name: String,
    val email: String,
    val phone: String?,
    val addr: String?,
    val birth: String?,
    val gender: String?,
    val imageUrl: String?,
    val needs: String?
)

// 센터 모델
data class Center(
    val centerId: String,
    val centerName: String,
    val address: String?,
    val phone: String?,
    val description: String?,
    val status: String,
    val startDate: String,
    val endDate: String?
)