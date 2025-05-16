package com.example.todak.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.todak.data.model.MissionItem
import com.example.todak.ui.fragment.MissionListFragment

class MissionSubPagerAdapter(
    fragment: Fragment,
    private val subTabs: List<String>,
    private val missionDataMap: Map<String, List<MissionItem>>,
    private val onMissionItemClick: (MissionItem) -> Unit
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = subTabs.size

    override fun createFragment(position: Int): Fragment {
        val subTab = subTabs[position]
        val missionData = missionDataMap[subTab] ?: emptyList()

        return MissionListFragment.newInstance(missionData, onMissionItemClick)
    }
}