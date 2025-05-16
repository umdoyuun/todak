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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

class HomeFragment : Fragment() {
    private val TAG = "HomeFragment"
    private val budgetRepository by lazy { BudgetRepository() }

    // 뷰 참조
    private lateinit var budgetLoadingLayout: View
    private lateinit var budgetContentLayout: View
    private lateinit var tvBudgetDate: TextView
    private lateinit var tvUsagePercent: TextView
    private lateinit var tvStatusLabel: TextView
    private lateinit var tvTargetBudget: TextView
    private lateinit var tvCurrentSpending: TextView
    private lateinit var tvDailyAvg: TextView
    private lateinit var donutChart: BudgetDonutChartView
    private lateinit var btnEditBudget: Button

    // 예산 설정 다이얼로그
    private lateinit var budgetDialog: Dialog

    // 현재 실행 중인 데이터 로드 작업 추적
    private var budgetLoadJob: Job? = null

    // 데이터 갱신 시간 추적
    private var lastDataRefreshTime = 0L
    private val DATA_REFRESH_INTERVAL = 60_000L // 1분마다 갱신

    // 숫자 포맷팅을 위한 객체
    private val decimalFormat = DecimalFormat("#,###")
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.KOREA)

    // 날짜 포맷팅을 위한 객체
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("M/d", Locale.getDefault())

    // 예외 처리를 위한 핸들러
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) {
            Log.d(TAG, "Coroutine was cancelled normally: ${throwable.message}")
        } else {
            Log.e(TAG, "예산 데이터 로딩 중 오류 발생", throwable)
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

        // 사용자 이름 설정
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val userName = SessionManager.getUserName() ?: "사용자"
        tvTitle.text = "${userName}님\n안녕하세요!"

        // 뷰 참조 초기화
        initViewReferences(view)

        // 예산 수정 버튼 설정
        btnEditBudget = view.findViewById(R.id.btn_edit_budget)
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

    // 예산 업데이트 이벤트 관찰 설정
    private fun observeBudgetUpdateEvent() {
        BudgetUpdateEvent.updateEvent.observe(viewLifecycleOwner, Observer { timestamp ->
            Log.d(TAG, "예산 업데이트 이벤트 수신: $timestamp")
            // 이벤트 수신 시 예산 데이터 갱신
            refreshBudgetData()
        })
    }

    private fun initViewReferences(view: View) {
        // 로딩 관련 뷰
        budgetLoadingLayout = view.findViewById(R.id.budget_loading_layout)
        budgetContentLayout = view.findViewById(R.id.budget_content_layout)

        // 콘텐츠 뷰
        tvBudgetDate = view.findViewById(R.id.tv_budget_date)
        tvUsagePercent = view.findViewById(R.id.tv_usage_percent)
        tvStatusLabel = view.findViewById(R.id.tv_status_label)  // XML에서 추가해야 합니다
        val cardTargetBudget = view.findViewById<CardView>(R.id.card_target_budget)
        cardTargetBudget.findViewById<TextView>(R.id.tv_card_title).text = "목표 예산"
        tvTargetBudget = cardTargetBudget.findViewById(R.id.tv_card_value)
        val cardCurrentSpending = view.findViewById<CardView>(R.id.card_current_spending)
        cardCurrentSpending.findViewById<TextView>(R.id.tv_card_title).text = "현재 지출"
        tvCurrentSpending = cardCurrentSpending.findViewById(R.id.tv_card_value)
        val cardDailyAvg = view.findViewById<CardView>(R.id.card_daily_avg)
        cardDailyAvg.findViewById<TextView>(R.id.tv_card_title).text = "일 평균"

        tvDailyAvg = cardDailyAvg.findViewById(R.id.tv_card_value)
        donutChart = view.findViewById(R.id.donut_chart)

        // 중요: 도넛 차트 초기화
        donutChart.reset()
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
                            refreshBudgetData()
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


    private fun setupDataLoadingCoroutine() {
        // 기존 작업 취소
        budgetLoadJob?.cancel()

        // 새로운 코루틴 시작 - viewLifecycleOwner의 생명주기에 따라 관리
        budgetLoadJob = lifecycleScope.launch(exceptionHandler) {
            // 중요: repeatOnLifecycle을 사용하여 STARTED 상태일 때만 코루틴 실행
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 데이터 로드
                fetchBudgetData()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 마지막 갱신 이후 충분한 시간이 지났으면 데이터 새로고침
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDataRefreshTime > DATA_REFRESH_INTERVAL) {
            // 새로운 데이터 로드 트리거 - 일반 함수에서는 suspend 함수를 직접 호출할 수 없으므로
            // 새 코루틴을 시작하여 호출
            refreshBudgetData()
        }
    }

    // 일반 함수에서 호출할 수 있는 데이터 갱신 함수
    fun refreshBudgetData() {
        lifecycleScope.launch(exceptionHandler) {
            fetchBudgetData()
        }
    }

    // 실제 데이터를 가져오는 suspend 함수
    private suspend fun fetchBudgetData() {
        try {
            // 로딩 상태 표시
            withContext(Dispatchers.Main) {
                showLoadingState()
            }

            // IO 스레드에서 네트워크 작업 수행
            val result = withContext(Dispatchers.IO) {
                budgetRepository.getWeeklyBudget()
            }

            // 메인 스레드에서 UI 업데이트
            withContext(Dispatchers.Main) {
                handleBudgetResult(result)
                lastDataRefreshTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            Log.e(TAG, "예산 정보 로딩 중 예외 발생", e)
            withContext(Dispatchers.Main) {
                showErrorState("오류 발생: ${e.message}")
            }
        }
    }

    private fun handleBudgetResult(result: NetworkResult<WeeklyBudgetResponse>) {
        when (result) {
            is NetworkResult.Success -> {
                val budgetData = result.data

                if (budgetData.has_budget) {
                    updateBudgetUI(budgetData)
                    showContentState()
                } else {
                    showNoBudgetState()
                }
            }
            is NetworkResult.Error -> {
                Log.e(TAG, "예산 정보 로딩 실패: ${result.message}")
                showErrorState("데이터를 불러오는데 실패했습니다")
            }
            is NetworkResult.Loading -> {
                showLoadingState()
            }
        }
    }

    private fun showNoBudgetState() {
        budgetLoadingLayout.isVisible = false
        budgetContentLayout.isVisible = false

        // Show the no budget layout
        val noBudgetLayout = view?.findViewById<View>(R.id.budget_no_budget_layout)
        noBudgetLayout?.isVisible = true

        // Make the entire card clickable to show budget dialog
        val cardBudget = view?.findViewById<CardView>(R.id.card_budget)
        cardBudget?.setOnClickListener {
            showSetBudgetDialog()
        }
    }

    private fun updateBudgetUI(budgetData: WeeklyBudgetResponse) {
        try {
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
                val colorRes = if (budgetData.usage_percent > 100) R.color.red else R.color.mintgreen
                tvUsagePercent.setTextColor(ctx.getColor(colorRes))
                tvStatusLabel.setTextColor(ctx.getColor(colorRes))

                // 현재 지출이 예산을 초과하면 텍스트 색상 변경
                tvCurrentSpending.setTextColor(
                    if (budgetData.current_spending > budgetData.total_budget)
                        ctx.getColor(R.color.red) else ctx.getColor(R.color.black)
                )
            }

            // 금액 정보 업데이트
            tvTargetBudget.text = "${decimalFormat.format(budgetData.total_budget)}원"
            tvCurrentSpending.text = "₩${decimalFormat.format(budgetData.current_spending)}원"
            tvDailyAvg.text = "₩${decimalFormat.format(budgetData.daily_avg_spent)}원"

        } catch (e: Exception) {
            Log.e(TAG, "데이터 파싱 중 오류 발생", e)
            showErrorState("데이터 형식 오류")
        }
    }

    // 로딩 상태 표시
    private fun showLoadingState() {
        budgetLoadingLayout.isVisible = true
        budgetContentLayout.isVisible = false
    }

    // 콘텐츠 상태 표시
    private fun showContentState() {
        budgetLoadingLayout.isVisible = false
        budgetContentLayout.isVisible = true
    }

    // 비어있는 상태 표시
    private fun showEmptyState(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        budgetLoadingLayout.isVisible = false
        budgetContentLayout.isVisible = false
    }

    // 오류 상태 표시
    private fun showErrorState(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        // 오류 상태에서는 로딩 레이아웃을 숨기고, 콘텐츠 레이아웃에 기본값 표시
        budgetLoadingLayout.isVisible = false
        budgetContentLayout.isVisible = true

        // 기본값으로 리셋
        tvBudgetDate.text = ""
        tvUsagePercent.text = "0%"
        tvStatusLabel.text = "-"
        tvTargetBudget.text = "₩0"
        tvCurrentSpending.text = "₩0"
        tvDailyAvg.text = "₩0"
        donutChart.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        budgetLoadJob?.cancel()
        if (::budgetDialog.isInitialized && budgetDialog.isShowing) {
            budgetDialog.dismiss()
        }
    }
}