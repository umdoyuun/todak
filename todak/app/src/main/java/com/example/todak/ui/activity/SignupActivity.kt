package com.example.todak.ui.activity

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.ViewModelProvider
import com.example.todak.data.model.SignupRequest
import com.example.todak.data.model.NetworkResult
import com.example.todak.ui.viewModel.AuthViewModel
import com.example.todak.R
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 회원가입 화면을 처리하는 액티비티
 */
class SignupActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel
    private val calendar = Calendar.getInstance()
    private val TAG = "SignupActivity"

    // 뷰 변수들
    private lateinit var etName: EditText
    private lateinit var rgGender: RadioGroup
    private lateinit var rbMale: RadioButton
    private lateinit var rbFemale: RadioButton
    private lateinit var etEmail: EditText
    private lateinit var btnVerifyEmail: Button
    private lateinit var etPassword: EditText
    private lateinit var etPasswordConfirm: EditText
    private lateinit var etPhone: EditText
    private lateinit var etAddress: EditText
    private lateinit var etBirthdate: EditText
    private lateinit var btnSignup: Button
    private lateinit var tvSignupTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // 뒤로가기 아이콘 클릭 리스너 설정
        findViewById<ImageView>(R.id.back_icon).setOnClickListener {
            onBackPressed()
        }

        // 뷰 초기화
        initializeViews()

        // ViewModel 초기화
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        setupListeners()
        observeViewModel()
    }

    private fun initializeViews() {
        etName = findViewById(R.id.etName)
        rgGender = findViewById(R.id.rgGender)
        rbMale = findViewById(R.id.rbMale)
        rbFemale = findViewById(R.id.rbFemale)
        etEmail = findViewById(R.id.etEmail)
        btnVerifyEmail = findViewById(R.id.btnVerifyEmail)
        etPassword = findViewById(R.id.etPassword)
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)
        etBirthdate = findViewById(R.id.etBirthdate)
        btnSignup = findViewById(R.id.btnSignup)
        tvSignupTitle = findViewById(R.id.tvSignupTitle)
    }

    private fun setupListeners() {
        // 회원가입 버튼 클릭 리스너
        btnSignup.setOnClickListener {
            if (validateInputs()) {
                submitSignup()
            }
        }

        // 이메일 인증 버튼
        btnVerifyEmail.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                etEmail.error = "이메일을 입력해주세요"
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "유효한 이메일 주소를 입력해주세요"
            } else {
                etEmail.error = null
                // 이메일 인증 로직 구현
                Toast.makeText(this, "이메일 인증이 요청되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 성별 라디오 버튼 선택 시 색상 변경
        rgGender.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbMale -> {
                    rbMale.setBackgroundColor(resources.getColor(android.R.color.holo_blue_dark, null))
                    rbFemale.setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
                }
                R.id.rbFemale -> {
                    rbMale.setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
                    rbFemale.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
                }
            }
        }
    }

    private fun updateBirthDateField() {
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        etBirthdate.setText(dateFormat.format(calendar.time))
    }

    private fun observeViewModel() {
        // 회원가입 결과 관찰
        viewModel.signupResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    showLoading(true)
                }
                is NetworkResult.Success -> {
                    showLoading(false)
                    val signupResponse = result.data
                    Toast.makeText(this, signupResponse.message, Toast.LENGTH_SHORT).show()

                    // 성공 시 로그인 화면으로 이동
                    if (signupResponse.status == "success") {  // Boolean에서 String으로 변경
                        navigateToLoginActivity()
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
                            Toast.makeText(this, "회원가입 실패: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        // JSON 파싱 실패 시 기본 에러 메시지 표시
                        Toast.makeText(this, "회원가입 실패: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            btnSignup.isEnabled = false
            btnSignup.text = "처리 중..."
        } else {
            btnSignup.isEnabled = true
            btnSignup.text = "회원가입"
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // 이름 검증 (필수)
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            etName.error = "이름을 입력해주세요"
            etName.requestFocus()
            isValid = false
        } else {
            etName.error = null
        }

        // 이메일 검증 (필수)
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) {
            etEmail.error = "이메일을 입력해주세요"
            etEmail.requestFocus()
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "유효한 이메일 주소를 입력해주세요"
            etEmail.requestFocus()
            isValid = false
        } else {
            etEmail.error = null
        }

        // 비밀번호 검증 (필수)
        val password = etPassword.text.toString()
        if (password.isEmpty()) {
            etPassword.error = "비밀번호를 입력해주세요"
            etPassword.requestFocus()
            isValid = false
        } else if (password.length < 6) {
            etPassword.error = "비밀번호는 최소 6자 이상이어야 합니다"
            etPassword.requestFocus()
            isValid = false
        } else {
            etPassword.error = null
        }

        // 비밀번호 확인 검증 (필수)
        val passwordConfirm = etPasswordConfirm.text.toString()
        if (passwordConfirm.isEmpty()) {
            etPasswordConfirm.error = "비밀번호 확인을 입력해주세요"
            etPasswordConfirm.requestFocus()
            isValid = false
        } else if (password != passwordConfirm) {
            etPasswordConfirm.error = "비밀번호가 일치하지 않습니다"
            etPasswordConfirm.requestFocus()
            isValid = false
        } else {
            etPasswordConfirm.error = null
        }

        // 전화번호 검증 (선택)
        val phone = etPhone.text.toString().trim()
        if (phone.isNotEmpty()) {
            // 전화번호 형식 검증 (입력된 경우에만)
            val phonePattern = Regex("^01([0|1|6|7|8|9])-?([0-9]{3,4})-?([0-9]{4})$")
            if (!phonePattern.matches(phone)) {
                etPhone.error = "유효한 전화번호 형식이 아닙니다"
                etPhone.requestFocus()
                isValid = false
            } else {
                etPhone.error = null
            }
            if (phone.length != 11) {
                etPhone.error = "유효한 전화번호 형식이 아닙니다"
                etPhone.requestFocus()
                isValid = false
            }
        } else {
            etPhone.error = null  // 비어 있어도 오류 없음
        }

        // 주소 검증 (선택)
        val address = etAddress.text.toString().trim()
        // 주소는 형식 검증 없이 비어 있어도 오류 없음
        etAddress.error = null

        // 생년월일 검증 (선택)
        val birthdate = etBirthdate.text.toString().trim()
        if (birthdate.isNotEmpty()) {
            // 생년월일 형식 검증 (입력된 경우에만)
            if (!validateBirthdate()) {
                etBirthdate.requestFocus()
                isValid = false
            }
            else {
                etBirthdate.error = null
            }
        } else {
            etBirthdate.error = null  // 비어 있어도 오류 없음
        }

        return isValid
    }

    private fun validateBirthdate(showToast: Boolean = true): Boolean {
        val birthdate = etBirthdate.text.toString().trim()

        // 정규식으로 YYYY.MM.DD 형식 검증
        val regex = Regex("^\\d{4}\\.(0[1-9]|1[0-2])\\.(0[1-9]|[12][0-9]|3[01])$")

        if (birthdate.isEmpty()) {
            if (showToast) {
                Toast.makeText(this, "생년월일을 입력해주세요", Toast.LENGTH_SHORT).show()
            }
            etBirthdate.error = "생년월일을 입력해주세요"
            return false
        } else if (!regex.matches(birthdate)) {
            if (showToast) {
                Toast.makeText(this, "생년월일 형식이 올바르지 않습니다. YYYY.MM.DD 형식으로 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
            etBirthdate.error = "예: 2000.01.01"
            return false
        } else {
            // 추가 검증: 실제 존재하는 날짜인지 확인
            try {
                val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                sdf.isLenient = false // 엄격한 날짜 검증
                sdf.parse(birthdate)

                // 미래 날짜 검증
                val inputDate = sdf.parse(birthdate)
                val currentDate = Calendar.getInstance().time
                if (inputDate != null && inputDate.after(currentDate)) {
                    if (showToast) {
                        Toast.makeText(this, "미래 날짜는 입력할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                    etBirthdate.error = "미래 날짜는 입력할 수 없습니다"
                    return false
                }

                etBirthdate.error = null
                return true
            } catch (e: ParseException) {
                if (showToast) {
                    Toast.makeText(this, "존재하지 않는 날짜입니다.", Toast.LENGTH_SHORT).show()
                }
                etBirthdate.error = "존재하지 않는 날짜입니다"
                return false
            }
        }
    }

    private fun submitSignup() {
        val name = etName.text.toString().trim()
        val gender = if (rbMale.isChecked) "남" else "여"  // API에 맞게 "남"/"여"로 설정
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        // 선택 필드들은 비어있을 경우 null로 전달
        val phone = etPhone.text.toString().trim().ifEmpty { null }
        val address = etAddress.text.toString().trim().ifEmpty { null }

        // 생년월일 형식 그대로 유지 (서버에서 처리)
        val birthdate = etBirthdate.text.toString().trim().ifEmpty { null }

        // SignupRequest 객체 생성
        val signupRequest = SignupRequest(
            name = name,
            email = email,
            password = password,
            gender = gender,
            phone = phone,
            address = address,
            birthdate = birthdate
        )

        // 회원가입 요청 전송
        viewModel.signup(signupRequest)
    }

    private fun navigateToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        navigateToLoginActivity()
    }

    companion object {
        private const val TAG = "SignupActivity"
    }
}