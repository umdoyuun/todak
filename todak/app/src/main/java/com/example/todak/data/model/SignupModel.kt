package com.example.todak.data.model

data class SignupRequest(
    val name: String,
    val email: String,
    val password: String,
    val type: String = "bid",
    val phone: String? = null,
    val address: String? = null,
    val birthdate: String? = null,  // birthDate로 서버에 보낼 때 변환
    val gender: String? = null      // male/female로 서버에 보낼 때 변환
)

data class SignupResponse(
    val status: String,
    val message: String,
)