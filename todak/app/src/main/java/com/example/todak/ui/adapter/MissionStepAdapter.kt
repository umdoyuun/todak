package com.example.todak.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ListAdapter
import com.example.todak.R
import com.example.todak.data.model.MissionStep

class MissionStepAdapter : ListAdapter<MissionStep, MissionStepAdapter.StepViewHolder>(StepDiffCallback()) {

    private var isMissionStarted = false // 미션 시작 여부
    private var missionStatus: String? = null // 미션 상태 (pending, in_progress, completed, failed)



    // 미션 시작 상태 설정
    fun setMissionStarted(isStarted: Boolean) {
        this.isMissionStarted = isStarted
        notifyDataSetChanged()
    }

    // 미션 상태 설정 메소드 추가
    fun setMissionStatus(status: String?) {
        this.missionStatus = status
        notifyDataSetChanged()
    }

    interface OnStepCompleteListener {
        fun onStepCompleteChanged(step: MissionStep, position: Int, isCompleted: Boolean)
    }

    private var stepCompleteListener: OnStepCompleteListener? = null

    private var completedSteps = mutableSetOf<Int>() // 완료된 단계를 저장
    private var inProgressSteps = mutableSetOf<Int>() // 진행 중인 단계를 저장
    private var pendingSteps = mutableSetOf<Int>() // 대기 중인 단계를 저장

    private val stepNotes = mutableMapOf<Int, String>() // 단계별 메모 저장

    // 순차적 단계 활성화 여부를 결정하는 변수 추가
    private var useSequentialActivation = true // 기본값은 순차적 활성화

    // 순차적 활성화 설정 메소드 추가
    fun setSequentialActivation(useSequential: Boolean) {
        this.useSequentialActivation = useSequential
        notifyDataSetChanged()
    }

    // 다음 단계 활성화를 위한 메소드 추가
    fun activateNextStep(completedStepOrder: Int) {
        // 완료된 단계가 inProgressSteps에 있었다면 제거
        inProgressSteps.remove(completedStepOrder)

        // 다음 단계 찾기
        val nextStepOrder = completedStepOrder + 1

        // 다음 단계가 pendingSteps에 있는지 확인하고 있다면 inProgressSteps로 이동
        if (pendingSteps.contains(nextStepOrder)) {
            pendingSteps.remove(nextStepOrder)
            inProgressSteps.add(nextStepOrder)
            notifyDataSetChanged()
        }
    }

    fun setOnStepCompleteListener(listener: OnStepCompleteListener) {
        this.stepCompleteListener = listener
    }

    // 완료된 단계 설정
    fun setCompletedSteps(completedOrders: Set<Int>) {
        this.completedSteps = completedOrders.toMutableSet()
        notifyDataSetChanged()
    }

    // 진행 중인 단계 설정
    fun setInProgressSteps(inProgressOrders: Set<Int>) {
        this.inProgressSteps = inProgressOrders.toMutableSet()
        notifyDataSetChanged()
    }

    // 대기 중인 단계 설정
    fun setPendingSteps(pendingOrders: Set<Int>) {
        this.pendingSteps = pendingOrders.toMutableSet()
        notifyDataSetChanged()
    }

    // 단계별 메모 설정
    fun setStepNote(stepOrder: Int, note: String) {
        stepNotes[stepOrder] = note
        notifyDataSetChanged()
    }

