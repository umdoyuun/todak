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
import com.example.todak.data.model.MissionItem

class MissionAdapter : ListAdapter<MissionItem, MissionAdapter.MissionViewHolder>(MissionDiffCallback()) {

    interface OnItemClickListener {
        fun onItemClick(missionItem: MissionItem)
    }

    private var listener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MissionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mission, parent, false)
        return MissionViewHolder(view)
    }

    override fun onBindViewHolder(holder: MissionViewHolder, position: Int) {
        val mission = getItem(position)
        holder.bind(mission)
    }

    inner class MissionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMissionTitle: TextView = itemView.findViewById(R.id.tv_mission_title)
        private val tvMissionCategory: TextView = itemView.findViewById(R.id.tv_mission_category)
        private val tvMissionDifficulty: TextView =
            itemView.findViewById(R.id.tv_mission_difficulty)

        // 상태에 따른 색상 설정 함수
        private fun setStatusColor(view: View, participationStatus: String?) {
            // 상태에 따른 색상 설정
            val colorCode = when (participationStatus) {
                "pending" -> "#F8F1DE" // 연한 베이지색 (시작 전)
                "in_progress" -> "#E3F2FB" // 연한 하늘색 (진행 중)
                "completed" -> "#E8F6EE" // 연한 민트색 (완료)
                "failed" -> "#F1F1F1" // 연한 회색 (포기)
                else -> "#F5F5F5" // 기본 색상 (참가하지 않은 경우)
            }

            view.setBackgroundColor(Color.parseColor(colorCode))
        }

        // 미션 아이템 바인딩 함수 (ViewHolder 내부에 있을 수 있음)
        fun bind(missionItem: MissionItem) {
            // 미션 제목, 카테고리, 난이도 등 기본 정보 설정
            tvMissionTitle.text = missionItem.title

            // 카테고리와 난이도 텍스트 변환 및 배경색 적용
            tvMissionCategory.text = getCategoryDisplayName(missionItem.category)
            tvMissionDifficulty.text = getDifficultyDisplayName(missionItem.difficulty)

            // 카테고리와 난이도 배경 스타일 설정
            setCategoryBackground(tvMissionCategory, missionItem.category)
            setDifficultyStyle(tvMissionDifficulty, missionItem.difficulty)

            // 상태 표시 색상 설정
            val statusColorView = itemView.findViewById<View>(R.id.view_status_color)

            if (missionItem.is_participated) {
                // 참가 중인 미션은 상태에 따라 색상 설정
                setStatusColor(statusColorView, missionItem.participation_status)
            } else {
                // 참가하지 않은 미션은 기본 색상 (연한 회색)
                setStatusColor(statusColorView, null)
            }

            // 아이템 클릭 리스너 설정
            itemView.setOnClickListener {
                // 클릭 이벤트 처리 (상세 화면으로 이동 등)
                listener?.onItemClick(missionItem)
            }
        }

        // 상태 버튼 스타일 설정 메서드
        private fun setStatusButtonStyle(textView: TextView, backgroundColor: String) {
            // GradientDrawable 생성
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.cornerRadius = 20 * textView.context.resources.displayMetrics.density // 20dp
            shape.setColor(Color.parseColor(backgroundColor))

            // TextView에 배경 설정
            textView.background = shape
        }

        // 난이도 표시명 반환
        private fun getDifficultyDisplayName(difficulty: String): String {
            return when (difficulty) {
                "beginner" -> "초급"
                "intermediate" -> "중급"
                "advanced" -> "고급"
                else -> difficulty.capitalize()
            }
        }


        // 카테고리 표시명 반환
        private fun getCategoryDisplayName(category: String): String {
            return when (category) {
                "daily_life" -> "일상"
                "career" -> "직업/경력"
                "social_skills" -> "사회성"
                "finance" -> "재정"
                "health" -> "건강"
                "transport" -> "교통"
                "other" -> "기타"
                else -> category.capitalize()
            }
        }

        // 카테고리에 따른 배경색상 및 스타일 설정
        private fun setCategoryBackground(textView: TextView, category: String) {
            // 카테고리에 따른 배경색 설정
            val backgroundColor = when (category) {
                "daily_life" -> Color.parseColor("#FFF3E0")    // 연한 주황색
                "career" -> Color.parseColor("#E1F5FE")        // 연한 파란색
                "social_skills" -> Color.parseColor("#E0F7FA") // 연한 청록색
                "finance" -> Color.parseColor("#E8F5E9")       // 연한 녹색
                "health" -> Color.parseColor("#F3E5F5")        // 연한 보라색
                "transport" -> Color.parseColor("#FAFAFA")     // 연한 회색
                "other" -> Color.parseColor("#EEEEEE")         // 연한 회색
                else -> Color.parseColor("#EEEEEE")            // 기본 연한 회색
            }

            // 동적으로 배경 drawable 생성
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * textView.context.resources.displayMetrics.density // 12dp
                setColor(backgroundColor)
            }

            // TextView에 배경 설정
            textView.background = shape
        }

        // 난이도에 따른 배경색상 및 스타일 설정
        private fun setDifficultyStyle(textView: TextView, difficulty: String) {
            // 난이도에 따른 배경색 설정
            val backgroundColor = when (difficulty) {
                "beginner" -> Color.parseColor("#E8F5E9")     // 연한 녹색
                "intermediate" -> Color.parseColor("#FFF9C4")  // 연한 노란색
                "advanced" -> Color.parseColor("#FFCCBC")      // 연한 주황색
                else -> Color.parseColor("#EEEEEE")           // 기본 연한 회색
            }

            // 동적으로 배경 drawable 생성
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * textView.context.resources.displayMetrics.density // 12dp
                setColor(backgroundColor)
            }

            // TextView에 배경 설정
            textView.background = shape
        }

        // 확장 함수 - String.capitalize()
        private fun String.capitalize(): String {
            return if (this.isNotEmpty()) {
                this[0].uppercase() + this.substring(1)
            } else {
                this
            }
        }
    }
}

// DiffUtil.ItemCallback 구현
class MissionDiffCallback : DiffUtil.ItemCallback<MissionItem>() {
    override fun areItemsTheSame(oldItem: MissionItem, newItem: MissionItem): Boolean {
        return oldItem._id == newItem._id
    }

    override fun areContentsTheSame(oldItem: MissionItem, newItem: MissionItem): Boolean {
        return oldItem == newItem
    }
}