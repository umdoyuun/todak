package com.example.todak.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.MissionItem
import com.example.todak.ui.adapter.MissionAdapter

class MissionListFragment : Fragment() {

    private lateinit var rvMission: RecyclerView
    private lateinit var tvEmptyState: TextView

    private var missionItems: List<MissionItem> = emptyList()
    private var onMissionItemClick: ((MissionItem) -> Unit)? = null

    companion object {
        private const val ARG_MISSION_ITEMS = "mission_items"

        fun newInstance(
            missionItems: List<MissionItem>,
            onMissionItemClick: (MissionItem) -> Unit
        ): MissionListFragment {
            val fragment = MissionListFragment()

            // 데이터 설정
            fragment.missionItems = missionItems
            fragment.onMissionItemClick = onMissionItemClick

            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mission_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뷰 초기화
        rvMission = view.findViewById(R.id.rv_mission_list)
        tvEmptyState = view.findViewById(R.id.tv_empty_list)

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val missionAdapter = MissionAdapter()
        rvMission.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = missionAdapter
        }

        // 아이템 클릭 리스너 설정
        missionAdapter.setOnItemClickListener(object : MissionAdapter.OnItemClickListener {
            override fun onItemClick(missionItem: MissionItem) {
                onMissionItemClick?.invoke(missionItem)
            }
        })

        // 데이터 설정
        missionAdapter.submitList(missionItems)

        // 빈 상태 처리
        if (missionItems.isEmpty()) {
            rvMission.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
        } else {
            rvMission.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }
    }
}