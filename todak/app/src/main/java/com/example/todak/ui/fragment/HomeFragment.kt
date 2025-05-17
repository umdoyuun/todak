package com.example.todak.ui.fragment

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Observer
import com.example.todak.R
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.SetWeeklyBudgetRequest
import com.example.todak.data.model.WakeupInfoResponse
import com.example.todak.data.model.WeeklyBudgetResponse
import com.example.todak.data.repository.BudgetRepository
import com.example.todak.ui.activity.MainActivity
import com.example.todak.ui.custom.BudgetDonutChartView
import com.example.todak.util.BudgetUpdateEvent
import com.example.todak.util.SessionManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar

class HomeFragment : Fragment() {
    private val TAG = "HomeFragment"
    private val budgetRepository by lazy { BudgetRepository() }

    // =========== 예산 카드 뷰 참조 ===========
    // 카드뷰 전체
    private lateinit var cardBudget: CardView

    // 로딩/내용 레이아웃
    private lateinit var budgetLoadingLayout: View
    private lateinit var budgetContentLayout: View
    private lateinit var budgetNoInfoLayout: View

    // 콘텐츠 뷰
    private lateinit var tvBudgetDate: TextView
    private lateinit var tvUsagePercent: TextView
    private lateinit var tvStatusLabel: TextView
    private lateinit var donutChart: BudgetDonutChartView
    private lateinit var btnEditBudget: Button
    private lateinit var tvTargetBudget: TextView
    private lateinit var tvCurrentSpending: TextView
    private lateinit var tvDailyAvg: TextView
    private lateinit var tvRecommendedDaily: TextView

    // =========== 기상 정보 카드 뷰 참조 ===========
    //    // 카드뷰 전체
    private lateinit var cardWakeupInfo: CardView

    // 로딩/내용 레이아웃
    private lateinit var wakeupLoadingLayout: View
    private lateinit var wakeupNoInfoLayout: View
    private lateinit var wakeupContentLayout: View

    // 콘텐츠 뷰
    private lateinit var tvTargetDate: TextView
    private lateinit var tvWakeupMessage: TextView
    private lateinit var tvWakeupSummary: TextView
    private lateinit var tvWakeupDetail: TextView
    private lateinit var ivExpandIndicator: ImageView
    private var isWakeupInfoExpanded = false

    // =========== 다음 일정 카드 뷰 참조 ===========
    // 카드뷰 전체
    private lateinit var cardNextSchedule: CardView
    private var currentScheduleId: String? = null

    // 로딩/내용 레이아웃
    private lateinit var scheduleLoadingLayout: View
    private lateinit var scheduleNoInfoLayout: View
    private lateinit var scheduleContentLayout: View

    // 콘텐츠 뷰
    private lateinit var tvScheduleTitle: TextView
    private lateinit var tvScheduleCategory: TextView
    private lateinit var tvScheduleTime: TextView

    // 예산 설정 다이얼로그
    private lateinit var budgetDialog: Dialog

    private var isDailySpendingExpanded = false
    private lateinit var layoutDailySpending: LinearLayout


    // 요일별 카드뷰 참조
    private lateinit var cardMonday: LinearLayout
    private lateinit var cardTuesday: LinearLayout
    private lateinit var cardWednesday: LinearLayout
    private lateinit var cardThursday: LinearLayout
    private lateinit var cardFriday: LinearLayout
    private lateinit var cardSaturday: LinearLayout
    private lateinit var cardSunday: LinearLayout

    // 요일별 금액 텍스트뷰 참조
    private lateinit var tvMondayAmount: TextView
    private lateinit var tvTuesdayAmount: TextView
    private lateinit var tvWednesdayAmount: TextView
    private lateinit var tvThursdayAmount: TextView
    private lateinit var tvFridayAmount: TextView
    private lateinit var tvSaturdayAmount: TextView
    private lateinit var tvSundayAmount: TextView

