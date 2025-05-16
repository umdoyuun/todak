package com.example.todak.ui.fragment

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.todak.R
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.RoutineItem
import com.example.todak.data.model.RoutineStep
import com.example.todak.data.repository.RoutineRepository
import com.example.todak.util.SessionManager
import kotlinx.coroutines.launch

class RoutineFragment : Fragment() {

    private lateinit var tvRoutineTitle: TextView
    private lateinit var tvRoutineDescription: TextView
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var llStepsContainer: LinearLayout
    private lateinit var tvEmptyState: TextView
    private lateinit var btnStartRoutine: Button

    private val routineRepository = RoutineRepository()
    private val TAG = "RoutineFragment"

    // 현재 루틴 데이터
    private var currentRoutine: RoutineItem? = null
    private var currentRoutineId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_routine, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뷰 초기화
        tvRoutineTitle = view.findViewById(R.id.tv_routine_title)
        tvRoutineDescription = view.findViewById(R.id.tv_routine_description)
        tvProgress = view.findViewById(R.id.tv_progress)
        progressBar = view.findViewById(R.id.progress_bar)
        llStepsContainer = view.findViewById(R.id.ll_steps_container)
        tvEmptyState = view.findViewById(R.id.tv_empty_state)
        btnStartRoutine = view.findViewById(R.id.btn_start_routine)
        val btnEditRoutine = view.findViewById<Button>(R.id.btn_edit_routine)
        val btnAddRoutine = view.findViewById<Button>(R.id.btn_add_routine)

        // 루틴 추가 버튼 클릭 리스너
        btnAddRoutine.setOnClickListener {
            navigateToAddRoutine()
        }

