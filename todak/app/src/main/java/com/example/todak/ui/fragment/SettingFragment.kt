package com.example.todak.ui.fragment

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.todak.R
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.repository.ApiRepository
import com.example.todak.data.repository.ChatRepository
import com.example.todak.ui.activity.LoginActivity
import com.example.todak.ui.activity.MainActivity
import com.example.todak.util.SessionManager
import kotlinx.coroutines.launch

class SettingFragment : Fragment() {

    private lateinit var tokenDialog: Dialog
    private val apiRepository = ApiRepository()
    private val TAG = "SettingFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        (activity as? MainActivity)?.setToolbarTitle("설정")

        // 음성 호출 스위치 초기 상태 설정 및 리스너
        setupVoiceRecognitionSwitch(view)

        // 인증 토큰 버튼 클릭 리스너 설정
        val tokenButton = view.findViewById<Button>(R.id.token)
        tokenButton.setOnClickListener {
            showTokenDialog()
        }

        // 프로필 버튼 클릭 리스너 설정
        val profileButton = view.findViewById<Button>(R.id.profile)
        profileButton.setOnClickListener {
            val profileFragment = ProfileFragment.newInstance()
            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_container, profileFragment)
                .addToBackStack(null)
                .commit()
        }

        // 로그아웃 버튼 클릭 리스너 설정
        val logoutButton = view.findViewById<Button>(R.id.logout)
        logoutButton.setOnClickListener {
            val chatRepository = ChatRepository()
            chatRepository.clearMessages(requireContext())
            SessionManager.init(requireContext())  // 반드시 먼저 초기화!
            SessionManager.clearSession()          // 세션 초기화
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        return view
    }

    private fun setupVoiceRecognitionSwitch(view: View) {
        val switchVoiceRecognition = view.findViewById<Switch>(R.id.switch_voice_recognition)

        // 현재 음성 호출 서비스 상태 가져오기
        val isVoiceServiceActive = (activity as? MainActivity)?.isVoiceServiceActive() ?: false
        switchVoiceRecognition.isChecked = isVoiceServiceActive

        // 스위치 클릭 리스너
        switchVoiceRecognition.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 음성 호출 서비스 활성화
                (activity as? MainActivity)?.startVoiceRecognition()
                Toast.makeText(context, "음성 호출 기능이 활성화되었습니다", Toast.LENGTH_SHORT).show()
            } else {
                // 음성 호출 서비스 비활성화
                (activity as? MainActivity)?.stopVoiceRecognition()
                Toast.makeText(context, "음성 호출 기능이 비활성화되었습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTokenDialog() {
        // 다이얼로그 초기화
        tokenDialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setContentView(R.layout.modal_token)

            // 다이얼로그 배경을 투명하게 설정
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // 다이얼로그 크기 및 위치 설정
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
        }

        // 닫기 버튼 클릭 리스너 설정
        val closeButton = tokenDialog.findViewById<ImageButton>(R.id.ib_close)
        closeButton.setOnClickListener {
            tokenDialog.dismiss()
        }

        // 토큰 값을 표시할 TextView
        val tokenValueTextView = tokenDialog.findViewById<TextView>(R.id.tv_token_value)
        tokenValueTextView.text = "토큰을 가져오는 중..."

        // 복사하기 버튼 클릭 리스너 설정
        val copyButton = tokenDialog.findViewById<Button>(R.id.btn_copy_token)
        copyButton.isEnabled = false  // 토큰이 로딩되기 전에는 비활성화

        copyButton.setOnClickListener {
            val token = tokenValueTextView.text.toString()
            if (token.isNotEmpty() && token != "토큰을 가져오는 중..." && token != "토큰 발급에 실패했습니다.") {
                copyToClipboard(token)
                Toast.makeText(requireContext(), "토큰이 복사되었습니다", Toast.LENGTH_SHORT).show()
            }
        }

        // 다이얼로그 표시
        tokenDialog.show()

        // 코루틴을 사용하여 API 호출
        lifecycleScope.launch {
            val result = apiRepository.getRelationToken()

            when (result) {
                is NetworkResult.Success -> {
                    tokenValueTextView.text = result.data
                    copyButton.isEnabled = true
                }
                is NetworkResult.Error -> {
                    tokenValueTextView.text = "토큰 발급에 실패했습니다."
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
                is NetworkResult.Loading -> {
                    // 이미 로딩 메시지가 표시되어 있으므로 별도 처리 불필요
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("토큰", text)
        clipboardManager.setPrimaryClip(clipData)
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때 음성 호출 스위치 상태 갱신
        val switchVoiceRecognition = view?.findViewById<Switch>(R.id.switch_voice_recognition)
        if (switchVoiceRecognition != null) {
            val isVoiceServiceActive = (activity as? MainActivity)?.isVoiceServiceActive() ?: false
            switchVoiceRecognition.isChecked = isVoiceServiceActive
        }
    }
}