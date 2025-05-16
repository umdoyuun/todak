package com.example.todak.ui.modal

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.Chronometer
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.todak.R
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.repository.EmotionApiRepository
import com.example.todak.ui.activity.MainActivity
import com.example.todak.util.WavAudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmotionCheckModal(
    context: Context,
    private val eventContext: String,
    private val message: String
) : Dialog(context) {

    private val TAG = "EmotionCheckModal"
    private val repository = EmotionApiRepository()

    // 선택된 감정
    private var selectedEmotion: String? = null

    // 녹음 관련 변수
    private var audioRecorder: WavAudioRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null

    // WakeWord 서비스 관련 변수
    private var wasWakeWordActive = false
    private var mainActivity: MainActivity? = null

    // UI 관련 변수
    private lateinit var initialLayout: ConstraintLayout
    private lateinit var detailLayout: ConstraintLayout
    private lateinit var messageText: TextView
    private lateinit var emotionIcons: List<ImageView>
    private lateinit var noteEditText: EditText
    private lateinit var recordButton: ImageButton
    private lateinit var recordTimer: Chronometer
    private lateinit var submitButton: Button

    init {
        // MainActivity 참조 저장
        if (context is MainActivity) {
            mainActivity = context
        }

        // 오디오 레코더 초기화
        audioRecorder = WavAudioRecorder(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.modal_emotion_check)

        // 다이얼로그 설정
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        // UI 요소 초기화
        initializeViews()
        setupListeners()

        // 메시지 설정
        messageText.text = message
    }

    private fun initializeViews() {
        // 레이아웃
        initialLayout = findViewById(R.id.emotion_initial_layout)
        detailLayout = findViewById(R.id.emotion_detail_layout)

        // 공통 요소
        messageText = findViewById(R.id.tv_emotion_message)

        // 감정 아이콘
        emotionIcons = listOf(
            findViewById(R.id.iv_emotion_anxious),
            findViewById(R.id.iv_emotion_sad),
            findViewById(R.id.iv_emotion_neutral),
            findViewById(R.id.iv_emotion_happy),
            findViewById(R.id.iv_emotion_angry)
        )

        // 상세 화면 요소
        noteEditText = findViewById(R.id.et_emotion_note)
        recordButton = findViewById(R.id.btn_record_audio)
        recordTimer = findViewById(R.id.chronometer_recording)
        submitButton = findViewById(R.id.btn_submit_emotion)

        // 초기 화면만 표시
        initialLayout.visibility = View.VISIBLE
        detailLayout.visibility = View.GONE
    }

    private fun setupListeners() {
        // 닫기 버튼
        findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            cleanupAndDismiss()
        }

        // 감정 아이콘에 클릭 리스너 설정
        for (i in emotionIcons.indices) {
            emotionIcons[i].setOnClickListener {
                onEmotionSelected(i)
            }
        }

        // 녹음 버튼 리스너
        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        // 제출 버튼 리스너
        submitButton.setOnClickListener {
            submitEmotion()
        }
    }

    private fun onEmotionSelected(index: Int) {
        // 선택된 감정 저장
        selectedEmotion = when (index) {
            0 -> "anxious"
            1 -> "sad"
            2 -> "neutral"
            3 -> "happy"
            4 -> "angry"
            else -> null
        }

        // 선택된 감정 강조 표시
        for (i in emotionIcons.indices) {
            emotionIcons[i].alpha = if (i == index) 1.0f else 0.5f
        }

        // 애니메이션으로 상세 화면으로 전환
        val fadeOut = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
        val fadeIn = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)

        initialLayout.startAnimation(fadeOut)
        initialLayout.visibility = View.GONE

        detailLayout.visibility = View.VISIBLE
        detailLayout.startAnimation(fadeIn)
    }

    private fun startRecording() {
        // 현재 WakeWord 서비스 상태 저장 및 중지
        wasWakeWordActive = mainActivity?.isVoiceServiceActive() ?: false
        if (wasWakeWordActive) {
            Log.d(TAG, "녹음 시작 전 WakeWord 서비스 일시 중지")
            mainActivity?.stopVoiceRecognition()
        }

        try {
            // 녹음 파일 경로 생성
            val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioDir = File(context.getExternalFilesDir(null), "audio")
            if (!audioDir.exists()) audioDir.mkdirs()

            val filePath = "${audioDir.absolutePath}/emotion_recording_$dateString.wav"

            // WavAudioRecorder를 사용하여 녹음 시작
            val success = audioRecorder?.startRecording(filePath) ?: false

            if (success) {
                // 녹음 파일 경로 저장
                audioFile = File(filePath)

                // UI 업데이트
                isRecording = true
                recordButton.setImageResource(R.drawable.icon_mic_off) // 스톱 아이콘으로 변경
                recordTimer.base = SystemClock.elapsedRealtime()
                recordTimer.start()
                recordTimer.visibility = View.VISIBLE

                Toast.makeText(context, "녹음을 시작합니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "녹음을 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
                restoreWakeWordService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "녹음 시작 실패", e)
            Toast.makeText(context, "녹음을 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()

            // 오류 발생 시 WakeWord 서비스 상태 복원
            restoreWakeWordService()
        }
    }

    private fun stopRecording() {
        try {
            // WavAudioRecorder를 사용하여 녹음 중지
            val recordedFile = audioRecorder?.stopRecording()

            // 녹음 파일 확인
            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                audioFile = recordedFile
                Log.d(TAG, "녹음 완료: ${recordedFile.absolutePath} (${recordedFile.length()} bytes)")
                Toast.makeText(context, "녹음이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "녹음 파일이 없거나 비어 있습니다")
                Toast.makeText(context, "녹음 파일이 생성되지 않았습니다.", Toast.LENGTH_SHORT).show()
                audioFile = null
            }

            // UI 업데이트
            isRecording = false
            recordButton.setImageResource(R.drawable.icon_mic) // 녹음 아이콘으로 변경
            recordTimer.stop()
            recordTimer.visibility = View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "녹음 중지 실패", e)
            Toast.makeText(context, "녹음 중지 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            audioFile = null
        } finally {
            // 녹음 완료 또는 오류 발생 시 WakeWord 서비스 상태 복원
            restoreWakeWordService()
        }
    }

    // WakeWord 서비스 상태 복원
    private fun restoreWakeWordService() {
        if (wasWakeWordActive) {
            Log.d(TAG, "WakeWord 서비스 상태 복원")
            mainActivity?.startVoiceRecognition()
            wasWakeWordActive = false
        }
    }

    private fun submitEmotion() {
        val emotion = selectedEmotion ?: run {
            Toast.makeText(context, "감정을 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val note = noteEditText.text.toString().trim()

        // 로딩 표시
        submitButton.isEnabled = false
        submitButton.text = "제출 중..."

        // API 호출
        CoroutineScope(Dispatchers.IO).launch {
            val result = repository.submitEmotion(
                emotion = emotion,
                context = eventContext,
                note = if (note.isNotEmpty()) note else null,
                audioFile = audioFile
            )

            withContext(Dispatchers.Main) {
                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(context, "감정이 성공적으로 등록되었습니다.", Toast.LENGTH_SHORT).show()
                        cleanupAndDismiss()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(context, "감정 등록 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                        submitButton.isEnabled = true
                        submitButton.text = "제출"
                    }
                    is NetworkResult.Loading -> {
                        // 로딩 중 표시
                    }
                }
            }
        }
    }

    private fun cleanupAndDismiss() {
        // 녹음 중이면 중지
        if (isRecording) {
            stopRecording()
        } else {
            // 녹음 중이 아닌 경우에도 WakeWord 서비스 상태 복원
            restoreWakeWordService()
        }

        // 오디오 레코더 리소스 해제
        audioRecorder?.release()
        audioRecorder = null

        // 다이얼로그 닫기
        dismiss()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanupAndDismiss()
    }

    companion object {
        fun show(context: Context, eventContext: String, message: String): EmotionCheckModal {
            val modal = EmotionCheckModal(context, eventContext, message)
            modal.show()
            return modal
        }
    }
}