package com.example.todak.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.todak.R
import com.example.todak.data.repository.FcmApiRepository
import com.example.todak.ui.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationActionReceiver : BroadcastReceiver() {

    private val TAG = "NotificationReceiver"
    private val fcmRepository = FcmApiRepository()

    companion object {
        const val ACTION_REFRESH_SCHEDULE = "com.example.todak.ACTION_REFRESH_SCHEDULE"
        const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val scheduleId = intent.getStringExtra("schedule_id") ?: return
        val notificationTitle = intent.getStringExtra("notification_title") ?: "일정 알림"
        val notificationBody = intent.getStringExtra("notification_body") ?: ""

        Log.d(TAG, "알림 액션 수신: $action, 일정 ID: $scheduleId")

        // 액션 버튼 클릭 시 알림 업데이트 (액션 버튼 제거)
        updateNotificationWithoutActions(context, scheduleId, notificationTitle, notificationBody)

        when (action) {
            "ACTION_START" -> {
                performScheduleAction(context, scheduleId, "start")
            }
            "ACTION_COMPLETE" -> {
                performScheduleAction(context, scheduleId, "complete")
            }
            "ACTION_POSTPONE" -> {
                performScheduleAction(context, scheduleId, "postpone")
            }
        }
    }

    // 액션 버튼이 제거된 알림으로 업데이트하는 메서드
    private fun updateNotificationWithoutActions(
        context: Context,
        scheduleId: String,
        title: String,
        body: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 알림 ID 계산 (원래 알림과 동일해야 함)
        val notificationId = scheduleId.hashCode()

        // 원래 알림과 동일한 PendingIntent 생성
        val pendingIntent = createContentPendingIntent(context, scheduleId)

        // 액션 버튼이 없는 새 알림 생성
        val updatedNotification = NotificationCompat.Builder(context, "schedule_notifications")
            .setSmallIcon(R.drawable.turtle_hi)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 액션 버튼은 추가하지 않음
            .build()

        // 알림 업데이트
        notificationManager.notify(notificationId, updatedNotification)
    }

    // 알림 내용 클릭 시 사용할 PendingIntent 생성
    private fun createContentPendingIntent(context: Context, scheduleId: String): android.app.PendingIntent {
        val intent = Intent(context, com.example.todak.ui.activity.MainActivity::class.java).apply {
            action = "SCHEDULE_NOTIFICATION"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("schedule_id", scheduleId)
            putExtra("show_schedule_fragment", true)
            putExtra("show_schedule_detail", true)
        }

        return androidx.core.app.TaskStackBuilder.create(context).run {
            addParentStack(com.example.todak.ui.activity.MainActivity::class.java)
            addNextIntent(intent)
            getPendingIntent(
                scheduleId.hashCode(),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun performScheduleAction(context: Context, scheduleId: String, actionType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = fcmRepository.performScheduleAction(scheduleId, actionType)

                withContext(Dispatchers.Main) {
                    if (result is com.example.todak.data.model.NetworkResult.Success) {
                        Toast.makeText(
                            context,
                            "일정이 ${getActionName(actionType)}되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()

                        // 새로고침 브로드캐스트 전송
                        Log.d(TAG, "일정 새로고침 브로드캐스트 전송")
                        val refreshIntent = Intent(ACTION_REFRESH_SCHEDULE)
                        context.sendBroadcast(refreshIntent)

                        // 앱이 실행 중이 아닐 경우를 위한 인텐트
                        val launchIntent = Intent(context, com.example.todak.ui.activity.MainActivity::class.java).apply {
                            action = ACTION_REFRESH_SCHEDULE
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context.startActivity(launchIntent)

                        // 감정 등록 알림 표시 (complete 액션인 경우에만)
                        if (actionType == "complete") {
                            showEmotionRegistrationNotification(context, scheduleId)
                        }

                    } else if (result is com.example.todak.data.model.NetworkResult.Error) {
                        Toast.makeText(
                            context,
                            "오류: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "일정 액션 처리 오류", e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "네트워크 오류가 발생했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun getActionName(actionType: String): String {
        return when (actionType) {
            "start" -> "시작"
            "complete" -> "완료"
            "postpone" -> "연기"
            else -> actionType
        }
    }

    // 감정 등록 알림 표시 메서드
    private fun showEmotionRegistrationNotification(context: Context, scheduleId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 알림 채널 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "emotion_registration",
                "감정 등록",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 클릭 시 감정 등록 화면으로 이동하는 인텐트
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "EMOTION_REGISTRATION"
            putExtra("schedule_id", scheduleId)
            putExtra("context", "after_schedule:${scheduleId}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            scheduleId.hashCode() + 100,  // 기존 알림과 ID가 겹치지 않도록 오프셋 추가
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "emotion_registration")
            .setSmallIcon(R.drawable.turtle_hi)
            .setContentTitle("감정을 등록하세요")
            .setContentText("일정을 완료하셨네요! 지금의 감정을 기록해 보세요.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(scheduleId.hashCode() + 100, notification)
    }
}