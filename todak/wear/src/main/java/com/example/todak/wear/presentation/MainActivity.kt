package com.example.todak.wear.presentation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.todak.wear.R
import com.example.todak.wear.util.WearableConstants
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

class MainActivity : Activity(), DataClient.OnDataChangedListener {

    private lateinit var tvTitle: TextView
    private lateinit var emotionLayout: LinearLayout
    private lateinit var btnHappy: ImageButton
    private lateinit var btnSad: ImageButton
    private lateinit var btnNeutral: ImageButton
    private lateinit var btnSurprised: ImageButton
    private lateinit var btnExcited: ImageButton
    private lateinit var btnViewManual: Button
    private lateinit var notificationLayout: ConstraintLayout
    private lateinit var tvScheduleName: TextView
    private lateinit var tvScheduleTime: TextView
    private lateinit var btnOk: Button
    private lateinit var fcmNotificationLayout: ConstraintLayout
    private lateinit var tvFcmTitle: TextView
    private lateinit var tvFcmBody: TextView
    private lateinit var btnFcmClose: Button
    private lateinit var btnFcmDetail: Button
    private var currentScheduleId: String = ""
    private var currentNotificationType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        emotionLayout = findViewById(R.id.emotionLayout)
        btnHappy = findViewById(R.id.emotionHappy)
        btnSad = findViewById(R.id.emotionSad)
        btnNeutral = findViewById(R.id.emotionNeutral)
        btnSurprised = findViewById(R.id.emotionSurprised)
        btnExcited = findViewById(R.id.emotionExcited)
        btnViewManual = findViewById(R.id.btnViewManual)
        notificationLayout = findViewById(R.id.notificationLayout)
        tvScheduleName = findViewById(R.id.tvScheduleName)
        tvScheduleTime = findViewById(R.id.tvScheduleTime)
        btnOk = findViewById(R.id.btnOk)
        fcmNotificationLayout = findViewById(R.id.fcmNotificationLayout)
        tvFcmTitle = findViewById(R.id.tvFcmTitle)
        tvFcmBody = findViewById(R.id.tvFcmBody)
        btnFcmClose = findViewById(R.id.btnFcmClose)
        btnFcmDetail = findViewById(R.id.btnFcmDetail)

    }

    private fun setupListeners() {
        btnHappy.setOnClickListener { registerEmotion("happy") }
        btnSad.setOnClickListener { registerEmotion("sad") }
        btnNeutral.setOnClickListener { registerEmotion("neutral") }
        btnSurprised.setOnClickListener { registerEmotion("surprised") }
        btnExcited.setOnClickListener { registerEmotion("excited") }

        btnViewManual.setOnClickListener {
            // 매뉴얼 화면으로 이동하는 코드 (필요시 구현)
            Toast.makeText(this, "매뉴얼 기능은 아직 구현되지 않았습니다", Toast.LENGTH_SHORT).show()
        }

        btnOk.setOnClickListener {
            notificationLayout.visibility = View.GONE
        }

        btnFcmClose.setOnClickListener {
            fcmNotificationLayout.visibility = View.GONE
        }

        btnFcmDetail.setOnClickListener {
            showDetailView(currentScheduleId, currentNotificationType)
        }
    }

    private fun showFcmNotification(title: String, body: String, scheduleId: String, notificationType: String) {
        // 현재 일정 정보 저장
        currentScheduleId = scheduleId
        currentNotificationType = notificationType

        // UI 업데이트
        tvFcmTitle.text = title
        tvFcmBody.text = body

        // 알림 레이아웃 표시
        fcmNotificationLayout.visibility = View.VISIBLE
    }

    // 상세 화면 표시 함수 추가
    private fun showDetailView(scheduleId: String, notificationType: String) {
        // 상세 화면으로 이동하는 인텐트 생성
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra("schedule_id", scheduleId)
            putExtra("notification_type", notificationType)
        }
        startActivity(intent)
    }

    private fun registerEmotion(emotionType: String) {
        try {
            // 백그라운드 스레드에서 작업 실행
            Thread {
                try {
                    // 스마트폰으로 감정 데이터 전송
                    val nodesResult = Tasks.await(Wearable.getNodeClient(this).connectedNodes)

                    if (nodesResult.isNotEmpty()) {
                        // 메시지 전송
                        nodesResult.forEach { node ->
                            val result = Tasks.await(
                                Wearable.getMessageClient(this)
                                    .sendMessage(
                                        node.id,
                                        WearableConstants.EMOTION_REGISTRATION_PATH,
                                        emotionType.toByteArray()
                                    )
                            )

                            // UI 스레드에서 토스트 표시
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    "감정이 등록되었습니다: $emotionType",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "연결된 기기가 없습니다",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "감정 등록 실패", e)
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "감정 등록 실패: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "스레드 시작 실패", e)
            Toast.makeText(this, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                val path = item.uri.path

                when (path) {
                    WearableConstants.SCHEDULE_NOTIFICATION_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val scheduleName = dataMap.getString(WearableConstants.KEY_SCHEDULE_NAME, "")
                        val scheduleTime = dataMap.getString(WearableConstants.KEY_SCHEDULE_TIME, "")

                        // UI 스레드에서 알림 표시
                        runOnUiThread {
                            showScheduleNotification(scheduleName, scheduleTime)
                        }
                    }

                    WearableConstants.FCM_NOTIFICATION_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val title = dataMap.getString("title", "알림")
                        val body = dataMap.getString("body", "")
                        val scheduleId = dataMap.getString("schedule_id", "")
                        val notificationType = dataMap.getString("notification_type", "")

                        // UI 스레드에서 FCM 알림 표시
                        runOnUiThread {
                            showFcmNotification(title, body, scheduleId, notificationType)
                        }
                    }

                    WearableConstants.MANUAL_STEP_UPDATE_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val shopId = dataMap.getString("shop_id", "")
                        val menuName = dataMap.getString("menu_name", "")
                        val currentStep = dataMap.getInt("current_step", 1)
                        val totalSteps = dataMap.getInt("total_steps", 1)
                        val imageUrl = dataMap.getString("image_url", "")
                        val description = dataMap.getString("step_description", "")

                        // UI 스레드에서 매뉴얼 화면 시작
                        runOnUiThread {
                            startManualStep(shopId, menuName, currentStep, totalSteps, imageUrl, description)
                        }
                    }
                }
            }
        }
    }

    private fun startManualStep(shopId: String, menuName: String, currentStep: Int, totalSteps: Int, imageUrl: String, description: String) {
        val intent = Intent(this, ManualStepActivity::class.java).apply {
            putExtra("shop_id", shopId)
            putExtra("menu_name", menuName)
            putExtra("current_step", currentStep)
            putExtra("total_steps", totalSteps)
            putExtra("image_url", imageUrl)
            putExtra("step_description", description)
        }
        startActivity(intent)
    }


    private fun showScheduleNotification(name: String, time: String) {
        tvScheduleName.text = name
        tvScheduleTime.text = time
        notificationLayout.visibility = View.VISIBLE
    }

    companion object {
        private const val TAG = "WearMainActivity"
    }
}