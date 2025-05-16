package com.example.todak.ui.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.ScheduleItem
import com.example.todak.data.model.ScheduleStatus

class ScheduleAdapter : ListAdapter<ScheduleItem, ScheduleAdapter.ScheduleViewHolder>(ScheduleDiffCallback()) {

    // 클릭 리스너 인터페이스 추가
    interface OnItemClickListener {
        fun onItemClick(scheduleItem: ScheduleItem)
    }

    private var itemClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val scheduleItem = getItem(position)
        holder.bind(scheduleItem)
    }

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle = itemView.findViewById<TextView>(R.id.tv_schedule_title)
        private val tvCategory = itemView.findViewById<TextView>(R.id.tv_category)
        private val tvTime = itemView.findViewById<TextView>(R.id.tv_schedule_time)
        private val tvStatus = itemView.findViewById<TextView>(R.id.tv_status)
        private val viewTimeline = itemView.findViewById<View>(R.id.view_timeline)

        fun bind(item: ScheduleItem) {
            tvTitle.text = item.title
            tvCategory.text = item.category

            // 아이템 클릭 이벤트 추가
            itemView.setOnClickListener {
                itemClickListener?.onItemClick(item)
            }

            // 시간 표시 (시작시간 - 종료시간)
            val timeText = StringBuilder().apply {
                append(item.startTime)
                if (item.endTime.isNotEmpty()) {
                    append(" - ")
                    append(item.endTime)
                }
            }.toString()

            tvTime.text = timeText

            // 상태에 따라 표시 변경
            when (item.status) {
                ScheduleStatus.NOT_STARTED -> {
                    tvStatus.text = "예정"
                    // 예정 상태용 배경 (연한 회색)
                    val bgNotStarted = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.parseColor("#F5F5F5"))  // 연한 회색
                        cornerRadius = 24 * itemView.resources.displayMetrics.density  // 24dp 라운드 코너
                        setStroke(1, Color.parseColor("#E0E0E0"))  // 연한 회색 테두리
                    }
                    tvStatus.background = bgNotStarted
                    tvStatus.setTextColor(Color.parseColor("#666666"))  // 어두운 회색 텍스트
                }
                ScheduleStatus.IN_PROGRESS -> {
                    tvStatus.text = "진행 중"
                    // 진행 중 상태용 배경 (연한 파란색)
                    val bgInProgress = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.parseColor("#B3E5FC"))  // 연한 파란색
                        cornerRadius = 24 * itemView.resources.displayMetrics.density  // 24dp 라운드 코너
                    }
                    tvStatus.background = bgInProgress
                    tvStatus.setTextColor(Color.parseColor("#01579B"))  // 진한 파란색 텍스트
                }
                ScheduleStatus.COMPLETED -> {
                    tvStatus.text = "완료"
                    // 완료 상태용 배경 (연한 녹색)
                    val bgCompleted = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.parseColor("#BBD6A8"))  // 연한 녹색
                        cornerRadius = 24 * itemView.resources.displayMetrics.density  // 24dp 라운드 코너
                    }
                    tvStatus.background = bgCompleted
                    tvStatus.setTextColor(Color.parseColor("#2E7D32"))  // 진한 녹색 텍스트
                }
                ScheduleStatus.POSTPONED -> {
                    tvStatus.text = "연기됨"
                    // 연기 상태용 배경 (연한 주황색)
                    val bgPostponed = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.parseColor("#FFD6A5"))  // 연한 주황색
                        cornerRadius = 24 * itemView.resources.displayMetrics.density  // 24dp 라운드 코너
                    }
                    tvStatus.background = bgPostponed
                    tvStatus.setTextColor(Color.parseColor("#E65100"))  // 진한 주황색 텍스트
                }
            }

            // 패딩 추가
            tvStatus.setPadding(
                (12 * itemView.resources.displayMetrics.density).toInt(),
                (4 * itemView.resources.displayMetrics.density).toInt(),
                (12 * itemView.resources.displayMetrics.density).toInt(),
                (4 * itemView.resources.displayMetrics.density).toInt()
            )

            // 카테고리 태그 배경 설정
            val tagBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#F0F0F0"))
                cornerRadius = 12 * itemView.resources.displayMetrics.density
            }
            tvCategory.background = tagBackground

            // 타임라인 표시 여부
            viewTimeline.visibility = if (item.showTimeline) View.VISIBLE else View.GONE
        }
    }

    private class ScheduleDiffCallback : DiffUtil.ItemCallback<ScheduleItem>() {
        override fun areItemsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem): Boolean {
            return oldItem == newItem
        }
    }
}