    // 단계별 메모 조회
    fun getStepNote(stepOrder: Int): String? {
        return stepNotes[stepOrder]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mission_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val step = getItem(position)
        val isCompleted = completedSteps.contains(step.order)
        val isInProgress = inProgressSteps.contains(step.order)
        val isPending = pendingSteps.contains(step.order)
        val note = stepNotes[step.order]

        // 단계 상태에 따라 bind 메서드 호출
        holder.bind(step, isCompleted, isInProgress, isPending, note)

        // 체크박스 표시 처리
        val checkBox = holder.itemView.findViewById<CheckBox>(R.id.cb_step_complete)

        // 미션 상태가 in_progress, completed, failed인 경우에만 체크박스 관련 처리
        if (missionStatus == "in_progress" || missionStatus == "completed" || missionStatus == "failed") {

            // 단계별 상태에 따른 체크박스 처리
            when {
                // 완료된 단계: 체크됨, 비활성화
                isCompleted -> {
                    checkBox.visibility = View.VISIBLE
                    checkBox.isChecked = true
                    checkBox.isEnabled = false
                    checkBox.alpha = 0.7f
                    checkBox.setOnClickListener(null)
                }

                // 진행 중인 단계: 체크 안됨, 활성화
                isInProgress -> {
                    checkBox.visibility = View.VISIBLE
                    checkBox.isChecked = false

                    // 미션 자체가 완료되거나 포기된 상태면 체크박스 비활성화
                    if (missionStatus == "completed" || missionStatus == "failed") {
                        checkBox.isEnabled = false
                        checkBox.alpha = 0.5f
                        checkBox.setOnClickListener(null)
                    } else {
                        // 미션이 진행 중인 상태에서만 체크박스 활성화
                        checkBox.isEnabled = true
                        checkBox.alpha = 1.0f

                        // 체크박스 클릭 이벤트 설정
                        checkBox.setOnClickListener {
                            val isChecked = checkBox.isChecked
                            if (isChecked) {
                                // 리스너 호출 (다이얼로그 표시)
                                stepCompleteListener?.onStepCompleteChanged(step, position, isChecked)

                                // 실제 API 완료 처리가 되기 전에 다시 체크 해제 상태로 복원
                                checkBox.isChecked = false
                            }
                        }
                    }
                }

                // 대기 중인 단계: 이전 단계 완료 상태에 따라 처리
                isPending -> {
                    // 순차적 활성화 모드가 켜져 있을 때만 적용
                    if (useSequentialActivation) {
                        // 이전 단계가 모두 완료되었는지 확인
                        val isPreviousStepCompleted = isPreviousStepCompleted(step.order)

                        if (isPreviousStepCompleted && missionStatus == "in_progress") {
                            // 이전 단계가 완료되었다면 이 단계는 진행 가능 상태
                            checkBox.visibility = View.VISIBLE
                            checkBox.isChecked = false
                            checkBox.isEnabled = true
                            checkBox.alpha = 0.9f

                            // 체크박스 클릭 이벤트 설정
                            checkBox.setOnClickListener {
                                val isChecked = checkBox.isChecked
                                if (isChecked) {
                                    stepCompleteListener?.onStepCompleteChanged(step, position, isChecked)
                                    checkBox.isChecked = false
                                }
                            }

                            // 진행 중 상태로 업데이트
                            if (!inProgressSteps.contains(step.order)) {
                                inProgressSteps.add(step.order)
                                pendingSteps.remove(step.order)
                            }
                        } else {
                            // 이전 단계가 완료되지 않았으면 체크박스 숨김
                            checkBox.visibility = View.GONE
                        }
                    } else {
                        // 순차적 활성화가 꺼져 있으면 체크박스 숨김
                        checkBox.visibility = View.GONE
                    }
                }

                // 상태 정보가 없는 경우 (기본값): 미션 상태에 따라 처리
                else -> {
                    if (missionStatus == "in_progress") {
                        // 순차적 활성화 모드일 경우 첫 번째 단계만 활성화
                        val isFirstStep = isFirstStep(step.order)
                        if (useSequentialActivation && isFirstStep) {
                            // 첫 번째 단계는 활성화
                            checkBox.visibility = View.VISIBLE
                            checkBox.isChecked = false
                            checkBox.isEnabled = true
                            checkBox.alpha = 1.0f

                            // 체크박스 클릭 이벤트 설정
                            checkBox.setOnClickListener {
                                val isChecked = checkBox.isChecked
                                if (isChecked) {
                                    stepCompleteListener?.onStepCompleteChanged(step, position, isChecked)
                                    checkBox.isChecked = false
                                }
                            }

                            // 진행 중 상태로 업데이트
                            if (!inProgressSteps.contains(step.order)) {
                                inProgressSteps.add(step.order)
                            }
                        } else if (!useSequentialActivation) {
                            // 순차적 활성화를 사용하지 않는 경우 모든 단계 활성화
                            checkBox.visibility = View.VISIBLE
                            checkBox.isChecked = false
                            checkBox.isEnabled = true
                            checkBox.alpha = 1.0f

                            checkBox.setOnClickListener {
                                val isChecked = checkBox.isChecked
                                if (isChecked) {
                                    stepCompleteListener?.onStepCompleteChanged(step, position, isChecked)
                                    checkBox.isChecked = false
                                }
                            }
                        } else {
                            // 첫 번째 단계가 아닌 경우 숨김
                            checkBox.visibility = View.GONE
                        }
                    } else {
                        // 그 외 상태에서는 체크박스 숨김
                        checkBox.visibility = View.GONE
                    }
                }
            }
        } else {
            // 미션이 시작되지 않은 경우 모든 체크박스 숨김
            checkBox.visibility = View.GONE
        }
    }

