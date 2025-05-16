package com.example.todak.util

import ai.picovoice.porcupine.*
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.todak.BuildConfig
import com.example.todak.R
import com.example.todak.data.model.AudioResponse
import com.example.todak.data.model.ChatMessage
import com.example.todak.data.model.ChatResponse
import com.example.todak.data.repository.ApiRepository
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.repository.ChatRepository
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

class WakeWordService : Service(), PorcupineManagerCallback {
    private var porcupineManager: PorcupineManager? = null
    private var audioRecorder: WavAudioRecorder? = null
    private var isRecording = false
    private var isWaitingForSpeech = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // ApiRepository 사용
    private val apiRepository = ApiRepository()

    interface VoiceResponseListener {
        fun onVoiceResponseReceived(response: String)
    }

    private var listener: VoiceResponseListener? = null

    // 바인더 구현
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun setVoiceResponseListener(listener: VoiceResponseListener?) {
        this.listener = listener
    }

    override fun onCreate() {
        super.onCreate()

        // 오디오 레코더 초기화
        audioRecorder = WavAudioRecorder(applicationContext)

        // Porcupine 초기화
        initPorcupine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("호출어 감지 대기 중"))
        return START_STICKY
    }

    private fun initPorcupine() {
        try {
            val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY

            val modelPath = copyAssetToFile("porcupine_params_ko.pv")
            val keywordPath = copyAssetToFile("todak_ko_android_v3_0_0.ppn")
            Log.d("WakeWordService", "AccessKey: $accessKey")
            Log.d("WakeWordService", "Model path: $modelPath")

            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setModelPath(modelPath)
                .setKeywordPaths(arrayOf(keywordPath))
                .setSensitivities(floatArrayOf(0.7f))
                .build(applicationContext, this)

            // 호출어 감지 시작
            porcupineManager?.start()
            Log.d(TAG, "Porcupine 호출어 감지 시작됨 (한국어 모델)")

        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine 초기화 실패", e)
        }
    }

    private fun copyAssetToFile(assetName: String): String {
        val outputFile = File(filesDir, assetName)

        try {
            if (!outputFile.exists()) {
                assets.open(assetName).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Asset 파일 복사 성공: $assetName")
            } else {
                Log.d(TAG, "Asset 파일이 이미 존재함: $assetName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Asset 파일 복사 실패: $assetName", e)
        }

        return outputFile.absolutePath
    }

    // 호출어 감지 콜백
    override fun invoke(keywordIndex: Int) {
        Log.d(TAG, "호출어 '토닥' 감지됨! 인덱스: $keywordIndex")

        // 호출어 감지 이벤트를 브로드캐스트
        val intent = Intent(ACTION_WAKE_WORD_DETECTED)
        intent.putExtra(EXTRA_KEYWORD_INDEX, keywordIndex)
        sendBroadcast(intent)

        // 음성 녹음 시작
        startVoiceRecording()
    }

    private fun startVoiceRecording() {
        if (isRecording) return

        try {
            // 호출어 감지 일시 중지
            porcupineManager?.stop()

            // 알림 업데이트
            updateNotification("음성 듣는 중...")

            isRecording = true
            isWaitingForSpeech = true

            // 음성 녹음 시작
            val recordingFile = File(getExternalFilesDir(null), "recording_${Date().time}.wav")
            audioRecorder?.startRecording(recordingFile.absolutePath)

            // 침묵 감지 및 녹음 종료 처리
            startSilenceDetection()

        } catch (e: Exception) {
            Log.e(TAG, "음성 녹음 시작 실패", e)
            stopVoiceRecording()
        }
    }

    private fun startSilenceDetection() {
        serviceScope.launch {
            // 음성 시작 대기 (최대 5초)
            var speechStarted = false
            var silenceCounter = 0
            val silenceThreshold = 1000 // 침묵 판단 임계값 (조정 필요)
            val maxSilenceDuration = 150 // 약 1.5초 (10ms 간격으로 체크 시)

            // 사용자가 말하기 시작할 때까지 최대 5초 대기
            var waitCounter = 0
            while (isWaitingForSpeech && waitCounter < 500) { // 5초 (10ms * 500)
                delay(10)
                val amplitude = audioRecorder?.getMaxAmplitude() ?: 0

                if (amplitude > silenceThreshold) {
                    isWaitingForSpeech = false
                    speechStarted = true
                    updateNotification("음성 녹음 중...")
                    break
                }
                waitCounter++
            }

            // 말하기 시작하지 않았으면 녹음 중단
            if (!speechStarted) {
                Log.d(TAG, "음성이 감지되지 않았습니다")
                stopVoiceRecording()
                return@launch
            }

            // 사용자가 말하기 시작한 후 침묵 감지
            while (isRecording) {
                delay(10) // 10ms 간격으로 체크
                val amplitude = audioRecorder?.getMaxAmplitude() ?: 0

                if (amplitude < silenceThreshold) {
                    silenceCounter++
                    if (silenceCounter > maxSilenceDuration) {
                        Log.d(TAG, "침묵 감지, 녹음 종료")
                        stopVoiceRecording()
                        break
                    }
                } else {
                    silenceCounter = 0
                }
            }
        }
    }

    private fun stopVoiceRecording() {
        if (!isRecording) return

        try {
            isRecording = false
            isWaitingForSpeech = false

            // 녹음 파일 저장
            val recordingFile = audioRecorder?.stopRecording()

            updateNotification("음성 처리 중...")

            if (recordingFile != null && recordingFile.exists() && recordingFile.length() > 0) {
                // 서버에 녹음 파일 전송
                sendRecordingToServer(recordingFile)
            } else {
                Log.e(TAG, "녹음 파일이 없거나 비어 있습니다")
                resumeWakeWordDetection()
            }

        } catch (e: Exception) {
            Log.e(TAG, "음성 녹음 중지 실패", e)
            resumeWakeWordDetection()
        }
    }

    private fun sendRecordingToServer(recordingFile: File) {
        serviceScope.launch {
            try {
                updateNotification("서버에 전송 중...")

                // ApiRepository를 통해 오디오 파일 전송
                val result = apiRepository.uploadAudio(recordingFile)

                when (result) {
                    is NetworkResult.Success -> {
                        val response = result.data
                        // 음성 응답 처리
                        handleVoiceResponse(response)
                        Log.d(TAG, "서버 응답: $response")
                    }
                    is NetworkResult.Error -> {
                        Log.e(TAG, "서버 오류: ${result.message}")
                    }
                    is NetworkResult.Loading -> {
                        // 처리 중 상태, 여기서는 무시
                    }
                }

                // 임시 파일 삭제
                recordingFile.delete()

            } catch (e: Exception) {
                Log.e(TAG, "서버 전송 실패", e)
            } finally {
                resumeWakeWordDetection()
            }
        }
    }

    private val audioPlayerManager = AudioPlayerManager()
    private fun handleVoiceResponse(response: AudioResponse) {
        // 채팅 저장소에 대화 내용 저장
        saveVoiceResponseToChatRepository(response)

        // 기존 TTS 재생 코드
        audioPlayerManager.playTtsFromS3(
            s3Key = response.s3_key,
            onCompletion = {
                // 재생 완료 후 처리 (예: UI 업데이트)
            },
            onError = { errorMsg ->
                // 오류 처리
                // 예: _errorMessage.value = errorMsg
            }
        )
    }

    private fun saveVoiceResponseToChatRepository(response: AudioResponse) {
        try {
            // ChatRepository 인스턴스 생성
            val chatRepository = ChatRepository()

            // 사용자 메시지 생성 (음성 인식된 내용)
            val userMessage = ChatMessage(
                id = System.currentTimeMillis(),
                message = response.user_message,
                sender = ChatMessage.Sender.USER,  // 여기서 USER는 ChatMessage 내의 enum 값
                timestamp = System.currentTimeMillis()
            )

            // 봇 메시지 생성
            val botMessage = ChatMessage(
                id = System.currentTimeMillis() + 1,
                message = response.bot_message,
                sender = ChatMessage.Sender.TODAK,  // 여기서 TODAK은 ChatMessage 내의 enum 값
                timestamp = System.currentTimeMillis() + 100  // 약간의 시간차
            )

            // 채팅 저장소에 메시지 저장
            chatRepository.saveMessage(applicationContext, userMessage)
            chatRepository.saveMessage(applicationContext, botMessage)

            Log.d(TAG, "음성 대화가 채팅 저장소에 저장되었습니다: 사용자: ${response.user_message}, 봇: ${response.bot_message}")

            // 메인 액티비티가 있고 채팅 모달이 열려있다면 새로고침 알림 브로드캐스트
            val refreshIntent = Intent(ACTION_REFRESH_CHAT)
            refreshIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            refreshIntent.setPackage(packageName)
            Log.d(TAG, "채팅 새로고침 브로드캐스트 전송: ${ACTION_REFRESH_CHAT}, 패키지: $packageName")
            sendBroadcast(refreshIntent)
        } catch (e: Exception) {
            Log.e(TAG, "음성 대화 저장 실패", e)
        }
    }

    private fun resumeWakeWordDetection() {
        updateNotification("호출어 감지 대기 중")

        try {
            if (porcupineManager == null) {
                initPorcupine()
            } else {
                porcupineManager?.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "호출어 감지 재시작 실패", e)
        }
    }

    private fun createNotification(status: String): Notification {
        // 알림 채널 생성 (Android O 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "음성 비서",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // 알림 클릭 시 앱 열기
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("토닥 음성 비서")
            .setContentText(status)
            .setSmallIcon(R.drawable.icon_mic)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()

        audioRecorder?.release()
        audioRecorder = null

        porcupineManager?.let {
            try {
                it.stop()
                it.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Porcupine 종료 실패", e)
            }
        }
        porcupineManager = null

        super.onDestroy()
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_assistant_channel"

        const val ACTION_WAKE_WORD_DETECTED = "com.example.todak.WAKE_WORD_DETECTED"
        const val EXTRA_KEYWORD_INDEX = "keywordIndex"
        const val ACTION_REFRESH_CHAT = "com.example.todak.REFRESH_CHAT"
    }
}