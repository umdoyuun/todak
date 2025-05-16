package com.example.todak.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.todak.R
import com.example.todak.data.model.MissionItem
import com.example.todak.ui.adapter.MissionAdapter
import com.example.todak.ui.adapter.MissionSubPagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.io.Serializable

class MissionSubFragment : Fragment() {

    private lateinit var subTabLayout: TabLayout
    private lateinit var subViewPager: ViewPager2

    private var mainTab: String = ""
    private var subTabs: List<String> = emptyList()
    private var missionDataMap: Map<String, List<MissionItem>> = emptyMap()
    private var onMissionItemClick: ((MissionItem) -> Unit)? = null

    companion object {
        private const val ARG_MAIN_TAB = "main_tab"
        private const val ARG_SUB_TABS = "sub_tabs"
        private const val ARG_MISSION_DATA = "mission_data"

        fun newInstance(
            mainTab: String,
            subTabs: List<String>,
            missionDataMap: Map<String, List<MissionItem>>,
            onMissionItemClick: (MissionItem) -> Unit
        ): MissionSubFragment {
            val fragment = MissionSubFragment()

            // 데이터 설정
            fragment.mainTab = mainTab
            fragment.subTabs = subTabs
            fragment.missionDataMap = missionDataMap
            fragment.onMissionItemClick = onMissionItemClick

            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mission_sub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뷰 초기화
        subTabLayout = view.findViewById(R.id.sub_tab_layout)
        subViewPager = view.findViewById(R.id.sub_view_pager)

        setupSubViewPager()
    }

    private fun setupSubViewPager() {
        // 서브 ViewPager 어댑터 설정
        val pagerAdapter = MissionSubPagerAdapter(
            this,
            subTabs,
            missionDataMap
        ) { missionItem ->
            onMissionItemClick?.invoke(missionItem)
        }

        subViewPager.adapter = pagerAdapter

        // TabLayout과 ViewPager 연결
        TabLayoutMediator(subTabLayout, subViewPager) { tab, position ->
            tab.text = subTabs[position]
        }.attach()

        // 서브 탭이 1개이면(참가 가능 탭의 경우) 탭 레이아웃 숨기기
        if (subTabs.size <= 1) {
            subTabLayout.visibility = View.GONE
        } else {
            subTabLayout.visibility = View.VISIBLE
        }
    }
}