    // 현재 실행 중인 데이터 로드 작업 추적
    private var dataLoadJob: Job? = null

    // 데이터 갱신 시간 추적
    private var lastDataRefreshTime = 0L
    private val DATA_REFRESH_INTERVAL = 60_000L // 1분마다 갱신

    // 숫자 포맷팅을 위한 객체
    private val decimalFormat = DecimalFormat("#,###")
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.KOREA)

    // 날짜 포맷팅을 위한 객체
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("M/d", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // 예외 처리를 위한 핸들러
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) {
            Log.d(TAG, "Coroutine was cancelled normally: ${throwable.message}")
        } else {
            Log.e(TAG, "데이터 로딩 중 오류 발생", throwable)
            activity?.runOnUiThread {
                showErrorState("데이터 로딩 중 오류가 발생했습니다")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        SessionManager.init(requireContext())

        // 사용자 이름을 가져와서 TextView에 설정
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val userName = SessionManager.getUserName() ?: "사용자" // 이름이 없을 경우 기본값 "사용자" 사용
        tvTitle.text = "${userName}님\n안녕하세요!"

        val cardSummary = view.findViewById<CardView>(R.id.card_summary)
        cardSummary.setOnClickListener {
            // Fragment 교체
            val summaryFragment = SummaryFragment()

            // Bundle로 데이터 전달이 필요한 경우
            val bundle = Bundle().apply {
                // 필요한 데이터를 추가하세요
                // putString("key", "value")
            }
            summaryFragment.arguments = bundle

            // Fragment 교체
            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_container, summaryFragment) // fragment_container는 실제 컨테이너 ID로 변경
                .addToBackStack(null) // 뒤로가기 버튼 지원
                .commit()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 세션 관리자 초기화
        SessionManager.init(requireContext())

        (activity as? MainActivity)?.setToolbarTitle("")

        // 사용자 이름 설정
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val userName = SessionManager.getUserName() ?: "사용자"
        tvTitle.text = "${userName}님\n안녕하세요!"

        // 뷰 참조 초기화 - 카드 뷰들을 직접 찾기
        findCardViews(view)

        // 각 카드 뷰 내부 요소 초기화
        initBudgetCardViews()
        initWakeupInfoCardViews()
        initNextScheduleCardViews()

        // 예산 수정 버튼 설정
        btnEditBudget.setOnClickListener {
            showSetBudgetDialog()
        }

        // 예산 설정 다이얼로그 초기화
        initBudgetDialog()

        // 초기 상태는 로딩 상태 표시 (콘텐츠는 숨김)
        showLoadingState()

        // 데이터 로드를 위한 코루틴 설정
        setupDataLoadingCoroutine()

        observeBudgetUpdateEvent()
    }

    // 카드뷰들을 직접 찾는 메소드
    private fun findCardViews(view: View) {
        cardBudget = view.findViewById(R.id.card_budget)
        cardWakeupInfo = view.findViewById(R.id.card_wakeup_info)
        cardNextSchedule = view.findViewById(R.id.card_next_schedule)
    }

    // 예산 카드뷰 내부 요소 초기화
    private fun initBudgetCardViews() {
        // 예산 카드 뷰 내부 요소 찾기
        budgetLoadingLayout = cardBudget.findViewById(R.id.budget_loading_layout)
        budgetContentLayout = cardBudget.findViewById(R.id.budget_content_layout)
        budgetNoInfoLayout = cardBudget.findViewById(R.id.budget_no_budget_layout)

        tvBudgetDate = cardBudget.findViewById(R.id.tv_budget_date)
        tvUsagePercent = cardBudget.findViewById(R.id.tv_usage_percent)
        tvStatusLabel = cardBudget.findViewById(R.id.tv_status_label)
        donutChart = cardBudget.findViewById(R.id.donut_chart)
        btnEditBudget = cardBudget.findViewById(R.id.btn_edit_budget)

        tvTargetBudget = cardBudget.findViewById(R.id.tv_target_budget)
        tvCurrentSpending = cardBudget.findViewById(R.id.tv_current_spending)
        tvDailyAvg = cardBudget.findViewById(R.id.tv_daily_avg)
        tvRecommendedDaily = cardBudget.findViewById(R.id.tv_recommended_daily)

        // 도넛 차트 초기화
        donutChart.reset()

        // 카드뷰 클릭 이벤트 설정
        cardBudget.setOnClickListener {
            if (budgetNoInfoLayout.isVisible) {
                showSetBudgetDialog()
            }
        }

        initDailySpendingViews()
    }

    // 기상 정보 카드뷰 내부 요소 초기화
    private fun initWakeupInfoCardViews() {
        wakeupLoadingLayout = cardWakeupInfo.findViewById(R.id.wakeup_loading_layout)
        wakeupNoInfoLayout = cardWakeupInfo.findViewById(R.id.wakeup_no_info_layout)
        wakeupContentLayout = cardWakeupInfo.findViewById(R.id.wakeup_content_layout)

        tvTargetDate = cardWakeupInfo.findViewById(R.id.tv_target_date)
        tvWakeupSummary = cardWakeupInfo.findViewById(R.id.tv_wakeup_summary)
        tvWakeupDetail = cardWakeupInfo.findViewById(R.id.tv_wakeup_detail)
        ivExpandIndicator = cardWakeupInfo.findViewById(R.id.iv_expand_indicator)

        // 카드뷰 클릭 이벤트 설정
        cardWakeupInfo.setOnClickListener {
            if (wakeupContentLayout.isVisible) {
                toggleWakeupInfoDetail()
            }
        }
    }


    // 다음 일정 카드뷰 내부 요소 초기화
    private fun initNextScheduleCardViews() {
        scheduleLoadingLayout = cardNextSchedule.findViewById(R.id.schedule_loading_layout)
        scheduleNoInfoLayout = cardNextSchedule.findViewById(R.id.schedule_no_info_layout)
        scheduleContentLayout = cardNextSchedule.findViewById(R.id.schedule_content_layout)

        tvScheduleTitle = cardNextSchedule.findViewById(R.id.tv_schedule_title)
        tvScheduleCategory = cardNextSchedule.findViewById(R.id.tv_schedule_category)
        tvScheduleTime = cardNextSchedule.findViewById(R.id.tv_schedule_time)

        cardNextSchedule.setOnClickListener {
            // 내용이 표시되어 있고, 일정이 있는 경우에만 처리
            if (scheduleContentLayout.isVisible) {
                // currentScheduleId가 있는 경우 ScheduleFragment로 이동
                currentScheduleId?.let { scheduleId ->
                    navigateToScheduleDetail(scheduleId)
                }
            }
        }
    }

    // 예산 업데이트 이벤트 관찰 설정
    private fun observeBudgetUpdateEvent() {
        BudgetUpdateEvent.updateEvent.observe(viewLifecycleOwner, Observer { timestamp ->
            Log.d(TAG, "예산 업데이트 이벤트 수신: $timestamp")
            // 이벤트 수신 시 예산 데이터 갱신
            refreshData()
        })
    }

    private fun initBudgetDialog() {
        budgetDialog = Dialog(requireContext())
        budgetDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        budgetDialog.setContentView(R.layout.modal_set_budget)

        // 다이얼로그 배경을 투명하게 설정
        budgetDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 다이얼로그 바깥 클릭 시 닫히도록 설정
        budgetDialog.setCanceledOnTouchOutside(true)

        // 전체 창 크기 조절
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(budgetDialog.window?.attributes)
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.gravity = Gravity.CENTER
        budgetDialog.window?.attributes = layoutParams

        // 취소 버튼 설정
        budgetDialog.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            budgetDialog.dismiss()
        }

        // 저장 버튼 설정
        budgetDialog.findViewById<Button>(R.id.btn_save).setOnClickListener {
            val etBudgetAmount = budgetDialog.findViewById<TextInputEditText>(R.id.et_budget_amount)
            val amountStr = etBudgetAmount.text.toString().replace(",", "")

            if (amountStr.isBlank()) {
                Toast.makeText(context, "금액을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val amount = amountStr.toInt()
                if (amount <= 0) {
                    Toast.makeText(context, "0보다 큰 금액을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                saveBudget(amount)
            } catch (e: NumberFormatException) {
                Toast.makeText(context, "올바른 금액을 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }

        // 입력 필드에 콤마 자동 추가
        val etBudgetAmount = budgetDialog.findViewById<TextInputEditText>(R.id.et_budget_amount)
        etBudgetAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) return

                etBudgetAmount.removeTextChangedListener(this)

                val cleanString = s.toString().replace("[^\\d]".toRegex(), "")
                if (cleanString.isNotEmpty()) {
                    try {
                        val parsed = cleanString.toDouble()
                        val formatted = DecimalFormat("#,###").format(parsed)
                        etBudgetAmount.setText(formatted)
                        etBudgetAmount.setSelection(formatted.length)
                    } catch (e: NumberFormatException) {
                        // 오류 처리
                    }
                }

                etBudgetAmount.addTextChangedListener(this)
            }
        })
    }

    private fun showSetBudgetDialog() {
        // 입력 필드 초기화
        val etBudgetAmount = budgetDialog.findViewById<TextInputEditText>(R.id.et_budget_amount)
        etBudgetAmount.setText("")

        // 다이얼로그 표시
        budgetDialog.show()
    }

    private fun saveBudget(amount: Int) {
        // 로딩 상태 표시
        val progressBar = budgetDialog.findViewById<ProgressBar>(R.id.progress_bar)
        val btnSave = budgetDialog.findViewById<Button>(R.id.btn_save)
        val btnCancel = budgetDialog.findViewById<Button>(R.id.btn_cancel)
        val tilBudgetAmount = budgetDialog.findViewById<TextInputLayout>(R.id.til_budget_amount)

        progressBar.isVisible = true
        btnSave.isEnabled = false
        btnCancel.isEnabled = false
        tilBudgetAmount.isEnabled = false

        // 예산 저장 API 호출
        lifecycleScope.launch(exceptionHandler) {
            try {
                val result = withContext(Dispatchers.IO) {
                    budgetRepository.setWeeklyBudget(amount)
                }

                withContext(Dispatchers.Main) {
                    progressBar.isVisible = false
                    btnSave.isEnabled = true
                    btnCancel.isEnabled = true
                    tilBudgetAmount.isEnabled = true

                    when (result) {
                        is NetworkResult.Success -> {
                            Toast.makeText(context, "예산이 성공적으로 설정되었습니다", Toast.LENGTH_SHORT).show()
                            budgetDialog.dismiss()

                            // 예산 정보 새로고침
                            refreshData()
                        }
                        is NetworkResult.Error -> {
                            Toast.makeText(context, "예산 설정 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                        }
                        is NetworkResult.Loading -> {
                            // 로딩 상태 처리
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.isVisible = false
                    btnSave.isEnabled = true
                    btnCancel.isEnabled = true
                    tilBudgetAmount.isEnabled = true

                    Toast.makeText(context, "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleWakeupInfoDetail() {
        isWakeupInfoExpanded = !isWakeupInfoExpanded

        // 상세 정보 표시/숨김
        tvWakeupDetail.isVisible = isWakeupInfoExpanded

        val rotation = if (isWakeupInfoExpanded) 180f else 0f
        ivExpandIndicator.rotation = rotation
    }

    private fun setupDataLoadingCoroutine() {
        // 기존 작업 취소
        dataLoadJob?.cancel()

        // 새로운 코루틴 시작 - viewLifecycleOwner의 생명주기에 따라 관리
        dataLoadJob = lifecycleScope.launch(exceptionHandler) {
            // 중요: repeatOnLifecycle을 사용하여 STARTED 상태일 때만 코루틴 실행
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 데이터 로드
                fetchData()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 마지막 갱신 이후 충분한 시간이 지났으면 데이터 새로고침
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDataRefreshTime > DATA_REFRESH_INTERVAL) {
            // 새로운 데이터 로드 트리거
            refreshData()
        }
    }

    // 일반 함수에서 호출할 수 있는 데이터 갱신 함수
    fun refreshData() {
        lifecycleScope.launch(exceptionHandler) {
            fetchData()
        }
    }

    // 실제 데이터를 가져오는 suspend 함수
    private suspend fun fetchData() {
        try {
            // 로딩 상태 표시
            withContext(Dispatchers.Main) {
                showLoadingState()
            }

            // IO 스레드에서 새로운 기상 정보 API 호출
            val result = withContext(Dispatchers.IO) {
                budgetRepository.getWakeupInfo()
            }

            // 메인 스레드에서 UI 업데이트
            withContext(Dispatchers.Main) {
                handleWakeupInfoResult(result)
                lastDataRefreshTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Log.e(TAG, "데이터 로딩 중 예외 발생", e)
            withContext(Dispatchers.Main) {
                showErrorState("오류 발생: ${e.message}")
            }
        }
    }

    private fun handleWakeupInfoResult(result: NetworkResult<WakeupInfoResponse>) {
        when (result) {
            is NetworkResult.Success -> {
                val wakeupInfoData = result.data
                Log.d(TAG, "$wakeupInfoData")

                // 예산 상태 업데이트
                updateBudgetUI(wakeupInfoData.budget_status, wakeupInfoData.daily_spending)

                // 기상 정보 업데이트
                updateWakeupInfoUI(wakeupInfoData)

                // 다음 일정 업데이트
                updateScheduleUI(wakeupInfoData)
            }
            is NetworkResult.Error -> {
                Log.e(TAG, "데이터 로딩 실패: ${result.message}")
                showErrorState("데이터를 불러오는데 실패했습니다")
            }
            is NetworkResult.Loading -> {
                showLoadingState()
            }
        }
    }


    private fun updateWakeupInfoUI(wakeupInfoData: WakeupInfoResponse) {
        try {
            if (!wakeupInfoData.has_info) {
                // 기상 정보가 없는 경우
                wakeupLoadingLayout.isVisible = false
                wakeupNoInfoLayout.isVisible = true
                wakeupContentLayout.isVisible = false
                return
            }

            // 기상 정보 표시
            tvTargetDate.text = wakeupInfoData.target_date

            // 메시지를 처리하여 요약과 상세 부분으로 나눕니다
            val fullMessage = wakeupInfoData.message
            val recommendedTime = wakeupInfoData.recommended_time

            // 추천 시간 메시지 생성
            val summaryMessage = "내일 추천 기상 시간은 ${recommendedTime}입니다."

            // 전체 메시지에서 요약 메시지 부분을 제거
            var detailMessage = fullMessage
            if (fullMessage.contains(summaryMessage)) {
                detailMessage = fullMessage.replace(summaryMessage, "").trim()
            }

            // 상세 메시지가 비어있으면 공백으로 만들기
            if (detailMessage.isBlank()) {
                detailMessage = ""
            }

            // TextView에 설정
            tvWakeupSummary.text = summaryMessage
            tvWakeupDetail.text = detailMessage
            tvWakeupDetail.isVisible = isWakeupInfoExpanded && detailMessage.isNotEmpty()

            // 초기 상태는 접힌 상태
            tvWakeupDetail.isVisible = isWakeupInfoExpanded

            // 기상 정보 영역 표시
            wakeupLoadingLayout.isVisible = false
            wakeupNoInfoLayout.isVisible = false
            wakeupContentLayout.isVisible = true
        } catch (e: Exception) {
            Log.e(TAG, "기상 정보 업데이트 중 오류 발생", e)
            wakeupLoadingLayout.isVisible = false
            wakeupNoInfoLayout.isVisible = true
            wakeupContentLayout.isVisible = false
        }
    }

    private fun updateScheduleUI(wakeupInfoData: WakeupInfoResponse) {
        try {
            if (!wakeupInfoData.has_schedule || wakeupInfoData.next_schedule == null) {
                // 일정 정보가 없는 경우
                scheduleLoadingLayout.isVisible = false
                scheduleNoInfoLayout.isVisible = true
                scheduleContentLayout.isVisible = false
                return
            }

            // 다음 일정 정보 표시
            val nextSchedule = wakeupInfoData.next_schedule
            tvScheduleTitle.text = nextSchedule.title
            tvScheduleCategory.text = nextSchedule.category
            tvScheduleTime.text = nextSchedule.start_time

            currentScheduleId = nextSchedule.schedule_id

            // 일정 정보 영역 표시
            scheduleLoadingLayout.isVisible = false
            scheduleNoInfoLayout.isVisible = false
            scheduleContentLayout.isVisible = true
        } catch (e: Exception) {
            Log.e(TAG, "일정 정보 업데이트 중 오류 발생", e)
            scheduleLoadingLayout.isVisible = false
            scheduleNoInfoLayout.isVisible = true
            scheduleContentLayout.isVisible = false
            currentScheduleId = null
        }
    }

    private fun navigateToScheduleDetail(scheduleId: String) {
        // MainActivity 인스턴스 가져오기
        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            mainActivity.navigateToScheduleWithId(scheduleId)
        }
    }

    private fun updateBudgetUI(budgetData: WeeklyBudgetResponse, dailySpending: Map<String, Int>) {
        try {
            if (!budgetData.has_budget) {
                showNoBudgetState()
                return
            }

            // 날짜 표시
            val startDate = dateFormat.parse(budgetData.start_date)
            val endDate = dateFormat.parse(budgetData.end_date)
            if (startDate != null && endDate != null) {
                val dateRangeText = "${displayDateFormat.format(startDate)} ~ ${displayDateFormat.format(endDate)}"
                tvBudgetDate.text = dateRangeText
            }

            // 도넛 차트 업데이트
            donutChart.setUsagePercent(budgetData.usage_percent)

            // 사용률 텍스트 업데이트
            tvUsagePercent.text = "${budgetData.usage_percent}%"

            // 사용률 라벨을 status 값으로 변경
            tvStatusLabel.text = budgetData.status

            // 사용률에 따라 텍스트 색상 변경
            context?.let { ctx ->
                val colorRes = when (budgetData.status) {
                    "절약 중" -> R.color.mintgreen
                    "계획대로" -> R.color.darkgreen
                    "조금 빠르게 사용 중" -> R.color.orange
                    "예산 초과" -> R.color.red
                    else -> R.color.mintgreen  // 기본값
                }
                tvUsagePercent.setTextColor(ctx.getColor(colorRes))
                tvStatusLabel.setTextColor(ctx.getColor(colorRes))

                // 현재 지출이 예산을 초과하면 텍스트 색상 변경
                tvCurrentSpending.setTextColor(
                    if (budgetData.current_spending > budgetData.total_budget)
                        ctx.getColor(R.color.red) else ctx.getColor(R.color.black)
                )
            }

            // 금액 정보 업데이트
            tvTargetBudget.text = "₩${decimalFormat.format(budgetData.total_budget)}"
            tvCurrentSpending.text = "₩${decimalFormat.format(budgetData.current_spending)}"
            tvDailyAvg.text = "₩${decimalFormat.format(budgetData.daily_avg_spent)}"
            tvRecommendedDaily.text = "₩${decimalFormat.format(budgetData.recommended_daily_budget)}"

            // 요일별 지출 정보 업데이트
            updateDailySpendingUI(dailySpending, budgetData.total_budget)

            // 예산 카드 표시
            budgetLoadingLayout.isVisible = false
            budgetNoInfoLayout.isVisible = false
            budgetContentLayout.isVisible = true

        } catch (e: Exception) {
            Log.e(TAG, "데이터 파싱 중 오류 발생", e)
            showErrorState("데이터 형식 오류")
        }
    }

    private fun updateDailySpendingUI(dailySpending: Map<String, Int>, targetBudget: Int) {
        context?.let { ctx ->
            // 현재 요일 구하기 (1=일요일, 7=토요일을 한국 요일 체계로 변환)
            val calendar = Calendar.getInstance()
            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

            // Calendar.DAY_OF_WEEK는 일요일=1, 토요일=7이므로 한국식(월=1, 일=7)으로 변환
            val koreanDayOfWeek = when(currentDayOfWeek) {
                Calendar.SUNDAY -> 7    // 일요일
                else -> currentDayOfWeek - 1  // 월~토
            }

            val targetDailyBudget = targetBudget / 7

            // 요일별 금액 설정 및 스타일 적용
            updateDayUI(tvMondayAmount, cardMonday, dailySpending.getOrDefault("월요일", 0), targetDailyBudget, 1 <= koreanDayOfWeek)
            updateDayUI(tvTuesdayAmount, cardTuesday, dailySpending.getOrDefault("화요일", 0), targetDailyBudget, 2 <= koreanDayOfWeek)
            updateDayUI(tvWednesdayAmount, cardWednesday, dailySpending.getOrDefault("수요일", 0), targetDailyBudget, 3 <= koreanDayOfWeek)
            updateDayUI(tvThursdayAmount, cardThursday, dailySpending.getOrDefault("목요일", 0), targetDailyBudget, 4 <= koreanDayOfWeek)
            updateDayUI(tvFridayAmount, cardFriday, dailySpending.getOrDefault("금요일", 0), targetDailyBudget, 5 <= koreanDayOfWeek)
            updateDayUI(tvSaturdayAmount, cardSaturday, dailySpending.getOrDefault("토요일", 0), targetDailyBudget, 6 <= koreanDayOfWeek)
            updateDayUI(tvSundayAmount, cardSunday, dailySpending.getOrDefault("일요일", 0), targetDailyBudget, 7 <= koreanDayOfWeek)
        }
    }

    private fun updateDayUI(textView: TextView, cardView: LinearLayout, amount: Int, dailyBudget: Int, isPast: Boolean) {
        // 금액 설정
        textView.text = "₩${decimalFormat.format(amount)}"

        context?.let { ctx ->
            if (isPast) {
                // 지출이 기준 예산의 ±10% 내에 있는지 확인
                val lowerThreshold = dailyBudget * 0.9  // 기준에서 10% 아래
                val upperThreshold = dailyBudget * 1.1  // 기준에서 10% 위

                // 색상 결정 (범위 내=오렌지, 초과=빨강, 미만=초록)
                val colorRes = when {
                    amount > upperThreshold -> R.color.red        // 10% 초과 = 빨강
                    amount < lowerThreshold -> R.color.green      // 10% 미만 = 초록
                    else -> R.color.orange                        // 10% 내외 = 오렌지
                }

                val color = ctx.getColor(colorRes)

                // 금액 텍스트뷰 색상 변경
                textView.setTextColor(color)

                // 요일 텍스트뷰 찾아서 같은 색상으로 변경
                if (cardView.childCount > 0) {
                    val dayTextView = cardView.getChildAt(0) as? TextView
                    dayTextView?.setTextColor(color)
                }

                // 카드 배경을 활성화 상태로
                cardView.alpha = 1.0f
            } else {
                // 미래 날짜는 비활성화 상태로
                val grayColor = ctx.getColor(R.color.darkgray)
                textView.setTextColor(grayColor)

                // 요일 텍스트뷰도 회색으로 설정
                if (cardView.childCount > 0) {
                    val dayTextView = cardView.getChildAt(0) as? TextView
                    dayTextView?.setTextColor(grayColor)
                }

                cardView.alpha = 0.5f
            }
        }
    }

    private fun initDailySpendingViews() {
        // 레이아웃 참조 가져오기
        layoutDailySpending = cardBudget.findViewById(R.id.layout_daily_spending)

        // 요일별 카드뷰 참조
        cardMonday = cardBudget.findViewById(R.id.card_monday)
        cardTuesday = cardBudget.findViewById(R.id.card_tuesday)
        cardWednesday = cardBudget.findViewById(R.id.card_wednesday)
        cardThursday = cardBudget.findViewById(R.id.card_thursday)
        cardFriday = cardBudget.findViewById(R.id.card_friday)
        cardSaturday = cardBudget.findViewById(R.id.card_saturday)
        cardSunday = cardBudget.findViewById(R.id.card_sunday)

        // 요일별 금액 텍스트뷰 참조
        tvMondayAmount = cardBudget.findViewById(R.id.tv_monday_amount)
        tvTuesdayAmount = cardBudget.findViewById(R.id.tv_tuesday_amount)
        tvWednesdayAmount = cardBudget.findViewById(R.id.tv_wednesday_amount)
        tvThursdayAmount = cardBudget.findViewById(R.id.tv_thursday_amount)
        tvFridayAmount = cardBudget.findViewById(R.id.tv_friday_amount)
        tvSaturdayAmount = cardBudget.findViewById(R.id.tv_saturday_amount)
        tvSundayAmount = cardBudget.findViewById(R.id.tv_sunday_amount)

        // 카드뷰 클릭 이벤트 수정
        cardBudget.setOnClickListener {
            if (budgetNoInfoLayout.isVisible) {
                showSetBudgetDialog()
            } else if (budgetContentLayout.isVisible) {
                // 이미 펼쳐져 있으면 접기, 아니면 펼치기
                toggleDailySpending(!isDailySpendingExpanded)
            }
        }
    }

    private fun toggleDailySpending(expand: Boolean) {
        isDailySpendingExpanded = expand

        // 요일별 지출 내역 레이아웃 표시/숨김
        layoutDailySpending.isVisible = isDailySpendingExpanded

    }


    private fun showNoBudgetState() {
        budgetLoadingLayout.isVisible = false
        budgetContentLayout.isVisible = false
        budgetNoInfoLayout.isVisible = true
    }

    // 로딩 상태 표시
    private fun showLoadingState() {
        // 예산 카드 로딩 표시
        budgetLoadingLayout.isVisible = true
        budgetContentLayout.isVisible = false
        budgetNoInfoLayout.isVisible = false

        // 기상 정보 카드 로딩 표시
        wakeupLoadingLayout.isVisible = true
        wakeupContentLayout.isVisible = false
        wakeupNoInfoLayout.isVisible = false

        // 일정 정보 카드 로딩 표시
        scheduleLoadingLayout.isVisible = true
        scheduleContentLayout.isVisible = false
        scheduleNoInfoLayout.isVisible = false
    }

    // 오류 상태 표시
    private fun showErrorState(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        // 예산 카드 오류 표시
        budgetLoadingLayout.isVisible = false
        budgetContentLayout.isVisible = false
        budgetNoInfoLayout.isVisible = true

        // 기상 정보 카드 오류 표시
        wakeupLoadingLayout.isVisible = false
        wakeupContentLayout.isVisible = false
        wakeupNoInfoLayout.isVisible = true

        // 일정 정보 카드 오류 표시
        scheduleLoadingLayout.isVisible = false
        scheduleContentLayout.isVisible = false
        scheduleNoInfoLayout.isVisible = true

        // 도넛 차트 리셋
        donutChart.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        dataLoadJob?.cancel()
        if (::budgetDialog.isInitialized && budgetDialog.isShowing) {
            budgetDialog.dismiss()
        }
    }
}