import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.MissionDetailResponse
import com.example.todak.data.model.MissionParticipationDetail
import com.example.todak.data.model.MissionStep
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.ParticipationStepProgress
import com.example.todak.ui.activity.MainActivity
import com.example.todak.ui.adapter.MissionStepAdapter
import com.example.todak.ui.modal.EmotionCheckModal
import kotlinx.coroutines.launch

class MissionDetailFragment : Fragment(), MissionStepAdapter.OnStepCompleteListener {

    private val TAG = "MissionDetailFragment"

    // 리포지토리
    private val missionRepository = MissionRepository()
    private var missionResponse: MissionDetailResponse? = null

    // 어댑터
    private lateinit var stepAdapter: MissionStepAdapter
    private lateinit var tipsAdapter: MissionTipAdapter
    private lateinit var rvMissionTips: RecyclerView

    // 뷰 요소
    private lateinit var tvMissionTitle: TextView
    private lateinit var tvMissionCategory: TextView
    private lateinit var tvMissionDifficulty: TextView
    private lateinit var tvEstimatedTime: TextView
    private lateinit var tvMissionDescription: TextView
    private lateinit var rvMissionSteps: RecyclerView
    private lateinit var layoutTips: LinearLayout
    private lateinit var tvMissionMaterialLabel: TextView
    private lateinit var tvMissionMaterial: TextView

    // 참가 또는 시작 버튼 참조
    private lateinit var btnMissionAction: Button

    // 참가/시작 상태에 따라 버튼 UI 업데이트
    // 버튼 상태 업데이트 함수 수정
    private fun updateActionButton(isParticipated: Boolean, participationStatus: String? = null) {
        if (isParticipated) {
            // 참가 중인 미션인 경우
            when (participationStatus) {
                "pending" -> {
                    // 참가했지만 아직 시작하지 않은 상태
                    btnMissionAction.text = "미션 시작하기"
                    btnMissionAction.isEnabled = true
                    btnMissionAction.alpha = 1.0f
                    btnMissionAction.setOnClickListener {
                        startMission()
                    }
                }

                "in_progress" -> {
                    // 진행 중인 미션 - 실패/포기 버튼으로 변경
                    btnMissionAction.text = "미션 포기하기"
                    btnMissionAction.isEnabled = true
                    btnMissionAction.alpha = 1.0f
                    // 배경색 변경 (빨간색 계열로 변경)
                    btnMissionAction.backgroundTintList =
                        ColorStateList.valueOf(Color.parseColor("#FFB3B3"))
                    btnMissionAction.setOnClickListener {
                        showFailMissionDialog()
                    }
                }

                "completed" -> {
                    // 완료된 미션
                    btnMissionAction.text = "완료된 미션"
                    btnMissionAction.isEnabled = false
                    btnMissionAction.alpha = 0.5f // 텍스트가 잘 보이도록 알파값 변경
                    // 녹색 배경, 흰색 글씨
                    btnMissionAction.backgroundTintList = null   // 어두운 녹색
                    btnMissionAction.setTextColor(Color.WHITE)
                }
                "failed" -> {
                    // 실패한 미션
                    btnMissionAction.text = "포기한 미션"
                    btnMissionAction.isEnabled = false
                    btnMissionAction.alpha = 0.5f // 텍스트가 잘 보이도록 알파값 변경
                    // 회색 배경, 흰색 글씨
                    btnMissionAction.backgroundTintList = null // 어두운 회색
                    btnMissionAction.setTextColor(Color.WHITE)
                }

                else -> {
                    // 기본값 (상태 정보가 없는 경우)
                    btnMissionAction.text = "미션 시작하기"
                    btnMissionAction.isEnabled = true
                    btnMissionAction.alpha = 1.0f
                    // 배경색 원복
                    btnMissionAction.backgroundTintList = null
                    btnMissionAction.setOnClickListener {
                        startMission()
                    }
                }
            }
        }
        else{
            btnMissionAction.text = "미션 참가하기"
            btnMissionAction.isEnabled = true
            btnMissionAction.alpha = 1.0f
            // 배경색 원복 (필요시)
            btnMissionAction.setOnClickListener {
                participateMission()
            }
        }
    }

