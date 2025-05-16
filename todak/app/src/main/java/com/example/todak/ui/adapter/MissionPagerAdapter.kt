package com.example.todak.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.todak.data.model.MissionItem
import com.example.todak.ui.fragment.MissionSubFragment

class MissionPagerAdapter(
    fragment: Fragment,
    private val mainTabs: List<String>,
    private val subTabs: Map<String, List<String>>,
    private val missionDataMap: Map<String, Map<String, List<MissionItem>>>,
    private val onMissionItemClick: (MissionItem) -> Unit
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = mainTabs.size

    override fun createFragment(position: Int): Fragment {
        val mainTab = mainTabs[position]
        val subTabList = subTabs[mainTab] ?: listOf("전체")
        val missionDataForTab = missionDataMap[mainTab] ?: emptyMap()

        return MissionSubFragment.newInstance(
            mainTab = mainTab,
            subTabs = subTabList,
            missionDataMap = missionDataForTab,
            onMissionItemClick = onMissionItemClick
        )
    }
}