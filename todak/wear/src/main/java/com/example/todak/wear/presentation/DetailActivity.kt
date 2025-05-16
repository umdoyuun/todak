package com.example.todak.wear.presentation

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.todak.wear.R

class DetailActivity : Activity() {

    private lateinit var tvDetailTitle: TextView
    private lateinit var tvDetailContent: TextView
    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        tvDetailTitle = findViewById(R.id.tvDetailTitle)
        tvDetailContent = findViewById(R.id.tvDetailContent)
        btnBack = findViewById(R.id.btnBack)

        val scheduleId = intent.getStringExtra("schedule_id") ?: ""
        val notificationType = intent.getStringExtra("notification_type") ?: ""

        displayDetailInfo(scheduleId, notificationType)

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun displayDetailInfo(scheduleId: String, notificationType: String) {
        // 여기에서는 예시로 하드코딩된 데이터를 표시합니다.
        // 실제로는 scheduleId를 사용하여 모바일 앱이나 서버에서 상세 정보를 가져와야 합니다.
        tvDetailTitle.text = "일정 상세 정보"

        // 상세 내용 설정 (예시)
        val detailContent = """
            일정 ID: $scheduleId
            
            타입: $notificationType
            
            상세 내용:
            프로젝트 회의에 대한 자세한 정보입니다. 회의에서는 현재 진행 중인 기능 개발 상황을 논의하고 
            향후 일정 계획을 수립할 예정입니다.
            
            참석자: 팀원 전체
            
            준비물: 지난 회의 자료, 일정표
        """.trimIndent()

        tvDetailContent.text = detailContent
    }
}