package com.example.todak.ui.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todak.data.model.LoginRequest
import com.example.todak.data.model.LoginResponse
import com.example.todak.data.model.SignupRequest
import com.example.todak.data.model.SignupResponse
import com.example.todak.data.repository.ApiRepository
import com.example.todak.data.model.NetworkResult
import com.example.todak.util.SessionManager
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val apiRepository = ApiRepository()

    // 회원가입 결과를 관찰할 LiveData
    private val _signupResult = MutableLiveData<NetworkResult<SignupResponse>>()
    val signupResult: LiveData<NetworkResult<SignupResponse>> = _signupResult

    // 로그인 결과를 관찰할 LiveData
    private val _loginResult = MutableLiveData<NetworkResult<LoginResponse>>()
    val loginResult: LiveData<NetworkResult<LoginResponse>> = _loginResult

    /**
     * 회원가입 요청을 처리합니다.
     */
    fun signup(
        name: String,
        email: String,
        password: String,
        type: String = "BID",
        phone: String = "",
        address: String = "",
        birthdate: String = "",
        gender: String = ""
    ) {
        viewModelScope.launch {
            _signupResult.value = NetworkResult.Loading

            try {
                // SignupRequest 객체 생성
                val request = SignupRequest(
                    name = name,
                    email = email,
                    password = password,
                    type = type,
                    gender = gender,
                    phone = phone,
                    address = address,
                    birthdate = birthdate
                )

                // API 호출 결과 처리
                val result = apiRepository.signup(request)
                _signupResult.value = result
            } catch (e: Exception) {
                _signupResult.value = NetworkResult.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
            }
        }
    }

    /**
     * SignupRequest 객체로 회원가입을 처리합니다.
     */
    fun signup(request: SignupRequest) {
        signup(
            name = request.name,
            email = request.email,
            password = request.password,
            type = request.type ?: "A",
            phone = request.phone ?: "",
            address = request.address ?: "",
            birthdate = request.birthdate ?: "",
            gender = request.gender ?: ""
        )
    }

    /**
     * 로그인 요청을 처리합니다.
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginResult.value = NetworkResult.Loading

            try {
                // LoginRequest 객체 생성
                val request = LoginRequest(email, password)

                // API 호출 결과 처리
                val result = apiRepository.login(request)
                _loginResult.value = result
            } catch (e: Exception) {
                _loginResult.value = NetworkResult.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
            }
        }
    }

    fun login(request: LoginRequest) {
        login(request.email, request.password)
    }

    fun logout() {
        // 세션 정보 삭제
        SessionManager.clearSession()
    }

    fun isLoggedIn(): Boolean {
        return SessionManager.isLoggedIn()
    }
}