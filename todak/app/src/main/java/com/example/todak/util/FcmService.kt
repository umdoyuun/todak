package com.example.todak.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.todak.R
import com.example.todak.data.repository.FcmApiRepository
import com.example.todak.ui.activity.MainActivity
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FcmService : FirebaseMessagingService() {
    private val TAG = "FCM_Service"
    private val fcmRepository = FcmApiRepository()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "새 FCM 토큰 발급: $token")

        // 서버에 토큰 등록
        registerTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "메시지 수신: ${remoteMessage.data}")

        // 항상 데이터 메시지 처리하기
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val title = data["title"] ?: remoteMessage.notification?.title ?: "알림"
            val body = data["body"] ?: remoteMessage.notification?.body ?: ""
            val scheduleId = data["schedule_id"] ?: ""
            val notificationType = data["notification_type"] ?: ""
            val deepLink = data["deep_link"] ?: ""

            // 알림 표시 - 백그라운드와 포그라운드 모두 동일하게 처리
            showNotification(title, body, scheduleId, notificationType, deepLink)

            // 워치로 알림 전송
            sendNotificationToWatch(title, body, scheduleId, notificationType)
        }
        // remoteMessage.notification이 있고 data가 없는 경우도 처리
        else if (remoteMessage.notification != null) {
            val title = remoteMessage.notification?.title ?: "알림"
            val body = remoteMessage.notification?.body ?: ""

            // 알림 표시 (기본 값으로)
            showNotification(title, body, "", "", "")
        }
    }

    // 워치로 전송
    private fun sendNotificationToWatch(title: String, body: String, scheduleId: String, notificationType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataClient = Wearable.getDataClient(this@FcmService)

                val request = PutDataMapRequest.create(WearableConstants.FCM_NOTIFICATION_PATH).run {
                    dataMap.putString("title", title)
                    dataMap.putString("body", body)
                    dataMap.putString("schedule_id", scheduleId)
                    dataMap.putString("notification_type", notificationType)
                    dataMap.putLong(WearableConstants.KEY_TIMESTAMP, System.currentTimeMillis())
                    asPutDataRequest()
                }

                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "워치에 알림 전송 결과: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "워치에 알림 전송 실패", e)
            }
        }
    }

    // 서버에 토큰 등록
    private fun registerTokenToServer(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = fcmRepository.registerDeviceToken(token)
                if (result is com.example.todak.data.model.NetworkResult.Success) {
                    Log.d(TAG, "FCM 토큰 서버 등록 성공")
                } else if (result is com.example.todak.data.model.NetworkResult.Error) {
                    Log.e(TAG, "FCM 토큰 서버 등록 실패: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM 토큰 서버 등록 예외", e)
            }
        }
    }

    private fun showNotification(
        title: String,
        body: String,
        scheduleId: String,
        notificationType: String,
        deepLink: String
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 알림 채널 설정 (Android 8.0 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "schedule_notifications",
                "일정 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "일정 관련 알림을 위한 채널입니다."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 로그 추가
        Log.d(TAG, "알림 표시 준비: title=$title, scheduleId=$scheduleId, type=$notificationType, deepLink=$deepLink")

        // 결과 인텐트 생성
        val resultIntent = if (deepLink.isNotEmpty()) {
            // 딥링크가 있는 경우
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(deepLink)
                setPackage(packageName)
                // 중요: 액션 관련 파라미터 제거, 그냥 상세 페이지로만 이동하도록 수정
                // deepLink에서 action 파라미터 제거
                if (data.toString().contains("&action=")) {
                    val uriString = data.toString().replace(Regex("&action=[^&]*"), "")
                    data = Uri.parse(uriString)
                }
            }
        } else {
            // 일반 인텐트 생성 - 액션 없이 상세 페이지로만 이동
            Intent(this, MainActivity::class.java).apply {
                action = "SCHEDULE_NOTIFICATION"
                putExtra("schedule_id", scheduleId)
                // 액션 파라미터 제거
                // putExtra("action", getActionFromType(notificationType))
                putExtra("show_schedule_fragment", true)
                putExtra("show_schedule_detail", true)
            }
        }

        // PendingIntent 생성 - 백스택 포함
        val resultPendingIntent: PendingIntent = TaskStackBuilder.create(this).run {
            // MainActivity를 스택 맨 아래에 추가
            addParentStack(MainActivity::class.java)
            // 결과 인텐트 추가
            addNextIntent(resultIntent)
            // PendingIntent 생성
            getPendingIntent(
                scheduleId.hashCode(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // 알림 빌더
        val notificationBuilder = NotificationCompat.Builder(this, "schedule_notifications")
            .setSmallIcon(R.drawable.turtle_hi)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(resultPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 확장 스타일 추가 (긴 텍스트를 위해)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        // 액션 추가 (해당하는 경우)
        when (notificationType) {
            "start" -> {
                val actionIntent = createActionIntent("start", scheduleId, title, body)
                notificationBuilder.addAction(
                    R.drawable.turtle_hi,
                    "시작하기",
                    actionIntent
                )
            }
            "end" -> {
                val actionIntent = createActionIntent("complete", scheduleId, title, body)
                notificationBuilder.addAction(
                    R.drawable.turtle_hi,
                    "완료하기",
                    actionIntent
                )
            }
        }

        // 알림 표시
        val notificationId = if (scheduleId.isNotEmpty()) {
            scheduleId.hashCode()
        } else {
            UUID.randomUUID().hashCode()
        }

        notificationManager.notify(notificationId, notificationBuilder.build())

        // 로그 추가
        Log.d(TAG, "알림 표시 완료: id=$notificationId, title=$title, deepLink=$deepLink")
    }

    // 액션 인텐트 생성 함수
    private fun createActionIntent(action: String, scheduleId: String, title: String, body: String): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = "ACTION_${action.uppercase()}"
            putExtra("schedule_id", scheduleId)
            putExtra("notification_title", title)
            putExtra("notification_body", body)
        }

        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun getActionFromType(notificationType: String): String? {
        return when (notificationType) {
            "start" -> "start"
            "end" -> "complete"
            else -> null
        }
    }
}