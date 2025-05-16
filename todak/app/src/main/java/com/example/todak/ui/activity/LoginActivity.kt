package com.example.todak.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.todak.ui.viewModel.AuthViewModel
import com.example.todak.R
import com.example.todak.data.model.NetworkResult
import com.example.todak.util.SessionManager
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private val viewModel: AuthViewModel by viewModels()
    private var passwordVisible = false
    private val TAG = "LoginActivity"

    // 뷰 변수들
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnTogglePassword: ImageButton
    private lateinit var tvSignup: TextView
    private lateinit var tvForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 뷰 초기화
        initializeViews()

        // SessionManager 초기화
        SessionManager.init(applicationContext)

        // 이미 로그인되어 있는 경우 메인 화면으로 이동
        if (SessionManager.isLoggedIn()) {
            navigateToMainActivity()
            return
        }

        setupListeners()
        observeViewModel()
    }

    private fun initializeViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        tvSignup = findViewById(R.id.tvSignup)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
    }

    private fun setupListeners() {
        // 로그인 버튼 클릭 리스너
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (validateInputs(email, password)) {
                viewModel.login(email, password)
            }
        }

        // 회원가입 화면으로 이동
        tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // 비밀번호 찾기
        tvForgotPassword.setOnClickListener {
            // 비밀번호 찾기 기능 구현 (미구현 상태)
            Toast.makeText(this, "비밀번호 찾기 기능은 아직 구현되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }

        // 비밀번호 표시/숨김 토글
        btnTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            togglePasswordVisibility()
        }
    }

    private fun togglePasswordVisibility() {
        if (passwordVisible) {
            // 비밀번호 표시
            etPassword.transformationMethod = null
            btnTogglePassword.setImageResource(R.drawable.icon_visibility_on)
        } else {
            // 비밀번호 숨김
            etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            btnTogglePassword.setImageResource(R.drawable.icon_visibility_off)
        }
        // 커서 위치 유지
        etPassword.setSelection(etPassword.text.length)
    }

    private fun observeViewModel() {
        viewModel.loginResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    showLoading(true)
                }

                is NetworkResult.Success -> {
                    showLoading(false)

                    // 로그인 성공 처리
                    val loginResponse = result.data
                    Log.d(TAG, "로그인 성공: ${loginResponse.message}")

                    if (loginResponse.status == "success") {
                        Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show()

                        SessionManager.saveAuthToken(
                            accessToken = loginResponse.accessToken ?: "",
                            refreshToken = loginResponse.refreshToken,
                            userId = loginResponse.id,
                            userName = loginResponse.name
                        )

                        navigateToMainActivity()
                    } else {
                        Toast.makeText(this, loginResponse.message, Toast.LENGTH_LONG).show()
                    }
                }

                is NetworkResult.Error -> {
                    showLoading(false)
                    try {
                        // JSON 응답에서 에러 메시지 추출 시도
                        val errorBody = result.errorBody
                        if (errorBody != null) {
                            val jsonObj = JSONObject(errorBody)
                            val message = jsonObj.optString("message", "알 수 없는 오류가 발생했습니다.")
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        } else {
                            // 기본 에러 메시지 표시
                            Toast.makeText(this, "로그인 실패: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        // JSON 파싱 실패 시 기본 에러 메시지 표시
                        Toast.makeText(this, "로그인 실패: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            btnLogin.isEnabled = false
            btnLogin.text = "로그인 중..."
        } else {
            btnLogin.isEnabled = true
            btnLogin.text = "Login"
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        // 이메일 검증
        if (email.isEmpty()) {
            etEmail.error = "이메일을 입력해주세요"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "유효한 이메일 주소를 입력해주세요"
            isValid = false
        } else {
            etEmail.error = null
        }

        // 비밀번호 검증
        if (password.isEmpty()) {
            etPassword.error = "비밀번호를 입력해주세요"
            isValid = false
        } else if (password.length < 2) {
            etPassword.error = "비밀번호는 최소 6자 이상이어야 합니다"
            isValid = false
        } else {
            etPassword.error = null
        }

        return isValid
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}