    // 데이터를 저장할 변수
    private var missionId: String = ""
    // 참여 ID 저장 변수 추가
    private var participationId: String? = null

    private val completedSteps = mutableSetOf<Int>()

    private var isParticipating: Boolean = false
    private var missionParticipationDetail: MissionParticipationDetail? = null

    companion object {
        private const val ARG_MISSION_ID = "missionId"
        private const val ARG_IS_PARTICIPATING = "isParticipating"
        private const val ARG_PARTICIPATION_ID = "participationId"

        fun newInstance(
            missionId: String,
            isParticipating: Boolean = false,
            participationId: String? = null
        ): MissionDetailFragment {
            val fragment = MissionDetailFragment()
            val args = Bundle()
            args.putString(ARG_MISSION_ID, missionId)
            args.putBoolean(ARG_IS_PARTICIPATING, isParticipating)
            participationId?.let { args.putString(ARG_PARTICIPATION_ID, it) }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 타이틀 설정 (필요한 경우)
        (activity as? MainActivity)?.setToolbarTitle("미션 상세")
        arguments?.let {
            missionId = it.getString(ARG_MISSION_ID, "")
            isParticipating = it.getBoolean(ARG_IS_PARTICIPATING, false)
            participationId = it.getString(ARG_PARTICIPATION_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mission_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뷰 초기화
        initViews(view)

        // 데이터 로드
        loadMissionData()

        if (isParticipating && participationId != null) {
            loadParticipationDetail()
        }

    }
    private fun initViews(view: View) {
        tvMissionTitle = view.findViewById(R.id.tv_mission_title)
        tvMissionCategory = view.findViewById(R.id.tv_mission_category)
        tvMissionDifficulty = view.findViewById(R.id.tv_mission_difficulty)
        tvEstimatedTime = view.findViewById(R.id.tv_estimated_time)
        tvMissionDescription = view.findViewById(R.id.tv_mission_description)
        rvMissionSteps = view.findViewById(R.id.rv_mission_steps)
        rvMissionTips = view.findViewById(R.id.rv_mission_tips)
        btnMissionAction  = view.findViewById(R.id.btn_participate_mission)
        tvMissionMaterialLabel = view.findViewById(R.id.tv_mission_material_label)
        tvMissionMaterial = view.findViewById(R.id.tv_mission_material)


        // 기존 코드
        rvMissionTips = view.findViewById(R.id.rv_mission_tips)

        // 스텝 어댑터 초기화
        stepAdapter = MissionStepAdapter()
        stepAdapter.setOnStepCompleteListener(this) // 이 부분이 누락되었습니다
        rvMissionSteps.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = stepAdapter
        }

        // 팁 어댑터 초기화
        tipsAdapter = MissionTipAdapter()
        rvMissionTips.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = tipsAdapter
        }
    }

    // 참가 상세 정보 로드 후 호출
    private fun updateUIWithParticipationDetail(participationDetail: MissionParticipationDetail) {
        // 미션 시작 상태 확인
        val isMissionStarted = participationDetail.status == "in_progress"

        // 어댑터에 미션 시작 상태 전달
        stepAdapter.setMissionStarted(isMissionStarted)

        // 어댑터에 미션 상태 전달 (신규 추가)
        stepAdapter.setMissionStatus(participationDetail.status)

        // 단계 진행 상태 업데이트
        updateStepProgressFromDetail(participationDetail.step_progress)

        // 버튼 상태 업데이트
        updateActionButton(isParticipated = true, participationDetail.status)
    }

    private fun loadMissionData() {
        lifecycleScope.launch {
            try {
                when (val result = missionRepository.getMissionDetail(missionId)) {
                    is NetworkResult.Success -> {
                        missionResponse = result.data

                        // 기본 UI 업데이트
                        updateUI(result.data)

                        // 참가 중인 미션이고 참가 ID가 있으면 참가 상세 정보 로드
                        if (result.data.is_participated) {
                            // 미션 상태를 어댑터에 전달 (신규 추가)
                            stepAdapter.setMissionStatus(result.data.status)
                            loadParticipationDetail()
                        } else {
                            // 참가하지 않은 경우 버튼 상태 업데이트
                            updateActionButton(isParticipated = false, null)
                            // 미션 상태 null로 설정 - 체크박스 숨김
                            stepAdapter.setMissionStatus(null)
                        }
                    }
                    is NetworkResult.Error -> {
                        Log.e(TAG, "미션 정보 로드 실패: ${result.message}")
                        Toast.makeText(context, "미션 정보를 불러오는데 실패했습니다: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is NetworkResult.Loading -> {
                        // 로딩 처리
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "미션 정보 로드 중 오류 발생", e)
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 미션 참가 상세 정보 로드
    private fun loadParticipationDetail() {
        val currentParticipationId = participationId ?: return

        lifecycleScope.launch {
            try {
                when (val result = missionRepository.getMissionParticipationDetail(currentParticipationId)) {
                    is NetworkResult.Success -> {
                        missionParticipationDetail = result.data

                        // UI 업데이트
                        updateUIWithParticipationDetail(result.data)

                        Log.d(TAG, "미션 참가 상세 정보 로드 성공")
                    }
                    is NetworkResult.Error -> {
                        Log.e(TAG, "미션 참가 상세 정보 로드 실패: ${result.message}")
                        Toast.makeText(context, "참가 정보를 불러오는데 실패했습니다: ${result.message}", Toast.LENGTH_SHORT).show()

                        // 오류 발생 시 기본 상태로 버튼 업데이트
                        updateActionButton(isParticipated = true, null)
                    }
                    is NetworkResult.Loading -> {
                        // 로딩 처리
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "미션 참가 상세 정보 로드 중 오류 발생", e)
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()

                // 오류 발생 시 기본 상태로 버튼 업데이트
                updateActionButton(isParticipated = true, null)
            }
        }
    }


    private fun updateUI(mission: MissionDetailResponse) {
        // 미션 기본 정보 표시
        tvMissionTitle.text = mission.title
        tvMissionCategory.text = getCategoryDisplayName(mission.category)
        tvMissionDifficulty.text = getDifficultyDisplayName(mission.difficulty)
        tvEstimatedTime.text = "약 ${mission.estimated_time}분 소요"
        tvMissionDescription.text = mission.description

        // 카테고리 배경 설정
        setCategoryBackground(tvMissionCategory, mission.category)

        // 난이도 배경 설정
        setDifficultyBackground(tvMissionDifficulty, mission.difficulty)

        // 미션 단계 표시
        stepAdapter.submitList(mission.steps)

        // 준비물 표시
        if (mission.materials.isNotEmpty()) {
            // 준비물 목록을 쉼표로 구분하여 표시
            val materialsText = mission.materials.joinToString(", ")
            tvMissionMaterial.text = materialsText
            tvMissionMaterial.visibility = View.VISIBLE
            tvMissionMaterialLabel.visibility = View.VISIBLE
        } else {
            // 준비물이 없는 경우 영역 숨김
            tvMissionMaterial.visibility = View.GONE
            tvMissionMaterialLabel.visibility = View.GONE
        }

        // 미션 팁 표시
        if (mission.tips.isNotEmpty()) {
            tipsAdapter.submitList(mission.tips)
            rvMissionTips.visibility = View.VISIBLE
            view?.findViewById<TextView>(R.id.tv_tips_label)?.visibility = View.VISIBLE
        } else {
            rvMissionTips.visibility = View.GONE
            view?.findViewById<TextView>(R.id.tv_tips_label)?.visibility = View.GONE
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

    // 난이도 표시명 반환
    private fun getDifficultyDisplayName(difficulty: String): String {
        return when (difficulty) {
            "beginner" -> "초급"
            "intermediate" -> "중급"
            "advanced" -> "고급"
            else -> difficulty.capitalize()
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
    private fun setDifficultyBackground(textView: TextView, difficulty: String) {
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

    // String 확장 함수 - capitalize()
    private fun String.capitalize(): String {
        return if (this.isNotEmpty()) {
            this[0].uppercase() + this.substring(1)
        } else {
            this
        }
    }

    // 미션 참가 API 호출
    private fun participateMission() {
        lifecycleScope.launch {
            try {

                // 미션 참여 API 호출
                val result = missionRepository.joinMission(missionId)

                when (result) {
                    is NetworkResult.Success -> {
                        // 참여 ID 저장
                        participationId = result.data._id

                        Toast.makeText(context, "미션에 참가했습니다!", Toast.LENGTH_SHORT).show()

                        // 버튼 상태 업데이트 (참가 완료, 시작 대기 상태)
                        updateActionButton(isParticipated = true, null)

                        // 성공 후 목록 화면으로 돌아가기
                        activity?.supportFragmentManager?.popBackStack()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(context, "미션 참여 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    else -> { /* 로딩 처리 */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "미션 참여 처리 중 오류", e)
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
            }
        }
    }

    // 미션 시작 API 호출
    private fun startMission() {
        val currentParticipationId = participationId ?: return Toast.makeText(
            context, "참여 ID가 없습니다. 다시 참가해 주세요.", Toast.LENGTH_SHORT
        ).show()

        lifecycleScope.launch {
            try {

                // 미션 시작 API 호출
                val result = missionRepository.startMission(currentParticipationId)

                when (result) {
                    is NetworkResult.Success -> {

                        Toast.makeText(context, "미션을 시작합니다!", Toast.LENGTH_SHORT).show()

                        // 참가 상세 정보 다시 로드하여 UI 업데이트
                        loadParticipationDetail()

                        // 필요시 바로 UI 업데이트
                        stepAdapter.setMissionStarted(true)
                        updateActionButton(isParticipated = true, "in_progress")
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(context, "미션 시작 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    else -> { /* 로딩 처리 */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "미션 시작 처리 중 오류", e)
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
            }
        }
    }

    // 참가 단계 상태 및 메모 업데이트
    private fun updateStepProgressFromDetail(stepProgresses: List<ParticipationStepProgress>) {
        // 완료된 단계 및 진행 중인 단계 식별
        val completedStepOrders = stepProgresses
            .filter { it.status == "completed" }
            .map { it.step_order }
            .toSet()

        // 진행 중인 단계 식별 (추가)
        val inProgressStepOrders = stepProgresses
            .filter { it.status == "in_progress" }
            .map { it.step_order }
            .toSet()

        // 대기 중인 단계 식별 (추가)
        val pendingStepOrders = stepProgresses
            .filter { it.status == "pending" }
            .map { it.step_order }
            .toSet()

        // 순차적 활성화 모드 설정 (첫 단계로 시작)
        stepAdapter.setSequentialActivation(true)

        // 완료된 단계가 없고, 미션이 in_progress 상태인 경우 첫 번째 단계 활성화
        if (completedStepOrders.isEmpty() && missionParticipationDetail?.status == "in_progress") {
            // 미션 단계 목록에서 첫 번째 단계를 찾아 활성화
            val steps = stepAdapter.currentList
            if (steps.isNotEmpty()) {
                val firstStepOrder = steps.first().order
                inProgressStepOrders.toMutableSet().add(firstStepOrder)
                pendingStepOrders.toMutableSet().remove(firstStepOrder)
            }
        }

        // 각 상태에 따른 단계 목록을 어댑터에 전달
        stepAdapter.setCompletedSteps(completedStepOrders)
        stepAdapter.setInProgressSteps(inProgressStepOrders)
        stepAdapter.setPendingSteps(pendingStepOrders)

        // 각 단계의 메모 설정
        for (progress in stepProgresses) {
            if (progress.status == "completed" && !progress.notes.isNullOrEmpty()) {
                stepAdapter.setStepNote(progress.step_order, progress.notes)
                Log.d(TAG, "단계 ${progress.step_order} 메모: ${progress.notes}")
            }
        }
    }

    // 단계 진행 상태 업데이트
    private fun updateStepProgress(stepProgress: List<ParticipationStepProgress>) {
        // 완료된 단계 표시
        val completedStepOrders = stepProgress
            .filter { it.status == "completed" }
            .map { it.step_order }
            .toSet()

        // 진행 중인 단계 표시
        val inProgressStepOrders = stepProgress
            .filter { it.status == "in_progress" }
            .map { it.step_order }
            .toMutableSet()

        // 대기 중인 단계 표시
        val pendingStepOrders = stepProgress
            .filter { it.status == "pending" }
            .map { it.step_order }
            .toMutableSet()

        // 체크박스 상태 업데이트
        this.completedSteps.clear()
        this.completedSteps.addAll(completedStepOrders)

        // 순차적 활성화 모드 설정
        stepAdapter.setSequentialActivation(true)

        // 완료된 단계가 있고, 완료된 단계 다음 단계가 없는 경우 자동으로 활성화
        if (completedStepOrders.isNotEmpty()) {
            val maxCompletedStep = completedStepOrders.maxOrNull() ?: 0
            val nextStepOrder = maxCompletedStep + 1

            // 다음 단계가 존재하고 대기 중인 단계에 있다면 진행 중 상태로 전환
            val steps = stepAdapter.currentList
            if (steps.any { it.order == nextStepOrder } && pendingStepOrders.contains(nextStepOrder)) {
                pendingStepOrders.remove(nextStepOrder)
                inProgressStepOrders.add(nextStepOrder)
            }
        } else if (missionParticipationDetail?.status == "in_progress") {
            // 완료된 단계가 없고 미션이 진행 중인 경우 첫 번째 단계 활성화
            val steps = stepAdapter.currentList
            if (steps.isNotEmpty()) {
                val firstStepOrder = steps.first().order
                if (!inProgressStepOrders.contains(firstStepOrder)) {
                    inProgressStepOrders.add(firstStepOrder)
                    pendingStepOrders.remove(firstStepOrder)
                }
            }
        }

        // 어댑터에 상태 전달
        stepAdapter.setCompletedSteps(completedSteps)
        stepAdapter.setInProgressSteps(inProgressStepOrders)
        stepAdapter.setPendingSteps(pendingStepOrders)

        // 참고용으로 각 단계의 메모 저장
        stepProgress.forEach { progress ->
            if (progress.notes != null) {
                stepAdapter.setStepNote(progress.step_order, progress.notes)
            }
        }
    }

    // 미션 단계 완료 API 호출 (메모 포함)
    private fun completeMissionStep(stepOrder: Int, note: String) {
        val currentParticipationId = participationId ?: return Toast.makeText(
            context, "참여 ID가 없습니다.", Toast.LENGTH_SHORT
        ).show()

        lifecycleScope.launch {
            try {
                // 미션 단계 완료 API 호출
                val result = missionRepository.completeMissionStep(
                    participationId = currentParticipationId,
                    stepOrder = stepOrder,
                    note = note
                )

                when (result) {
                    is NetworkResult.Success -> {
                        // 성공 처리
                        completedSteps.add(stepOrder)
                        stepAdapter.setCompletedSteps(completedSteps)
                        stepAdapter.setStepNote(stepOrder, note)

                        // 중요: 다음 단계 활성화 추가
                        stepAdapter.activateNextStep(stepOrder)

                        Toast.makeText(context, "${stepOrder}단계 완료!", Toast.LENGTH_SHORT).show()

                        // 모든 단계가 완료된 경우 추가 처리
                        checkAllStepsCompleted()
                    }
                    is NetworkResult.Error -> {
                        // 실패 처리
                        Toast.makeText(context, "단계 완료 처리 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                        // 체크박스 상태 복원
                        stepAdapter.setCompletedSteps(completedSteps)
                    }
                    else -> { /* 로딩 처리 */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "단계 완료 처리 중 오류", e)
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                // 체크박스 상태 복원
                stepAdapter.setCompletedSteps(completedSteps)
            } finally {
            }
        }
    }

    // 단계 완료 상태 변경 리스너 구현
    override fun onStepCompleteChanged(step: MissionStep, position: Int, isCompleted: Boolean) {
        // 이미 완료된 단계를 다시 체크하는 경우 무시 (참가 중인 미션의 경우)
        if (isParticipating && completedSteps.contains(step.order) && isCompleted) {
            return
        }
        if (isCompleted) {
            // 메모 입력 다이얼로그 표시
            showStepNoteDialog(step)
        } else {
            // 필요시 단계 완료 취소 API 호출 (해당 API가 있는 경우)
        }
    }

    // 단계 완료 메모 입력 다이얼로그 표시
    private fun showStepNoteDialog(step: MissionStep) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.modal_step_note, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // 다이얼로그 창 모서리 둥글게 설정
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 닫기 버튼 클릭 리스너 설정
        val closeButton = dialogView.findViewById<ImageButton>(R.id.iv_close)
        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        // 완료 버튼 클릭 리스너
        val btnSubmit = dialogView.findViewById<Button>(R.id.btn_submit)
        val etStepNote = dialogView.findViewById<EditText>(R.id.et_step_note)

        btnSubmit.setOnClickListener {
            val note = etStepNote.text.toString().trim()
            dialog.dismiss()

            // 메모와 함께 단계 완료 API 호출
            completeMissionStep(step.order, note)
        }

        dialog.show()
    }

    // 모든 단계가 완료되었는지 확인
    private fun checkAllStepsCompleted() {
        val allSteps = stepAdapter.currentList
        val isAllCompleted = allSteps.all { completedSteps.contains(it.order) }

        if (isAllCompleted && allSteps.isNotEmpty()) {
            // 모든 단계 완료 시 처리
            Toast.makeText(context, "모든 단계를 완료했습니다!", Toast.LENGTH_SHORT).show()
            updateActionButton(isParticipated = true, "completed")

            val missionTitle = missionResponse?.title ?: "미션"
            val context = "after_mission:${missionTitle}"
            val message = "잘했어요! ${missionTitle} 미션을 완수했어요! 감정은 어땠나요?"
            activity?.let {
                EmotionCheckModal.show(it, context, message)
            }
            // 필요에 따라 미션 완료 API 호출
            // completeMission()
        }
    }

    // 미션 실패/포기 다이얼로그 표시
    private fun showFailMissionDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.modal_mission_fail, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // 다이얼로그 창 모서리 둥글게 설정
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 취소 버튼 클릭 리스너
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // 확인 버튼 클릭 리스너
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_confirm)
        val etReason = dialogView.findViewById<EditText>(R.id.et_fail_reason)

        btnConfirm.setOnClickListener {
            val reason = etReason.text.toString().trim()
            dialog.dismiss()

            // 미션 실패/포기 API 호출
            failMission(reason)
        }

        dialog.show()
    }

    // 미션 실패/포기 API 호출
    private fun failMission(reason: String) {
        val currentParticipationId = participationId ?: return Toast.makeText(
            context, "참여 ID가 없습니다.", Toast.LENGTH_SHORT
        ).show()

        lifecycleScope.launch {
            try {
                val result = missionRepository.failMission(currentParticipationId, reason)

                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(context, "미션을 포기했습니다.", Toast.LENGTH_SHORT).show()

                        // UI 업데이트
                        updateActionButton(isParticipated = true, "failed")

                        // 체크박스 비활성화
                        stepAdapter.setMissionStarted(false)
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(context, "미션 포기 처리 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    else -> { /* 로딩 처리 */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "미션 포기 처리 중 오류", e)
                Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}