        // 루틴 수정 버튼 클릭 리스너
        btnEditRoutine.setOnClickListener {
            if (currentRoutine != null) {
                navigateToEditRoutine(currentRoutine!!)
            } else {
                Toast.makeText(context, "수정할 루틴이 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 시작 버튼 클릭 리스너 설정 - 다이얼로그 없이 직접 시작
        btnStartRoutine.setOnClickListener {
            if (currentRoutineId.isNotEmpty()) {
                startRoutine(currentRoutineId)
            } else {
                Toast.makeText(context, "루틴 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 세션 체크
        if (checkUserSession()) {
            // 루틴 데이터 가져오기
            fetchRoutines()
        } else {
            // 로그인 상태가 아닌 경우 처리
            showLoginRequiredMessage()
        }

    }

    private fun checkUserSession(): Boolean {
        val accessToken = SessionManager.getAuthToken()
        val userId = SessionManager.getUserId()
        return !accessToken.isNullOrEmpty() && !userId.isNullOrEmpty()
    }

    private fun showLoginRequiredMessage() {
        tvEmptyState.text = "로그인이 필요합니다."
        tvEmptyState.visibility = View.VISIBLE
        tvRoutineTitle.visibility = View.GONE
        tvRoutineDescription.visibility = View.GONE
        tvProgress.visibility = View.GONE
        llStepsContainer.visibility = View.GONE
    }

    private fun fetchRoutines() {
        // 로딩 상태 표시
        showLoading(true)

        lifecycleScope.launch {
            when (val result = routineRepository.getRoutines()) {
                is NetworkResult.Success -> {
                    // 로딩 상태 숨김
                    showLoading(false)

                    val routines = result.data.routines
                    if (routines.isNotEmpty()) {
                        // 첫 번째 루틴 표시
                        currentRoutine = routines.first()

                        // 루틴 ID 저장 및 로그 출력 (디버깅용)
                        currentRoutineId = currentRoutine?.routine_id ?: ""
                        Log.d(TAG, "루틴 ID 저장: $currentRoutineId")

                        if (currentRoutineId.isEmpty()) {
                            Log.e(TAG, "루틴 ID가 빈 문자열입니다. 현재 루틴: $currentRoutine")
                            Toast.makeText(context, "루틴 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                            showEmptyRoutine()
                            return@launch
                        }

                        displayRoutineDetails(currentRoutine!!)
                    } else {
                        // 루틴이 없는 경우
                        currentRoutine = null
                        currentRoutineId = ""
                        showEmptyRoutine()
                    }

                    Log.d(TAG, "루틴 목록 가져오기 성공: ${routines.size}개 항목")
                }
                is NetworkResult.Error -> {
                    // 로딩 상태 숨김
                    showLoading(false)

                    // 오류 처리 코드
                    handleApiError(result.message)

                    Log.e(TAG, "루틴 데이터 로드 오류: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    // 로딩 상태 표시
                    showLoading(true)
                }
            }
        }
    }

    private fun displayRoutineDetails(routine: RoutineItem) {

        // 타이틀과 설명 설정
        tvRoutineTitle.text = routine.title
        tvRoutineDescription.text = routine.description ?: ""

        // 진행 상황 표시
        val progressText = "진행 상황: ${routine.progress.completed_steps}/${routine.progress.total_steps} " +
                "(${String.format("%.1f", routine.progress.progress_percentage)}%)"
        tvProgress.text = progressText

        updateProgressBar(
            routine.progress.completed_steps,
            routine.progress.total_steps,
            routine.progress.progress_percentage
        )

        // 루틴 시작 상태에 따른 안내 메시지 추가
        val tvStartStatus = view?.findViewById<TextView>(R.id.tv_start_status)
        when {
            // 루틴이 완료된 경우
            routine.started && routine.progress.progress_percentage >= 100.0 -> {
                tvStartStatus?.text = "루틴이 완료되었습니다! 모든 단계를 마쳤습니다."
                tvStartStatus?.visibility = View.VISIBLE
            }
            // 루틴이 시작되었지만 완료되지 않은 경우
            routine.started -> {
                tvStartStatus?.text = "현재 루틴이 진행 중입니다. 단계별로 완료 또는 건너뛰기를 선택하세요."
                tvStartStatus?.visibility = View.VISIBLE
            }
            // 루틴이 시작되지 않은 경우
            else -> {
                tvStartStatus?.text = "루틴을 시작하려면 아래 '루틴 시작하기' 버튼을 누르세요."
                tvStartStatus?.visibility = View.VISIBLE
            }
        }

        // 단계 컨테이너 초기화
        llStepsContainer.removeAllViews()

        // 루틴 단계 표시
        routine.steps.forEach { step ->
            addStepView(step)
        }

        // 시작 버튼 상태 업데이트
        updateStartButtonState(routine)

        // 컨테이너 표시
        tvRoutineTitle.visibility = View.VISIBLE
        tvRoutineDescription.visibility = View.VISIBLE
        tvProgress.visibility = View.VISIBLE
        tvStartStatus?.visibility = View.VISIBLE
        llStepsContainer.visibility = View.VISIBLE
        btnStartRoutine.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE
    }

    private fun updateProgressBar(completedSteps: Int, totalSteps: Int, progressPercentage: Double) {
        val percentage = progressPercentage.toInt()

        // 프로그레스 바 업데이트
        progressBar.progress = percentage

    }

    private fun updateStartButtonState(routine: RoutineItem) {
        // 루틴 진행 상태에 따른 버튼 업데이트
        when {
            // 루틴이 완료된 경우 (100% 이상)
            routine.started && routine.progress.progress_percentage >= 100.0 -> {
                btnStartRoutine.text = "루틴 완료"
                btnStartRoutine.isEnabled = false
                // 버튼 색상을 회색으로 변경
                btnStartRoutine.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray)
            }
            // 루틴이 시작되었지만 완료되지 않은 경우
            routine.started -> {
                btnStartRoutine.text = "루틴 진행 중"
                btnStartRoutine.isEnabled = false
                // 버튼 색상을 회색으로 변경
                btnStartRoutine.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray)
            }
            // 루틴이 시작되지 않은 경우
            else -> {
                btnStartRoutine.text = "루틴 시작하기"
                btnStartRoutine.isEnabled = true
                // 버튼 색상을
                btnStartRoutine.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green)
            }
        }
    }

    private fun startRoutine(routineId: String) {
        lifecycleScope.launch {
            // 버튼 비활성화 (중복 클릭 방지)
            btnStartRoutine.isEnabled = false
            btnStartRoutine.text = "시작 중..."

            when (val result = routineRepository.startRoutine(routineId)) {
                is NetworkResult.Success -> {
                    Toast.makeText(context, "루틴을 시작했습니다.", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "루틴 시작 성공: ${result.data.message}")

                    // 루틴 데이터 새로고침
                    fetchRoutines()
                }
                is NetworkResult.Error -> {
                    // 버튼 다시 활성화 (오류 시)
                    btnStartRoutine.isEnabled = true
                    btnStartRoutine.text = "루틴 시작하기"

                    Toast.makeText(context, "루틴 시작 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "루틴 시작 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    // 로딩 처리
                }
            }
        }
    }

    private fun addStepView(step: RoutineStep) {
        val stepView = layoutInflater.inflate(R.layout.item_routine_step, llStepsContainer, false)

        // 뷰에 태그 설정 (단계 인덱스로 식별하기 위함)
        stepView.tag = step.step_index

        // 단계 번호와 제목 설정
        val tvStepNumber = stepView.findViewById<TextView>(R.id.tv_step_number)
        val tvStepTitle = stepView.findViewById<TextView>(R.id.tv_step_title)
        val tvStepDescription = stepView.findViewById<TextView>(R.id.tv_step_description)
        val tvStepNotes = stepView.findViewById<TextView>(R.id.tv_step_notes)
        val tvCompletionStatus = stepView.findViewById<TextView>(R.id.tv_completion_status)
        val buttonLayout = stepView.findViewById<LinearLayout>(R.id.button_layout)
        val btnCompleteStep = stepView.findViewById<Button>(R.id.btn_complete_step)
        val btnSkipStep = stepView.findViewById<Button>(R.id.btn_skip_step)

        tvStepNumber.text = (step.step_index + 1).toString()
        tvStepTitle.text = step.title
        tvStepDescription.text = step.description ?: ""

        // 소요 시간 설정
        val tvDuration = stepView.findViewById<TextView>(R.id.tv_duration)
        if (step.duration_minutes > 0) {
            tvDuration.text = "${step.duration_minutes}분"
            tvDuration.visibility = View.VISIBLE
        } else {
            tvDuration.visibility = View.GONE
        }

        // 루틴 시작 여부 확인
        val isRoutineStarted = currentRoutine?.started == true

        // 단계 상태에 따른 처리
        when (step.status) {
            "completed" -> {
                // 완료된 단계
                buttonLayout.visibility = View.GONE

                // 완료 상태 메시지 표시 (녹색 버튼 스타일)
                tvCompletionStatus.text = "완료했습니다."
                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.button_rounded_background)?.mutate()
                drawable?.setTint(ContextCompat.getColor(requireContext(), R.color.darkgreen))
                tvCompletionStatus.background = drawable
                tvCompletionStatus.visibility = View.VISIBLE

                // 메모 설정 (완료된 단계만 메모 표시)
                if (!step.notes.isNullOrEmpty()) {
                    tvStepNotes.text = "메모: ${step.notes}"
                    tvStepNotes.visibility = View.VISIBLE
                } else {
                    tvStepNotes.visibility = View.GONE
                }
            }
            "skipped" -> {
                // 건너뛴 단계
                buttonLayout.visibility = View.GONE

                // 건너뛴 상태 메시지 표시 (회색 버튼 스타일)
                tvCompletionStatus.text = "건너뛴 단계"
                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.button_rounded_background)?.mutate()
                drawable?.setTint(ContextCompat.getColor(requireContext(), R.color.gray))
                tvCompletionStatus.background = drawable
                tvCompletionStatus.visibility = View.VISIBLE

                // 메모 숨기기 (건너뛴 단계는 메모 표시 안함)
                tvStepNotes.visibility = View.GONE
            }
            else -> {
                // 미완료 단계 (pending)
                tvCompletionStatus.visibility = View.GONE
                tvStepNotes.visibility = View.GONE

                if (isRoutineStarted) {
                    // 현재 활성화된 단계인지 확인 (순차적 진행을 위해)
                    val isActiveStep = isStepActive(step.step_index)

                    if (isActiveStep) {
                        // 현재 진행 가능한 단계인 경우 버튼 표시
                        buttonLayout.visibility = View.VISIBLE

                        // 완료 버튼 클릭 리스너
                        btnCompleteStep.setOnClickListener {
                            showStepCompleteDialog(step.step_index, step.title)
                        }

                        // 건너뛰기 버튼 클릭 리스너
                        btnSkipStep.setOnClickListener {
                            showStepSkipConfirmDialog(step.step_index, step.title)
                        }
                    } else {
                        // 아직 진행할 수 없는 단계
                        buttonLayout.visibility = View.GONE
                    }
                } else {
                    // 루틴이 시작되지 않은 경우 버튼 숨김
                    buttonLayout.visibility = View.GONE
                }
            }
        }

        // 단계 뷰를 컨테이너에 추가
        llStepsContainer.addView(stepView)
    }

    // 현재 단계가 활성화되어 있는지 확인 (순차적 진행을 위함)
    private fun isStepActive(stepIndex: Int): Boolean {
        // 루틴이 없는 경우
        if (currentRoutine == null) return false

        // 모든 이전 단계가 완료 또는 건너뛰기되었는지 확인
        for (i in 0 until stepIndex) {
            val previousStep = currentRoutine!!.steps.find { it.step_index == i }
            if (previousStep != null && previousStep.status != "completed" && previousStep.status != "skipped") {
                // 이전 단계 중 하나라도 완료/건너뛰기 되지 않았다면 false
                return false
            }
        }

        // 현재 단계가 완료/건너뛰기되지 않았다면 true
        val currentStep = currentRoutine!!.steps.find { it.step_index == stepIndex }
        return currentStep != null && currentStep.status != "completed" && currentStep.status != "skipped"
    }

    private fun showStepSkipConfirmDialog(stepIndex: Int, stepTitle: String) {
        context?.let { ctx ->
            val alertDialog = AlertDialog.Builder(ctx)
                .setTitle("단계 건너뛰기")
                .setMessage("'${stepTitle}' 단계를 건너뛰시겠습니까?")
                .setPositiveButton("건너뛰기") { dialog, _ ->
                    skipStep(currentRoutineId, stepIndex)
                    dialog.dismiss()
                }
                .setNegativeButton("취소") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            alertDialog.show()
        }
    }

    private fun skipStep(routineId: String, stepIndex: Int) {
        lifecycleScope.launch {
            // 로딩 표시 (필요한 경우)
            // showStepLoading(stepIndex, true)

            when (val result = routineRepository.skipRoutineStep(routineId, stepIndex)) {
                is NetworkResult.Success -> {
                    Toast.makeText(context, "단계를 건너뛰었습니다.", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "단계 건너뛰기 성공: ${result.data.message}")

                    // 루틴 데이터 새로고침
                    fetchRoutines()
                }
                is NetworkResult.Error -> {
                    Toast.makeText(context, "단계 건너뛰기 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "단계 건너뛰기 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    // 로딩 처리
                }
            }

            // 로딩 숨김 (필요한 경우)
            // showStepLoading(stepIndex, false)
        }
    }

    private fun showStepCompleteDialog(stepIndex: Int, stepTitle: String) {
        // 루틴 ID 확인
        if (currentRoutineId.isEmpty()) {
            Log.e(TAG, "루틴 ID가 비어 있습니다. 다이얼로그를 표시할 수 없습니다.")
            Toast.makeText(context, "루틴 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        context?.let { ctx ->
            // 기존의 다이얼로그 사용
            val dialog = Dialog(ctx)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.modal_step_note)

            // 다이얼로그 배경을 투명하게 설정
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            // 다이얼로그 크기 설정
            val window = dialog.window
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 다이얼로그 뷰 초기화
            val tvDialogTitle = dialog.findViewById<TextView>(R.id.tv_dialog_title)
            val etStepNote = dialog.findViewById<EditText>(R.id.et_step_note)
            val btnSubmit = dialog.findViewById<Button>(R.id.btn_submit)
            val ivClose = dialog.findViewById<ImageButton>(R.id.iv_close)

            // 제목 설정 (단계 제목 표시)
            tvDialogTitle.text = "단계 완료: $stepTitle"

            // 완료 버튼 클릭 리스너
            btnSubmit.setOnClickListener {
                val notes = etStepNote.text.toString().trim()
                submitStepCompletion(currentRoutineId, stepIndex, notes)
                dialog.dismiss()
            }

            // 닫기 버튼 클릭 리스너
            ivClose.setOnClickListener {
                dialog.dismiss()
            }

            // 다이얼로그 표시
            dialog.show()
        }
    }

    private fun submitStepCompletion(routineId: String, stepIndex: Int, notes: String) {
        lifecycleScope.launch {
            // 디버깅을 위한 로그 추가
            Log.d(TAG, "API 호출 시작 - 루틴 ID: $routineId, 단계 인덱스: $stepIndex")

            when (val result = routineRepository.completeRoutineStep(
                routineId = routineId,
                stepIndex = stepIndex,
                notes = notes
            )) {
                is NetworkResult.Success -> {
                    // 성공 처리
                    Toast.makeText(context, "단계 완료가 기록되었습니다", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "단계 완료 기록 성공: ${result.data.message}")

                    // 루틴 데이터 새로고침
                    fetchRoutines()
                }
                is NetworkResult.Error -> {
                    // 오류 처리
                    Toast.makeText(context, "단계 완료 기록 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "단계 완료 기록 실패: ${result.message}")

                }
                is NetworkResult.Loading -> {
                    // 로딩 처리
                }
            }
        }
    }

    private fun showEmptyRoutine() {
        tvEmptyState.text = "루틴이 없습니다."
        tvEmptyState.visibility = View.VISIBLE
        tvRoutineTitle.visibility = View.GONE
        tvRoutineDescription.visibility = View.GONE
        tvProgress.visibility = View.GONE
        llStepsContainer.visibility = View.GONE
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            tvEmptyState.text = "루틴을 불러오는 중..."
            tvEmptyState.visibility = View.VISIBLE
            tvRoutineTitle.visibility = View.GONE
            tvRoutineDescription.visibility = View.GONE
            tvProgress.visibility = View.GONE
            llStepsContainer.visibility = View.GONE
        }
    }

    private fun handleApiError(errorMessage: String) {
        // 토큰 만료 등의 에러 메시지 처리
        when {
            errorMessage.contains("토큰이 만료되었습니다") -> {
                tvEmptyState.text = "로그인 세션이 만료되었습니다. 다시 로그인해주세요."
                // 로그인 화면으로 이동하는 로직 추가
            }
            errorMessage.contains("로그인이 필요합니다") -> {
                tvEmptyState.text = "로그인이 필요합니다."
                // 로그인 화면으로 이동하는 로직 추가
            }
            else -> {
                tvEmptyState.text = "루틴을 불러오는 중 오류가 발생했습니다."
            }
        }

        tvEmptyState.visibility = View.VISIBLE
        tvRoutineTitle.visibility = View.GONE
        tvRoutineDescription.visibility = View.GONE
        tvProgress.visibility = View.GONE
        llStepsContainer.visibility = View.GONE
    }

    // 루틴 추가 화면으로 이동
    private fun navigateToAddRoutine() {
        try {
            val routineEditFragment = RoutineEditFragment.newInstance()

            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_container, routineEditFragment)
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "RoutineEditFragment 이동 오류", e)
            Toast.makeText(context, "루틴 추가 화면으로 이동할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 루틴 수정 화면으로 이동
    private fun navigateToEditRoutine(routine: RoutineItem) {
        try {
            val routineEditFragment = RoutineEditFragment.newInstance(
                routineId = routine.routine_id,
                routine = routine
            )

            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_container, routineEditFragment)
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "RoutineEditFragment 이동 오류", e)
            Toast.makeText(context, "루틴 수정 화면으로 이동할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // Fragment가 다시 보일 때 데이터를 새로고침
    override fun onResume() {
        super.onResume()
        if (checkUserSession()) {
            fetchRoutines()
        }
    }
}