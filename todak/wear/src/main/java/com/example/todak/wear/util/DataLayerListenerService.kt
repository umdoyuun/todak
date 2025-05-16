package com.example.todak.wear.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.todak.wear.R
import com.example.todak.wear.presentation.MainActivity
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import java.util.UUID

class DataLayerListenerService : WearableListenerService() {
    private val TAG = "DataLayerListener"

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val path = uri.path ?: ""

                when (path) {
                    WearableConstants.FCM_NOTIFICATION_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val title = dataMap.getString("title", "알림")
                        val body = dataMap.getString("body", "")
                        val scheduleId = dataMap.getString("schedule_id", "")
                        val notificationType = dataMap.getString("notification_type", "")

                        Log.d(TAG, "FCM 알림 수신: $title, $body")
                        showNotification(title, body, scheduleId, notificationType)
                    }
                    WearableConstants.SCHEDULE_NOTIFICATION_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val scheduleName = dataMap.getString(WearableConstants.KEY_SCHEDULE_NAME, "")
                        val scheduleTime = dataMap.getString(WearableConstants.KEY_SCHEDULE_TIME, "")

                        Log.d(TAG, "일정 알림 수신: $scheduleName, $scheduleTime")
                        showScheduleNotification(scheduleName, scheduleTime)
                    }

                    "/manual_step_change" -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val shopId = dataMap.getString("shop_id", "")
                        val menuName = dataMap.getString("menu_name", "")
                        val currentStep = dataMap.getInt("current_step", 1)

                        Log.d(TAG, "워치에서 단계 변경 수신: 가게=$shopId, 메뉴=$menuName, 단계=$currentStep")

                        // 앱이 실행 중이면 브로드캐스트로 처리, 아니면 새 액티비티 시작
                        val intent = Intent("com.example.todak.MANUAL_STEP_CHANGED").apply {
                            putExtra("shop_id", shopId)
                            putExtra("menu_name", menuName)
                            putExtra("current_step", currentStep)
                        }
                        sendBroadcast(intent)
                    }
                }
            }
        }
        super.onDataChanged(dataEvents)
    }

    private fun showNotification(title: String, body: String, scheduleId: String, notificationType: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 알림 채널 설정 (Android 8.0 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fcm_notifications",
                "FCM 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "FCM에서 전달된 알림을 위한 채널입니다."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 메인 인텐트 생성 (알림 클릭시 실행)
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("schedule_id", scheduleId)
            putExtra("notification_type", notificationType)
        }

        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 자세히 보기 액션 인텐트 생성
        val detailIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("schedule_id", scheduleId)
            putExtra("notification_type", notificationType)
            putExtra("action", "view_detail")
        }

        val detailPendingIntent = PendingIntent.getActivity(
            this,
            1,
            detailIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 요약된 내용 생성
        val shortBody = if (body.length > 50) {
            "${body.substring(0, 47)}..."
        } else {
            body
        }

        // 알림 빌더
        val notificationBuilder = NotificationCompat.Builder(this, "fcm_notifications")
            .setSmallIcon(R.drawable.icon_notification) // 워치용 알림 아이콘 필요
            .setContentTitle(title)
            .setContentText(shortBody) // 짧은 내용만 표시
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // BigTextStyle 사용하여 확장 시 전체 내용 표시
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // 자세히 보기 액션 추가
            .addAction(android.R.drawable.ic_menu_view, "자세히 보기", detailPendingIntent)

        // 알림 표시
        val notificationId = if (scheduleId.isNotEmpty()) {
            scheduleId.hashCode()
        } else {
            UUID.randomUUID().hashCode()
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun showScheduleNotification(scheduleName: String, scheduleTime: String) {
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

        // 메인 인텐트 생성
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("schedule_name", scheduleName)
            putExtra("schedule_time", scheduleTime)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 알림 빌더
        val notificationBuilder = NotificationCompat.Builder(this, "schedule_notifications")
            .setSmallIcon(R.drawable.icon_notification) // 워치용 알림 아이콘 필요
            .setContentTitle("일정 알림")
            .setContentText("$scheduleName: $scheduleTime")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)

        // 알림 표시
        val notificationId = (scheduleName + scheduleTime).hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}