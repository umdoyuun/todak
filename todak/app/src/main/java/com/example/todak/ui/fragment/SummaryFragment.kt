package com.example.todak.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.CenterReportDetailResponse
import com.example.todak.data.model.EmotionStats
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.Summary
import com.example.todak.data.repository.CenterReportsRepository
import com.example.todak.ui.activity.MainActivity
import com.example.todak.ui.adapter.SummaryItemAdapter
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SummaryFragment : Fragment() {

    private val centerReportsRepository = CenterReportsRepository()
    private val TAG = "SummaryFragment"

    // UI 요소
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var tvReportDate: TextView
    private lateinit var tvEmotionalInsights: TextView
    private lateinit var tvClosingMessage: TextView
    private lateinit var layoutTipsContainer: LinearLayout
    private lateinit var layoutExpandedEmotions: LinearLayout

    // 통계 프로그레스 바
    private lateinit var progressRoutine: CircularProgressIndicator
    private lateinit var progressSchedule: CircularProgressIndicator
    private lateinit var progressMission: CircularProgressIndicator
    private lateinit var tvRoutineCount: TextView
    private lateinit var tvScheduleCount: TextView
    private lateinit var tvMissionCount: TextView

    // 펼치기/접기 버튼
    private lateinit var btnExpandAchievements: ImageButton
    private lateinit var btnExpandEmotions: ImageButton

    // RecyclerViews
    private lateinit var rvAchievements: RecyclerView
    private lateinit var layoutEmotionIcons: LinearLayout


    // 데이터
    private var reportDetail: CenterReportDetailResponse? = null
    private var tomorrowTips: List<String> = emptyList()

    private val achievementAdapter = SummaryItemAdapter(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 타이틀 설정
        (activity as? MainActivity)?.setToolbarTitle("내 활동 보고서")

        // UI 요소 초기화
        initViews(view)

        // 클릭 리스너 설정
        setupClickListeners()

        // RecyclerView 설정
        setupRecyclerViews()

        // API에서 데이터 로드
        loadReports()
    }

    private fun initViews(view: View) {
        progressBar = view.findViewById(R.id.progress_bar)
        errorText = view.findViewById(R.id.tv_error)
        tvGreeting = view.findViewById(R.id.tv_greeting)
        tvReportDate = view.findViewById(R.id.tv_report_date)
        tvEmotionalInsights = view.findViewById(R.id.tv_emotional_insights)
        tvClosingMessage = view.findViewById(R.id.tv_closing_message)
        layoutTipsContainer = view.findViewById(R.id.layout_tips_container)
        layoutExpandedEmotions = view.findViewById(R.id.layout_expanded_emotions)


        // 통계 프로그레스 바
        progressRoutine = view.findViewById(R.id.progress_routine)
        progressSchedule = view.findViewById(R.id.progress_schedule)
        progressMission = view.findViewById(R.id.progress_mission)
        tvRoutineCount = view.findViewById(R.id.tv_routine_count)
        tvScheduleCount = view.findViewById(R.id.tv_schedule_count)
        tvMissionCount = view.findViewById(R.id.tv_mission_count)

        // 펼치기/접기 버튼
        btnExpandAchievements = view.findViewById(R.id.btn_expand_achievements)
        btnExpandEmotions = view.findViewById(R.id.btn_expand_emotions)

        // RecyclerViews
        rvAchievements = view.findViewById(R.id.rv_achievements)
        layoutEmotionIcons = view.findViewById(R.id.layout_emotion_icons)

    }

    private fun setupClickListeners() {
        // 성취 목록 펼치기/접기
        btnExpandAchievements.setOnClickListener {
            val isVisible = rvAchievements.visibility == View.VISIBLE
            rvAchievements.visibility = if (isVisible) View.GONE else View.VISIBLE
            btnExpandAchievements.setImageResource(
                if (isVisible) R.drawable.icon_expand else R.drawable.icon_collapse
            )
        }

        // 감정 인사이트 펼치기/접기
        btnExpandEmotions.setOnClickListener {
            val isVisible = layoutExpandedEmotions.visibility == View.VISIBLE
            layoutExpandedEmotions.visibility = if (isVisible) View.GONE else View.VISIBLE
            btnExpandEmotions.setImageResource(
                if (isVisible) R.drawable.icon_expand else R.drawable.icon_collapse
            )
        }
    }

    private fun setupRecyclerViews() {
        // 성취 항목 목록
        rvAchievements.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = achievementAdapter
        }
    }

    private fun loadReports() {
        showLoading()
        lifecycleScope.launch {
            when (val result = centerReportsRepository.getCenterReports()) {
                is NetworkResult.Success -> {
                    val reports = result.data.reports
                    if (reports.isNotEmpty()) {
                        // 첫 번째 보고서의 ID로 상세 정보 가져오기
                        val firstReport = reports[0]
                        loadReportDetail(firstReport.report_id)
                    } else {
                        showError("보고서 목록이 비어 있습니다.")
                    }
                }
                is NetworkResult.Error -> {
                    showError("보고서 목록 가져오기 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    // 로딩 중
                }
            }
        }
    }

    private fun loadReportDetail(reportId: String) {
        lifecycleScope.launch {
            when (val result = centerReportsRepository.getCenterReportDetail(reportId)) {
                is NetworkResult.Success -> {
                    hideLoading()
                    reportDetail = result.data
                    updateUI(result.data)
                }
                is NetworkResult.Error -> {
                    showError("보고서 상세 가져오기 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    // 로딩 중
                }
            }
        }
    }

    private fun updateUI(reportDetail: CenterReportDetailResponse) {
        // 날짜 포맷팅
        val startDate = parseApiDate(reportDetail.start_date)
        tvReportDate.text = formatDate(startDate)

        // 요약 정보 설정
        val summary = reportDetail.summary

        // 인사말 설정
        tvGreeting.text = summary.greeting

        // 핵심 지표 설정 (이제 statistics를 직접 전달)
        setupActivityStats(reportDetail)

        // 성취 목록 설정
        setupAchievements(summary)

        // 감정 인사이트 설정
        setupEmotionInsights(summary, reportDetail.statistics.emotion_stats)

        // 팁 설정
        setupTips(summary)

        // 마무리 메시지
        setupClosingMessage(summary)
    }

    private fun setupActivityStats(reportDetail: CenterReportDetailResponse) {
        val statistics = reportDetail.statistics

        // 루틴 통계
        val routineStats = statistics.routine_stats
        val completedRoutineSteps = routineStats.step_statuses.completed
        val totalRoutineSteps = routineStats.total_logs

        progressRoutine.max = totalRoutineSteps
        progressRoutine.progress = completedRoutineSteps
        tvRoutineCount.text = "$completedRoutineSteps/$totalRoutineSteps"

        // 일정 통계
        val scheduleStats = statistics.schedule_stats
        val completedSchedules = if (scheduleStats.daily_completion_rates.isNotEmpty()) {
            scheduleStats.daily_completion_rates[0].completed
        } else 0
        val totalSchedules = if (scheduleStats.daily_completion_rates.isNotEmpty()) {
            scheduleStats.daily_completion_rates[0].total
        } else 0

        progressSchedule.max = totalSchedules
        progressSchedule.progress = completedSchedules
        tvScheduleCount.text = "$completedSchedules/$totalSchedules"

        // 미션 통계
        val missionStats = statistics.mission_stats
        val completedMissions = missionStats.statuses.completed
        val totalMissions = missionStats.total_missions

        progressMission.max = totalMissions
        progressMission.progress = completedMissions
        tvMissionCount.text = "$completedMissions/$totalMissions"
    }

    private fun setupAchievements(summary: Summary) {
        // 성취 요약 텍스트
        val achievementSummary = summary.achievements.joinToString(", ") {
            it.replace("완료", "").trim()
        }

        // 성취 목록 어댑터 업데이트
        achievementAdapter.updateItems(summary.achievements)
    }

    private fun setupEmotionInsights(summary: Summary, emotionStats: EmotionStats) {
        // 감정 인사이트 전체 내용
        tvEmotionalInsights.text = summary.emotional_insights
        val emotionCounts = emotionStats.emotion_counts

        // 감정 아이콘 표시
        setupEmotionIcons(emotionCounts)
    }

    private fun setupEmotionIcons(emotionCounts: Map<String, Int>) {
        // 레이아웃 초기화
        layoutEmotionIcons.removeAllViews()

        // 감정 아이콘 추가
        emotionCounts.forEach { (emotion, count) ->
            when (emotion.lowercase()) {
                "happy" -> addEmotionIcon(R.drawable.emotion_happy, "기쁨 ($count)")
                "neutral" -> addEmotionIcon(R.drawable.emotion_neutral, "중립 ($count)")
                "sad" -> addEmotionIcon(R.drawable.emotion_sad, "슬픔 ($count)")
                "angry" -> addEmotionIcon(R.drawable.emotion_angry, "분노 ($count)")
                "anxious" -> addEmotionIcon(R.drawable.emotion_anxious, "불안 ($count)")
                else -> addEmotionIcon(R.drawable.emotion_neutral, "$emotion ($count)")
            }
        }

        // 감정 기록이 없는 경우
        if (emotionCounts.isEmpty()) {
            addEmotionIcon(R.drawable.emotion_neutral, "감정 기록 없음")
        }
    }

    private fun addEmotionIcon(iconResId: Int, emotionName: String) {
        val context = requireContext()

        // 감정 아이콘 컨테이너
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = android.view.Gravity.CENTER

        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.marginEnd = 24
        container.layoutParams = layoutParams

        // 아이콘 이미지
        val iconImage = ImageView(context)
        iconImage.setImageResource(iconResId)
        val iconSize = resources.getDimensionPixelSize(R.dimen.emotion_icon_size)
        val iconParams = LinearLayout.LayoutParams(iconSize, iconSize)
        iconParams.gravity = Gravity.CENTER_HORIZONTAL  // 수평 중앙 정렬
        iconImage.layoutParams = iconParams

        // 감정 이름 텍스트
        val nameText = TextView(context)
        nameText.text = emotionName
        nameText.textSize = 12f
        nameText.setTextColor(ContextCompat.getColor(context, R.color.gray))
        nameText.gravity = Gravity.CENTER  // 텍스트 내용 중앙 정렬

        // TextView의 레이아웃 파라미터 설정
        val textParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        textParams.gravity = Gravity.CENTER_HORIZONTAL  // 수평 중앙 정렬
        nameText.layoutParams = textParams

        // 컨테이너에 추가
        container.addView(iconImage)
        container.addView(nameText)

        // 레이아웃에 추가
        layoutEmotionIcons.addView(container)
    }

    // setupTips 함수 수정
    private fun setupTips(summary: Summary) {
        // 팁 컨테이너 초기화
        layoutTipsContainer.removeAllViews()

        if (summary.tomorrow_tips.isNotEmpty()) {
            // 각 팁을 리스트 형태로 추가
            for (tip in summary.tomorrow_tips) {
                addTipItem(tip)
            }
        } else {
            // 팁이 없는 경우
            val emptyTipView = layoutInflater.inflate(
                R.layout.item_tip,
                layoutTipsContainer,
                false
            )
            val tvTipText = emptyTipView.findViewById<TextView>(R.id.tv_tip_text)
            tvTipText.text = "팁이 없습니다."
            layoutTipsContainer.addView(emptyTipView)
        }
    }

    // 개별 팁 아이템 추가 함수
    private fun addTipItem(tipText: String) {
        val tipView = layoutInflater.inflate(
            R.layout.item_tip,
            layoutTipsContainer,
            false
        )

        val tvTipText = tipView.findViewById<TextView>(R.id.tv_tip_text)
        tvTipText.text = tipText

        layoutTipsContainer.addView(tipView)
    }

    private fun setupClosingMessage(summary: Summary) {
        // 마무리 메시지에서 첫 두 문장만 표시 (너무 길지 않게)
        val closingMessage = summary.closing_message.split("!")
            .take(2)
            .joinToString("!") + "!"

        tvClosingMessage.text = closingMessage
    }

    // 텍스트에서 숫자 정보 추출 (예: "총 15개의 루틴 중 3개를 완료" -> total: 15, completed: 3)
    private fun extractStatsFromSummary(summaryText: String): Stats {
        val total = extractNumber(summaryText, "총 (\\d+)개") ?:
        extractNumber(summaryText, "(\\d+)개.*중") ?:
        0

        val completed = extractNumber(summaryText, "(\\d+)개를 완료") ?:
        extractNumber(summaryText, "성공적으로 (\\d+)개") ?:
        0

        return Stats(total, completed)
    }

    // 텍스트에서 감정 요약 추출
    private fun extractEmotionSummary(insightsText: String): String {
        // 감정 요약 부분 추출 (첫 문장 또는 '느꼈네요' 포함 문장)
        val sentences = insightsText.split(". ", ".\n", "! ", "!\n")

        for (sentence in sentences) {
            if (sentence.contains("감정을 느꼈") ||
                sentence.contains("기쁨") ||
                sentence.contains("중립") ||
                sentence.contains("감정 기록")) {
                return sentence.trim() + "."
            }
        }

        // 찾지 못한 경우 첫 문장 반환
        return sentences.firstOrNull()?.trim() + "." ?: "감정 데이터가 없습니다."
    }

    // 정규식으로 숫자 추출
    private fun extractNumber(text: String, pattern: String): Int? {
        val regex = Regex(pattern)
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseApiDate(dateString: String): Date {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return try {
            format.parse(dateString.substringBefore(".")) ?: Date()
        } catch (e: Exception) {
            Log.e(TAG, "날짜 파싱 오류: $dateString", e)
            Date()
        }
    }

    private fun formatDate(date: Date): String {
        val format = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
        return format.format(date)
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        errorText.visibility = View.GONE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    private fun showError(message: String) {
        errorText.visibility = View.VISIBLE
        errorText.text = message
        hideLoading()
    }

    // 통계 데이터 클래스
    data class Stats(val total: Int, val completed: Int)
}