    // 이전 단계가 모두 완료되었는지 확인하는 헬퍼 메소드
    private fun isPreviousStepCompleted(currentStepOrder: Int): Boolean {
        // 현재 단계 이전의 모든 단계가 completedSteps에 있는지 확인
        for (order in 1 until currentStepOrder) {
            if (!completedSteps.contains(order)) {
                return false
            }
        }
        return true
    }

    // 첫 번째 단계인지 확인하는 헬퍼 메소드
    private fun isFirstStep(stepOrder: Int): Boolean {
        return stepOrder == 1 || (currentList.isNotEmpty() && stepOrder == currentList.first().order)
    }

    inner class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStepNumber: TextView = itemView.findViewById(R.id.tv_step_number)
        private val tvStepTitle: TextView = itemView.findViewById(R.id.tv_step_title)
        private val tvStepContent: TextView = itemView.findViewById(R.id.tv_step_content)
        private val cbStepComplete: CheckBox = itemView.findViewById(R.id.cb_step_complete)
        private val tvStepNote: TextView = itemView.findViewById(R.id.tv_step_note)

        fun bind(step: MissionStep, isCompleted: Boolean, isInProgress: Boolean, isPending: Boolean, note: String?) {
            tvStepNumber.text = step.order.toString()
            tvStepTitle.text = step.title
            tvStepContent.text = step.content

            // 체크박스 상태 설정 (리스너 호출 방지를 위해 리스너 제거)
            cbStepComplete.setOnCheckedChangeListener(null)
            cbStepComplete.isChecked = isCompleted

            // 메모 표시 (있는 경우)
            if (!note.isNullOrEmpty()) {
                tvStepNote.text = "메모: $note"
                tvStepNote.visibility = View.VISIBLE
            } else {
                tvStepNote.visibility = View.GONE
            }

            // 단계 상태에 따른 시각적 표시
            when {
                isCompleted -> {
                    // 완료된 단계: 폰트 약간 흐리게, 옵션: 취소선 추가
                    tvStepTitle.alpha = 0.8f
                    tvStepContent.alpha = 0.7f
                    // tvStepTitle.paintFlags = tvStepTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                }
                isInProgress -> {
                    // 진행 중인 단계: 기본 스타일
                    tvStepTitle.alpha = 1.0f
                    tvStepContent.alpha = 1.0f
                }
                isPending -> {
                    // 대기 중인 단계: 약간 흐리게
                    tvStepTitle.alpha = 0.7f
                    tvStepContent.alpha = 0.6f
                }
                else -> {
                    // 상태 정보가 없는 경우: 기본 스타일
                    tvStepTitle.alpha = 1.0f
                    tvStepContent.alpha = 1.0f
                }
            }
        }
    }

    private class StepDiffCallback : DiffUtil.ItemCallback<MissionStep>() {
        override fun areItemsTheSame(oldItem: MissionStep, newItem: MissionStep): Boolean {
            return oldItem.order == newItem.order
        }

        override fun areContentsTheSame(oldItem: MissionStep, newItem: MissionStep): Boolean {
            return oldItem == newItem
        }
    }
}