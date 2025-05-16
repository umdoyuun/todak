package com.example.todak.util

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.todak.data.model.ChatMessage
import com.example.todak.data.repository.ApiRepository
import com.example.todak.data.repository.ChatRepository
import com.example.todak.util.SessionManager
import com.example.todak.util.WearableConstants
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class WearableService : Service(), MessageClient.OnMessageReceivedListener {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val apiRepository = ApiRepository()
    private val chatRepository = ChatRepository()

    override fun onCreate() {
        super.onCreate()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearableConstants.EMOTION_REGISTRATION_PATH -> {
                val emotionType = String(messageEvent.data)
                Log.d(TAG, "워치로부터 감정 등록 수신: $emotionType")

                // 감정 데이터 처리
                handleEmotionRegistration(emotionType)
            }
        }
    }

    private fun handleEmotionRegistration(emotionType: String) {
        scope.launch {
            try {
                // 감정 등록 메시지 생성
                val userMessage = "오늘의 감정: $emotionType"

                // 채팅 저장소에 사용자 메시지 저장
                val chatMessage = ChatMessage(
                    id = System.currentTimeMillis(),
                    message = userMessage,
                    sender = ChatMessage.Sender.USER,
                    timestamp = System.currentTimeMillis()
                )
                chatRepository.saveMessage(applicationContext, chatMessage)

                // API로 감정 메시지 전송
                val result = apiRepository.sendChatMessage(userMessage)

                withContext(Dispatchers.Main) {
                    // 결과 처리 및 UI 새로고침 브로드캐스트 전송
                    val refreshIntent = Intent("com.example.todak.REFRESH_CHAT")
                    sendBroadcast(refreshIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "감정 등록 처리 실패", e)
            }
        }
    }

    // 워치로 일정 알림 전송
    fun sendScheduleNotification(scheduleName: String, scheduleTime: String) {
        scope.launch {
            try {
                val dataClient = Wearable.getDataClient(this@WearableService)

                val request = PutDataMapRequest.create(WearableConstants.SCHEDULE_NOTIFICATION_PATH).run {
                    dataMap.putString(WearableConstants.KEY_SCHEDULE_NAME, scheduleName)
                    dataMap.putString(WearableConstants.KEY_SCHEDULE_TIME, scheduleTime)
                    dataMap.putLong(WearableConstants.KEY_TIMESTAMP, System.currentTimeMillis())
                    asPutDataRequest()
                }

                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "일정 알림 전송 결과: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "일정 알림 전송 실패", e)
            }
        }
    }

    companion object {
        private const val TAG = "WearableService"
    }
}