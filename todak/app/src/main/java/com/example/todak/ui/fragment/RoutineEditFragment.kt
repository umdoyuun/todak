package com.example.todak.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.todak.R
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.RoutineItem
import com.example.todak.data.model.RoutineStepRequest
import com.example.todak.data.repository.RoutineRepository
import kotlinx.coroutines.launch

class RoutineEditFragment : Fragment() {

    private lateinit var tvEditRoutineTitle: TextView
    private lateinit var etRoutineName: EditText
    private lateinit var etRoutineDescription: EditText
    private lateinit var llStepsContainer: LinearLayout
    private lateinit var btnAddStep: ImageView
    private lateinit var btnSave: Button
    private lateinit var ibClose: ImageButton

    private val routineRepository = RoutineRepository()
    private val TAG = "RoutineEditFragment"

    // 편집 모드 여부
    private var isEditMode = false
    private var routineId: String? = null
    private var routine: RoutineItem? = null
    private var currentStepCount = 0

    companion object {
        private const val ARG_ROUTINE_ID = "routineId"
        private const val ARG_ROUTINE = "routine"
        private const val ARG_IS_EDIT_MODE = "isEditMode"

        // 새 루틴 추가 모드로 시작
        fun newInstance(): RoutineEditFragment {
            val fragment = RoutineEditFragment()
            val args = Bundle()
            args.putBoolean(ARG_IS_EDIT_MODE, false)
            fragment.arguments = args
            return fragment
        }

        // 기존 루틴 수정 모드로 시작
        fun newInstance(routineId: String, routine: RoutineItem): RoutineEditFragment {
            val fragment = RoutineEditFragment()
            val args = Bundle()
            args.putBoolean(ARG_IS_EDIT_MODE, true)
            args.putString(ARG_ROUTINE_ID, routineId)
            args.putSerializable(ARG_ROUTINE, routine)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isEditMode = it.getBoolean(ARG_IS_EDIT_MODE, false)
            if (isEditMode) {
                routineId = it.getString(ARG_ROUTINE_ID)
                routine = it.getSerializable(ARG_ROUTINE) as? RoutineItem
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_routine_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뷰 초기화
        tvEditRoutineTitle = view.findViewById(R.id.tv_edit_routine_title)
        etRoutineName = view.findViewById(R.id.et_routine_name)
        etRoutineDescription = view.findViewById(R.id.et_routine_description)
        llStepsContainer = view.findViewById(R.id.ll_steps_container)
        btnAddStep = view.findViewById(R.id.btn_add_step)
        btnSave = view.findViewById(R.id.btn_save)
        ibClose = view.findViewById(R.id.ib_close)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        if (isEditMode) {
            // 수정 모드
            tvEditRoutineTitle.text = "루틴 수정"
            btnSave.text = "수정하기"


            // 단계 추가 버튼 숨기기 (수정 모드에서는 단계 추가 불가)
            btnAddStep.visibility = View.GONE

            // 루틴 데이터가 있으면 화면에 표시
            routine?.let {
                etRoutineName.setText(it.title)
                etRoutineDescription.setText(it.description)

                // 단계 추가
                it.steps.forEach { step ->
                    addStepView(step.step_index, step.title, step.description ?: "", step.duration_minutes, showDeleteButton = false)
                }
            } ?: run {
                Toast.makeText(context, "루틴 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                // 수정할 루틴이 없으면 뒤로 가기
                requireActivity().onBackPressed()
            }
        } else {
            // 추가 모드
            tvEditRoutineTitle.text = "루틴 추가"
            btnSave.text = "추가하기"

            // 단계 추가 버튼 표시 (추가 모드에서는 단계 추가/삭제 가능)
            btnAddStep.visibility = View.VISIBLE

            // 기본 단계 하나 추가 (삭제 버튼 포함)
            addStepView(showDeleteButton = true)
        }
    }

    private fun setupListeners() {
        // 단계 추가 버튼 클릭 리스너
        btnAddStep.setOnClickListener {
            addStepView()
        }

        // 저장 버튼 클릭 리스너
        btnSave.setOnClickListener {
            if (validateInputs()) {
                saveRoutine()
            }
        }

        // 닫기 버튼 클릭 리스너
        ibClose.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    // 나머지 addStepView, validateInputs, saveRoutine 등의 메서드는 이전과 동일

    private fun addStepView(stepIndex: Int? = null, title: String = "", description: String = "", duration: Int = 0, showDeleteButton: Boolean = true) {
        val stepView = LayoutInflater.from(context).inflate(R.layout.item_step_edit, llStepsContainer, false)

        // 단계 번호 설정
        val tvStepNumber = stepView.findViewById<TextView>(R.id.tv_step_number)
        val newStepIndex = stepIndex ?: currentStepCount
        tvStepNumber.text = (newStepIndex + 1).toString()

        // 단계 입력 필드에 기존 값 설정
        val etStepTitle = stepView.findViewById<EditText>(R.id.et_step_title)
        val etStepDescription = stepView.findViewById<EditText>(R.id.et_step_description)
        val etStepDuration = stepView.findViewById<EditText>(R.id.et_step_duration)

        etStepTitle.setText(title)
        etStepDescription.setText(description)
        if (duration > 0) {
            etStepDuration.setText(duration.toString())
        }

        // 단계에 태그 설정 (나중에 순서 변경 시 사용)
        stepView.tag = newStepIndex

        // 단계 삭제 버튼 - 수정 모드에서는 표시하지 않음
        val ibDeleteStep = stepView.findViewById<ImageButton>(R.id.ib_delete_step)
        if (showDeleteButton) {
            ibDeleteStep.visibility = View.VISIBLE
            ibDeleteStep.setOnClickListener {
                llStepsContainer.removeView(stepView)
                updateStepNumbers()
            }
        } else {
            ibDeleteStep.visibility = View.GONE
        }

        // 컨테이너에 단계 뷰 추가
        llStepsContainer.addView(stepView)

        // 현재 단계 개수 증가
        currentStepCount = llStepsContainer.childCount
    }

    private fun updateStepNumbers() {
        for (i in 0 until llStepsContainer.childCount) {
            val stepView = llStepsContainer.getChildAt(i)
            val tvStepNumber = stepView.findViewById<TextView>(R.id.tv_step_number)
            tvStepNumber.text = (i + 1).toString()

            // 태그 업데이트
            stepView.tag = i
        }

        // 현재 단계 개수 업데이트
        currentStepCount = llStepsContainer.childCount
    }

    private fun validateInputs(): Boolean {
        // 루틴 이름 검증
        if (etRoutineName.text.toString().trim().isEmpty()) {
            Toast.makeText(context, "루틴 이름을 입력하세요.", Toast.LENGTH_SHORT).show()
            etRoutineName.requestFocus()
            return false
        }

        // 단계가 하나 이상 있는지 확인
        if (llStepsContainer.childCount == 0) {
            Toast.makeText(context, "최소 하나의 단계를 추가하세요.", Toast.LENGTH_SHORT).show()
            return false
        }

        // 각 단계 검증
        for (i in 0 until llStepsContainer.childCount) {
            val stepView = llStepsContainer.getChildAt(i)

            val etStepTitle = stepView.findViewById<EditText>(R.id.et_step_title)
            if (etStepTitle.text.toString().trim().isEmpty()) {
                Toast.makeText(context, "${i + 1}번 단계의 제목을 입력하세요.", Toast.LENGTH_SHORT).show()
                etStepTitle.requestFocus()
                return false
            }

            val etStepDuration = stepView.findViewById<EditText>(R.id.et_step_duration)
            if (etStepDuration.text.toString().trim().isEmpty()) {
                Toast.makeText(context, "${i + 1}번 단계의 예상 시간을 입력하세요.", Toast.LENGTH_SHORT).show()
                etStepDuration.requestFocus()
                return false
            }

            // 시간 값이 0보다 큰지 확인
            val duration = etStepDuration.text.toString().toIntOrNull() ?: 0
            if (duration <= 0) {
                Toast.makeText(context, "${i + 1}번 단계의 예상 시간은 0보다 커야 합니다.", Toast.LENGTH_SHORT).show()
                etStepDuration.requestFocus()
                return false
            }
        }

        return true
    }

    private fun saveRoutine() {
        // 버튼 비활성화 및 텍스트 변경
        btnSave.isEnabled = false
        btnSave.text = if (isEditMode) "수정 중..." else "추가 중..."

        // 루틴 정보 가져오기
        val name = etRoutineName.text.toString().trim()
        val description = etRoutineDescription.text.toString().trim()

        // 활성화 상태를 무조건 true로 설정
        val isActive = true

        // 단계 정보 수집
        val steps = collectSteps()

        lifecycleScope.launch {
            val result = if (isEditMode && routineId != null) {
                // 수정 모드
                routineRepository.updateRoutine(
                    routineId = routineId!!,
                    title = name,
                    description = description,
                    steps = steps,
                    isActive = isActive
                )
            } else {
                // 추가 모드
                routineRepository.createRoutine(
                    title = name,
                    description = description,
                    steps = steps
                )
            }

            when (result) {
                is NetworkResult.Success -> {
                    val message = if (isEditMode) "루틴이 수정되었습니다." else "루틴이 추가되었습니다."
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                    // 이전 화면으로 돌아가기
                    requireActivity().onBackPressed()
                }
                is NetworkResult.Error -> {
                    val actionType = if (isEditMode) "수정" else "추가"
                    Toast.makeText(context, "$actionType 실패: ${result.message}", Toast.LENGTH_SHORT).show()

                    // 버튼 상태 복원
                    btnSave.isEnabled = true
                    btnSave.text = if (isEditMode) "수정하기" else "추가하기"
                }
                is NetworkResult.Loading -> {
                    // 로딩 처리
                }
            }
        }
    }

    private fun collectSteps(): List<RoutineStepRequest> {
        val steps = mutableListOf<RoutineStepRequest>()

        for (i in 0 until llStepsContainer.childCount) {
            val stepView = llStepsContainer.getChildAt(i)

            val etStepTitle = stepView.findViewById<EditText>(R.id.et_step_title)
            val etStepDescription = stepView.findViewById<EditText>(R.id.et_step_description)
            val etStepDuration = stepView.findViewById<EditText>(R.id.et_step_duration)

            val stepTitle = etStepTitle.text.toString().trim()
            val stepDescription = etStepDescription.text.toString().trim()
            val stepDuration = etStepDuration.text.toString().toIntOrNull() ?: 0

            // 수정 모드에서는 원래 step_index 유지 (태그에 저장된 값 사용)
            val stepIndex = if (isEditMode) {
                stepView.tag as? Int ?: i
            } else {
                i
            }

            steps.add(RoutineStepRequest(
                step_index = stepIndex,
                title = stepTitle,
                description = stepDescription,
                duration_minutes = stepDuration
            ))
        }

        return steps
    }
}