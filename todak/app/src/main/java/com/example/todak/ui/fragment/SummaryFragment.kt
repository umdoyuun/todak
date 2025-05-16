package com.example.todak.ui.fragment

import com.example.todak.data.model.Summary

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.repository.CenterReportsRepository
import com.example.todak.ui.activity.MainActivity
import com.example.todak.ui.adapter.SummaryItemAdapter
import kotlinx.coroutines.launch

class SummaryFragment : Fragment() {

    private lateinit var adapter: SummaryItemAdapter
    private val centerReportsRepository = CenterReportsRepository()
    private val TAG = "SummaryFragment"

    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 타이틀 설정 (필요한 경우)
        (activity as? MainActivity)?.setToolbarTitle("보고서")

        // 프로그레스바와 에러 텍스트 초기화
        progressBar = view.findViewById(R.id.progress_bar)
        errorText = view.findViewById(R.id.tv_error)

        // API에서 실제 데이터 로드
        loadReports()
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
                    // 이미 showLoading()을 호출했으므로 아무것도 하지 않음
                    // 또는 추가적인 로딩 상태 처리가 필요한 경우 여기에 작성
                }
            }
        }
    }

    private fun loadReportDetail(reportId: String) {
        lifecycleScope.launch {
            when (val result = centerReportsRepository.getCenterReportDetail(reportId)) {
                is NetworkResult.Success -> {
                    hideLoading()
                    val reportDetail = result.data

                    // 데이터를 UI에 적용 - summary 부분만 사용
                    val summary = reportDetail.summary
                    updateUI(summary)
                }
                is NetworkResult.Error -> {
                    showError("보고서 상세 가져오기 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    // 이미 showLoading()을 호출했으므로 아무것도 하지 않음
                    // 또는 추가적인 로딩 상태 처리가 필요한 경우 여기에 작성
                }
            }
        }
    }
    private fun updateUI(summary: Summary) {
        view?.apply {
            // TextView 설정
            findViewById<TextView>(R.id.tv_greeting).text = summary.greeting
            findViewById<TextView>(R.id.tv_daily_summary).text = summary.daily_summary
            findViewById<TextView>(R.id.tv_routine_highlights).text = summary.routine_highlights
            findViewById<TextView>(R.id.tv_schedule_highlights).text = summary.schedule_highlights
            findViewById<TextView>(R.id.tv_mission_highlights).text = summary.mission_highlights
            findViewById<TextView>(R.id.tv_emotional_insights).text = summary.emotional_insights
            findViewById<TextView>(R.id.tv_closing_message).text = summary.closing_message

            // RecyclerViews 설정
            setupRecyclerView(findViewById(R.id.rv_achievements), summary.achievements)
            setupRecyclerView(findViewById(R.id.rv_tomorrow_tips), summary.tomorrow_tips)

        }
    }

    private fun setupRecyclerView(recyclerView: RecyclerView, items: List<String>) {
        adapter = SummaryItemAdapter(items)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
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
}