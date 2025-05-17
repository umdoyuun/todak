package com.example.todak.ui.fragment

import MissionRepository
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.todak.R
import com.example.todak.data.model.MissionItem
import com.example.todak.data.model.NetworkResult
import com.example.todak.ui.activity.MainActivity
import com.example.todak.ui.adapter.MissionPagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class MissionFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var tvEmptyState: TextView

    private val missionRepository = MissionRepository()
    private val TAG = "MissionFragment"

    // 미션 데이터
    private var allMissions: List<MissionItem> = emptyList()
    private var participatingMissions: List<MissionItem> = emptyList()    // 진행 중 + 시작 전
    private var completedParticipationMissions: List<MissionItem> = emptyList() // 완료 + 포기
    private var availableMissions: List<MissionItem> = emptyList()

    // 참가 중인 미션의 세부 카테고리
    private var pendingMissions: List<MissionItem> = emptyList()
    private var inProgressMissions: List<MissionItem> = emptyList()
    private var completedMissions: List<MissionItem> = emptyList()
    private var failedMissions: List<MissionItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 타이틀 설정 (필요한 경우)
        (activity as? MainActivity)?.setToolbarTitle("미션 목록")

        // 뷰 초기화
        tabLayout = view.findViewById(R.id.tab_layout)
        viewPager = view.findViewById(R.id.view_pager)
        tvEmptyState = view.findViewById(R.id.tv_empty_state)

        // 미션 데이터 가져오기
        fetchMissions()
    }

    private fun setupViewPager() {
        // 미션 개수 계산
        val participatingCount = participatingMissions.size
        val completedParticipationCount = completedParticipationMissions.size
        val availableCount = availableMissions.size

        // 탭 목록 정의 (개수 포함)
        val mainTabs = listOf(
            "참가 중 ($participatingCount)",
            "참가 가능 ($availableCount)",
            "참가 완료 ($completedParticipationCount)"
        )

        // 참가 중 탭의 하위 카테고리 정의
        val subTabs = mapOf(
            "참가 중 ($participatingCount)" to listOf("전체", "시작 전", "진행 중"),
            "참가 완료 ($completedParticipationCount)" to listOf("전체", "완료", "포기"),
            "참가 가능 ($availableCount)" to listOf("전체")
        )

        // 미션 데이터 매핑
        val missionDataMap = mapOf(
            "참가 중 ($participatingCount)" to mapOf(
                "전체" to participatingMissions,
                "시작 전" to pendingMissions,
                "진행 중" to inProgressMissions
            ),
            "참가 완료 ($completedParticipationCount)" to mapOf(
                "전체" to completedParticipationMissions,
                "완료" to completedMissions,
                "포기" to failedMissions
            ),
            "참가 가능 ($availableCount)" to mapOf(
                "전체" to availableMissions
            )
        )

        // ViewPager 어댑터 설정
        val pagerAdapter = MissionPagerAdapter(
            this,
            mainTabs,
            subTabs,
            missionDataMap
        ) { missionItem ->
            navigateToMissionDetail(missionItem)
        }

        viewPager.adapter = pagerAdapter

        // TabLayout과 ViewPager 연결
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = mainTabs[position]
        }.attach()

        // 데이터가 있는지 확인하여 UI 업데이트
        updateEmptyState()

    }

    private fun updateEmptyState() {
        if (allMissions.isEmpty()) {
            tvEmptyState.text = "미션이 없습니다."
            tvEmptyState.visibility = View.VISIBLE
            viewPager.visibility = View.GONE
            tabLayout.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            viewPager.visibility = View.VISIBLE
            tabLayout.visibility = View.VISIBLE
        }
    }

    private fun fetchMissions() {
        tvEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = missionRepository.getAvailableMissions()) {
                is NetworkResult.Success -> {
                    // 전체 미션 목록 저장
                    allMissions = result.data

                    // 참가 상태에 따라 미션 분류
                    categorizeByParticipationStatus()

                    // 참가 중인 미션 상태별 세부 분류
                    categorizeParticipatingMissions()

                    // ViewPager 및 탭 설정
                    setupViewPager()

                    Log.d(TAG, "미션 목록 가져오기 성공: ${allMissions.size}개 항목")
                }
                is NetworkResult.Error -> {
                    // 오류 처리 코드
                    tvEmptyState.text = "미션을 불러오는 중 오류가 발생했습니다."
                    tvEmptyState.visibility = View.VISIBLE
                    viewPager.visibility = View.GONE
                    tabLayout.visibility = View.GONE
                }
                is NetworkResult.Loading -> {
                    // 로딩 처리 코드
                }
            }
        }
    }

    // 참가 상태에 따라 미션 분류
    private fun categorizeByParticipationStatus() {
        // 참가 중인 미션은 is_participated가 true이고 상태가 pending 또는 in_progress인 것만
        participatingMissions = allMissions.filter {
            it.is_participated &&
                    (it.participation_status == "pending" || it.participation_status == "in_progress")
        }

        // 참가 완료 미션은 is_participated가 true이고 상태가 completed 또는 failed인 것만
        completedParticipationMissions = allMissions.filter {
            it.is_participated &&
                    (it.participation_status == "completed" || it.participation_status == "failed")
        }

        // 참가 가능한 미션은 is_participated가 false인 것
        availableMissions = allMissions.filter { !it.is_participated }
    }

    // 참가 중인 미션 상태별 분류
    private fun categorizeParticipatingMissions() {
        pendingMissions = participatingMissions.filter { it.participation_status == "pending" }
        inProgressMissions = participatingMissions.filter { it.participation_status == "in_progress" }
        completedMissions = completedParticipationMissions.filter { it.participation_status == "completed" }
        failedMissions = completedParticipationMissions.filter { it.participation_status == "failed" }

        // 참가 중인 미션 전체 리스트도 상태에 따라 정렬
        participatingMissions = sortMissionsByParticipationStatus(participatingMissions)

        // 참가 완료 미션 전체 리스트도 상태에 따라 정렬
        completedParticipationMissions = sortMissionsByCompletionStatus(completedParticipationMissions)

    }

    // 참가 상태에 따라 미션 목록 정렬
    private fun sortMissionsByParticipationStatus(missions: List<MissionItem>): List<MissionItem> {
        // 참가 상태별 우선순위 정의
        val statusPriority = mapOf(
            "pending" to 0,      // 시작 전 (1순위)
            "in_progress" to 1,  // 진행 중 (2순위)
            null to 2            // 상태가 없는 경우 (3순위)
        )

        // 참가 상태에 따라 정렬
        return missions.sortedWith(compareBy { missionItem ->
            statusPriority[missionItem.participation_status] ?: 4
        })
    }

    // 완료 상태에 따라 미션 목록 정렬 (참가 완료)
    private fun sortMissionsByCompletionStatus(missions: List<MissionItem>): List<MissionItem> {
        // 완료 상태별 우선순위 정의
        val statusPriority = mapOf(
            "completed" to 0,    // 완료 (1순위)
            "failed" to 1,       // 포기 (2순위)
            null to 2            // 상태가 없는 경우 (3순위)
        )

        // 완료 상태에 따라 정렬
        return missions.sortedWith(compareBy { missionItem ->
            statusPriority[missionItem.participation_status] ?: 2
        })
    }

    private fun navigateToMissionDetail(missionItem: MissionItem) {
        try {
            val isParticipating = missionItem.is_participated
            val participationId = if (isParticipating) missionItem.participation_id else null

            val missionDetailFragment = MissionDetailFragment.newInstance(
                missionId = missionItem._id,
                isParticipating = isParticipating,
                participationId = participationId
            )

            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_container, missionDetailFragment)
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "MissionDetailFragment 이동 오류", e)
            Toast.makeText(context, "${missionItem.title} 미션 상세를 확인합니다", Toast.LENGTH_SHORT).show()
        }
    }

    // Fragment가 다시 보일 때 데이터를 새로고침
    override fun onResume() {
        super.onResume()
        fetchMissions()
    }
}