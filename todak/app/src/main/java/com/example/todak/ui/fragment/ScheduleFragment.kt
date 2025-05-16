package com.example.todak.ui.fragment

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.ScheduleItem
import com.example.todak.data.model.ScheduleRepeatInfo
import com.example.todak.data.model.ScheduleRequest
import com.example.todak.data.model.ScheduleStatus
import com.example.todak.data.repository.ScheduleRepository
import com.example.todak.ui.activity.MainActivity
import com.example.todak.ui.adapter.ScheduleAdapter
import com.example.todak.ui.modal.EmotionCheckModal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleFragment : Fragment() {

    private lateinit var tvDateHeader: TextView
    private lateinit var rvSchedule: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var btnAddSchedule: Button

    private lateinit var scheduleAdapter: ScheduleAdapter
    private val scheduleRepository = ScheduleRepository()
    private val TAG = "ScheduleFragment"

    // 모달 다이얼로그
    private var scheduleDetailDialog: Dialog? = null
    private var scheduleAddDialog: Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 타이틀 설정 (필요한 경우)
        (activity as? MainActivity)?.setToolbarTitle("일정")

        // 뷰 초기화
        tvDateHeader = view.findViewById(R.id.tv_date_header)
        rvSchedule = view.findViewById(R.id.rv_schedule)
        progressBar = view.findViewById(R.id.progress_bar)
        tvEmptyState = view.findViewById(R.id.tv_empty_state)
        btnAddSchedule = view.findViewById(R.id.btn_add_schedule)

        setupDateHeader()
        setupRecyclerView()
        fetchTodaySchedules()

        // 일정 추가 버튼 클릭 리스너 설정
        btnAddSchedule.setOnClickListener {
            showAddScheduleModal()
        }

        arguments?.let { args ->
            val scheduleId = args.getString("schedule_id")
            val action = args.getString("action")
            val showDetail = args.getBoolean("show_detail", false)

            if (showDetail && scheduleId != null) {
                // 일정 데이터 로드 후 상세 정보 표시
                fetchTodaySchedules()

                // 일정 목록 로드 완료 후 상세 정보 모달 표시를 위한 리스너 설정
                lifecycleScope.launch {
                    when (val result = scheduleRepository.getTodaySchedules()) {
                        is NetworkResult.Success -> {
                            val schedules = result.data
                            val schedule = schedules.find { it.id == scheduleId }

                            schedule?.let {
                                // 상세 정보 모달 표시
                                showScheduleDetailModal(it)

                                // 액션 수행 (시작 또는 완료)
                                when (action) {
                                    "start" -> handleScheduleAction(scheduleId, "start")
                                    "complete" -> handleScheduleAction(scheduleId, "complete")
                                    else -> {}
                                }
                            }
                        }
                        else -> {
                        }
                    }
                }
            } else {
                // 일반적인 일정 목록 표시
                fetchTodaySchedules()
            }
        } ?: run {
            fetchTodaySchedules()
        }
    }

    // 일정 액션 처리 함수
    private fun handleScheduleAction(scheduleId: String, action: String) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE

            val result = when (action) {
                "start" -> scheduleRepository.startSchedule(scheduleId)
                "complete" -> scheduleRepository.completeSchedule(scheduleId)
                "postpone" -> scheduleRepository.postponeSchedule(scheduleId)
                else -> null
            }

            result?.let {
                when (it) {
                    is NetworkResult.Success -> {
                        val actionName = when (action) {
                            "start" -> "시작"
                            "complete" -> "완료"
                            "postpone" -> "연기"
                            else -> "처리"
                        }
                        Toast.makeText(requireContext(), "일정이 ${actionName}되었습니다", Toast.LENGTH_SHORT).show()

                        Log.d(TAG, "일정 ${action} 성공: ${it.data}")
                        val newStatus = when (it.data.status) {
                            "pending" -> ScheduleStatus.NOT_STARTED
                            "started" -> ScheduleStatus.IN_PROGRESS
                            "completed" -> ScheduleStatus.COMPLETED
                            "postponed" -> ScheduleStatus.POSTPONED
                            else -> ScheduleStatus.NOT_STARTED
                        }

                        // 현재 목록에서 해당 일정 찾아 상태 업데이트
                        val currentList = scheduleAdapter.currentList.toMutableList()
                        val itemIndex = currentList.indexOfFirst { item -> item.id == scheduleId }

                        if (itemIndex != -1) {
                            // 기존 항목을 상태만 업데이트된 새 항목으로 교체
                            val updatedItem = currentList[itemIndex].copy(
                                status = newStatus,
                                isCompleted = newStatus == ScheduleStatus.COMPLETED
                            )
                            currentList[itemIndex] = updatedItem

                            // 어댑터에 새 목록 적용
                            scheduleAdapter.submitList(currentList)
                        }

                        // 상태 변경 후 모달 다이얼로그 업데이트 (닫지 않음)
                        scheduleDetailDialog?.let { dialog ->
                            updateScheduleDetailModalStatus(dialog, newStatus)
                        }

                        // 일정이 완료되었을 시 모달 표시
                        if(action == "complete") {
                            val scheduleItem = scheduleAdapter.currentList.find { it.id == scheduleId }

                            scheduleItem?.let {
                                val context = "after_schedule:${it.title}"
                                val message = "${it.title} 일정을 완료했어요! 감정은 어땠나요?"
                                showEmotionCheckModal(context, message)
                            }
                        }

                        // 서버 상태가 UI에 완전히 반영되도록 약간의 지연 후 데이터 새로고침
                        delay(300) // 300ms 지연
                        fetchTodaySchedules()

                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), "일정 ${action} 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                    is NetworkResult.Loading -> {
                        // 로딩 상태
                    }
                }
            }

            progressBar.visibility = View.GONE
        }
    }

    // 감정 등록 모달
    private fun showEmotionCheckModal(context: String, message: String) {
        activity?.let {
            EmotionCheckModal.show(it, context, message)
        }
    }

    // 다이얼로그 내 상태 업데이트 함수 추가
    private fun updateScheduleDetailModalStatus(dialog: Dialog, status: ScheduleStatus) {
        try {
            // 액션 버튼 참조
            val btnStartSchedule = dialog.findViewById<Button>(R.id.btn_start_schedule)
            val btnPostponeSchedule = dialog.findViewById<Button>(R.id.btn_postpone_schedule)
            val btnCompleteSchedule = dialog.findViewById<Button>(R.id.btn_complete_schedule)

            Log.d(TAG, "상태 업데이트: $status")
            // 상태 텍스트뷰가 있는 경우 업데이트 (레이아웃에 따라 다를 수 있음)
            val tvStatus = dialog.findViewById<TextView>(R.id.tv_status)
            tvStatus?.let {
                it.text = when (status) {
                    ScheduleStatus.NOT_STARTED -> "예정"
                    ScheduleStatus.IN_PROGRESS -> "진행 중"
                    ScheduleStatus.COMPLETED -> "완료"
                    ScheduleStatus.POSTPONED -> "연기됨"
                }
            }

            // 액션 버튼 가시성 업데이트
            when (status) {
                ScheduleStatus.NOT_STARTED -> {
                    btnStartSchedule.visibility = View.VISIBLE
                    btnPostponeSchedule.visibility = View.VISIBLE
                    btnCompleteSchedule.visibility = View.GONE
                }
                ScheduleStatus.IN_PROGRESS -> {
                    btnStartSchedule.visibility = View.GONE
                    btnPostponeSchedule.visibility = View.GONE
                    btnCompleteSchedule.visibility = View.VISIBLE
                }
                ScheduleStatus.COMPLETED -> {
                    btnStartSchedule.visibility = View.GONE
                    btnPostponeSchedule.visibility = View.GONE
                    btnCompleteSchedule.visibility = View.GONE
                }
                ScheduleStatus.POSTPONED -> {
                    btnStartSchedule.visibility = View.VISIBLE
                    btnPostponeSchedule.visibility = View.GONE
                    btnCompleteSchedule.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "다이얼로그 상태 업데이트 오류", e)
        }
    }

    private fun setupDateHeader() {
        // 현재 날짜를 한글로 표시 (예: 5월 2일 금요일)
        val currentDate = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN)
        tvDateHeader.text = dayFormat.format(currentDate.time)
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter()
        rvSchedule.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }

        // 아이템 클릭 리스너 설정
        scheduleAdapter.setOnItemClickListener(object : ScheduleAdapter.OnItemClickListener {
            override fun onItemClick(scheduleItem: ScheduleItem) {
                // 상세보기 모달 다이얼로그 표시
                showScheduleDetailModal(scheduleItem)
            }
        })
    }

    private fun fetchTodaySchedules() {
        progressBar.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = scheduleRepository.getTodaySchedules()) {
                is NetworkResult.Success -> {
                    progressBar.visibility = View.GONE

                    val schedules = result.data
                    if (schedules.isEmpty()) {
                        tvEmptyState.visibility = View.VISIBLE
                        rvSchedule.visibility = View.GONE
                    } else {
                        tvEmptyState.visibility = View.GONE
                        rvSchedule.visibility = View.VISIBLE
                        scheduleAdapter.submitList(schedules)
                    }

                    Log.d(TAG, "일정 가져오기 성공: ${schedules.size}개 항목")
                }
                is NetworkResult.Error -> {
                    progressBar.visibility = View.GONE
                    tvEmptyState.visibility = View.VISIBLE
                    rvSchedule.visibility = View.GONE

                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "일정 가져오기 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    // 일정 추가 모달 다이얼로그 표시
    private fun showAddScheduleModal() {
        // 이미 열려있는 다이얼로그가 있다면 닫기
        dismissAddScheduleModal()

        // 현재 날짜와 시간 정보 초기화
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var startDate = dateFormat.format(calendar.time)
        var endDate = startDate
        var startTime = "09:00"
        var endTime = "11:00"
        var repeatType = "none"
        var repeatDays = mutableListOf<String>()

        // 새 다이얼로그 생성
        scheduleAddDialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setContentView(R.layout.modal_addschedule)

            // 다이얼로그 배경을 투명하게 설정
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // 다이얼로그 크기 및 위치 설정
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }

            // UI 요소 초기화
            val etTitle = findViewById<EditText>(R.id.et_schedule_title)
            val spinnerCategory = findViewById<Spinner>(R.id.spinner_category)
            val tvStartDate = findViewById<TextView>(R.id.tv_start_date)
            val tvEndDate = findViewById<TextView>(R.id.tv_end_date)
            val tvStartTime = findViewById<TextView>(R.id.tv_start_time)
            val spinnerRepeatType = findViewById<Spinner>(R.id.spinner_repeat_type)
            val weekdaySelection = findViewById<LinearLayout>(R.id.weekday_selection)
            val etNoteContent = findViewById<EditText>(R.id.et_note_content)
            val btnSave = findViewById<Button>(R.id.btn_save)
            val ibClose = findViewById<ImageButton>(R.id.ib_close)

            // 카테고리 스피너 설정
            val categories = arrayOf("개인", "업무", "가족", "기타")
            val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
            categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = categoryAdapter

            // 반복 유형 스피너 설정
            val repeatTypes = arrayOf("반복 안함", "매일", "매주", "매월")
            val repeatTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, repeatTypes)
            repeatTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerRepeatType.adapter = repeatTypeAdapter

            // 반복 유형 선택에 따라 요일 선택 레이아웃 표시/숨김 처리
            spinnerRepeatType.setOnItemSelectedListener { position ->
                if (position == 2) { // "매주" 선택 시
                    weekdaySelection.visibility = View.VISIBLE
                    repeatType = "weekly"
                } else {
                    weekdaySelection.visibility = View.GONE
                    repeatType = when (position) {
                        0 -> "none"
                        1 -> "daily"
                        3 -> "monthly"
                        else -> "none"
                    }
                }
            }

            // 날짜 초기화 및 클릭 리스너 설정
            val displayDateFormat = SimpleDateFormat("yyyy.MM.dd(E)", Locale.KOREAN)
            tvStartDate.text = displayDateFormat.format(calendar.time)
            tvEndDate.text = displayDateFormat.format(calendar.time)

            tvStartDate.setOnClickListener {
                showDatePicker(tvStartDate) { selectedDate ->
                    startDate = selectedDate
                }
            }

            tvEndDate.setOnClickListener {
                showDatePicker(tvEndDate) { selectedDate ->
                    endDate = selectedDate
                }
            }

            // 시간 초기화
            val startHour = 9
            val endHour = 11
            val displayStartTime = formatTimeForDisplay(startHour, 0)
            val displayEndTime = formatTimeForDisplay(endHour, 0)
            tvStartTime.text = "$displayStartTime - $displayEndTime"

            // 시간 범위 선택 클릭 리스너 설정
            tvStartTime.setOnClickListener {
                showTimeRangePicker(tvStartTime) { start, end ->
                    startTime = start
                    endTime = end
                }
            }

            // 요일 체크박스 리스너 설정
            val dayMap = mapOf(
                R.id.cb_mon to "MON",
                R.id.cb_tue to "TUE",
                R.id.cb_wed to "WED",
                R.id.cb_thu to "THU",
                R.id.cb_fri to "FRI",
                R.id.cb_sat to "SAT",
                R.id.cb_sun to "SUN"
            )

            dayMap.forEach { (id, day) ->
                findViewById<CheckBox>(id).setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        repeatDays.add(day)
                    } else {
                        repeatDays.remove(day)
                    }
                }
            }

            // 저장 버튼 클릭 리스너
            btnSave.setOnClickListener {
                val title = etTitle.text.toString().trim()
                val category = categories[spinnerCategory.selectedItemPosition].lowercase(Locale.getDefault())
                val note = etNoteContent.text.toString().trim()

                // 입력 검증
                if (title.isEmpty()) {
                    Toast.makeText(context, "제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 일정 생성 요청
                createSchedule(
                    title = title,
                    category = category,
                    startDate = startDate,
                    endDate = endDate,
                    startTime = startTime,
                    endTime = endTime,
                    repeatType = repeatType,
                    repeatDays = repeatDays,
                    note = note
                )

                // 다이얼로그 닫기
                dismiss()
            }

            // 닫기 버튼 클릭 리스너
            ibClose.setOnClickListener {
                dismiss()
            }
        }

        // 다이얼로그 표시
        scheduleAddDialog?.show()
    }

    // 스피너 아이템 선택 리스너 확장 함수
    private fun Spinner.setOnItemSelectedListener(onItemSelected: (Int) -> Unit) {
        this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onItemSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // 일정 생성 API 호출
    private fun createSchedule(
        title: String,
        category: String,
        startDate: String,
        endDate: String,
        startTime: String,
        endTime: String,
        repeatType: String,
        repeatDays: List<String>,
        note: String
    ) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                val scheduleRequest = ScheduleRequest(
                    title = title,
                    category = category,
                    start_date = startDate,
                    end_date = endDate,
                    start_time = startTime,
                    end_time = endTime,
                    repeat = ScheduleRepeatInfo(
                        type = repeatType,
                        days = repeatDays
                    ),
                    note = note
                )

                // API 호출
                val result = scheduleRepository.createSchedule(scheduleRequest)

                // 결과 처리
                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(requireContext(), "일정이 추가되었습니다", Toast.LENGTH_SHORT).show()
                        // 일정 목록 새로고침
                        fetchTodaySchedules()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), "일정 추가 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is NetworkResult.Loading -> {
                        // 로딩 상태 처리
                    }
                }

                progressBar.visibility = View.GONE

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "일정 추가 오류", e)
            }
        }
    }

    // 일정 추가 모달 다이얼로그 닫기 함수
    private fun dismissAddScheduleModal() {
        scheduleAddDialog?.dismiss()
        scheduleAddDialog = null
    }

    // 모달 다이얼로그 표시 함수 - 수정 모드 지원
    private fun showScheduleDetailModal(scheduleItem: ScheduleItem) {
        // 이미 열려있는 다이얼로그가 있다면 닫기
        dismissScheduleDetailModal()

        // 원본 데이터 저장 (수정용)
        val scheduleId = scheduleItem.id
        var startDate = ""
        var endDate = ""
        var startTime = ""
        var endTime = ""
        var category = scheduleItem.category
        var repeatType = "none"
        var repeatDays = emptyList<String>()

        // API에서 수정에 필요한 원본 형식 데이터를 가져오기 위한 임시 코드
        // 실제로는 API에서 원본 데이터 형식(YYYY-MM-DD 및 HH:MM)을 가져와야 함
        val currentDate = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        startDate = dateFormat.format(currentDate.time)
        endDate = startDate

        // 시간 문자열 파싱 (오전 7시 → 07:00 형식으로 변환)
        startTime = convertDisplayTimeToApiTime(scheduleItem.startTime)
        endTime = convertDisplayTimeToApiTime(scheduleItem.endTime)

        // 새 다이얼로그 생성
        scheduleDetailDialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setContentView(R.layout.modal_schedule)

            // 다이얼로그 배경을 투명하게 설정
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // 다이얼로그 크기 및 위치 설정
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }

            // UI 요소 초기화
            val tvDetailTitle = findViewById<TextView>(R.id.tv_detail_title)
            val etDetailTitle = findViewById<EditText>(R.id.et_detail_title)
            val tvDateValue = findViewById<TextView>(R.id.tv_date_value)
            val tvTimeValue = findViewById<TextView>(R.id.tv_time_value)
            val tvDatePicker = findViewById<TextView>(R.id.tv_date_picker)
            val tvTimePicker = findViewById<TextView>(R.id.tv_time_picker)
            val ivDatePicker = findViewById<ImageView>(R.id.iv_date_icon)
            val tvNoteContent = findViewById<TextView>(R.id.tv_note_content)
            val etNoteContent = findViewById<EditText>(R.id.et_note_content)
            val ivEdit = findViewById<ImageView>(R.id.iv_edit)
            val ivSave = findViewById<ImageView>(R.id.iv_save)
            val ivClose = findViewById<ImageButton>(R.id.iv_close)
            val ivDelete = findViewById<ImageView>(R.id.iv_delete)

            // 액션 버튼 참조
            val btnStartSchedule = findViewById<Button>(R.id.btn_start_schedule)
            val btnPostponeSchedule = findViewById<Button>(R.id.btn_postpone_schedule)
            val btnCompleteSchedule = findViewById<Button>(R.id.btn_complete_schedule)

            // 초기 데이터 설정
            tvDetailTitle.text = scheduleItem.title
            etDetailTitle.setText(scheduleItem.title)

            // 시간 정보 설정
            val timeText = if (scheduleItem.endTime.isNotEmpty()) {
                "${scheduleItem.startTime} - ${scheduleItem.endTime}"
            } else {
                scheduleItem.startTime
            }

            // 현재 날짜 포맷팅
            val displayDate = getCurrentFormattedDate()

            tvDateValue.text = displayDate
            tvTimeValue.text = timeText
            tvDatePicker.text = displayDate
            tvTimePicker.text = timeText

            // 노트 내용 설정
            if (!TextUtils.isEmpty(scheduleItem.note)) {
                val bulletedNote = scheduleItem.note.split("\n").joinToString("\n") { "• $it" }
                tvNoteContent.text = bulletedNote
                etNoteContent.setText(scheduleItem.note)
            } else {
                tvNoteContent.text = "추가 정보 없음"
                etNoteContent.setText("")
            }

            // 수정 버튼 클릭 리스너
            ivEdit.setOnClickListener {
                // 보기 모드 UI 숨기기
                tvDetailTitle.visibility = View.GONE
                tvTimeValue.visibility = View.GONE
                tvDateValue.visibility = View.GONE
                tvNoteContent.visibility = View.GONE
                ivEdit.visibility = View.GONE

                // 수정 모드 UI 표시
                etDetailTitle.visibility = View.VISIBLE
                tvDatePicker.visibility = View.VISIBLE
                ivDatePicker.visibility = View.VISIBLE
                tvTimePicker.visibility = View.VISIBLE
                etNoteContent.visibility = View.VISIBLE
                ivSave.visibility = View.VISIBLE
            }

            // 날짜 선택 클릭 리스너
            tvDatePicker.setOnClickListener {
                showDatePicker(tvDatePicker) { selectedDate ->
                    startDate = selectedDate
                    endDate = selectedDate
                }
            }

            ivDatePicker.setOnClickListener {
                showDatePicker(tvDatePicker) { selectedDate ->
                    startDate = selectedDate
                    endDate = selectedDate
                }
            }

            tvTimePicker.setOnClickListener {
                showTimeRangePicker(tvTimePicker) { start, end ->
                    startTime = start
                    endTime = end
                }
            }

            // 저장 버튼 클릭 리스너
            ivSave.setOnClickListener {
                val title = etDetailTitle.text.toString().trim()
                val note = etNoteContent.text.toString().trim()

                // 입력 검증
                if (title.isEmpty()) {
                    Toast.makeText(context, "제목을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 일정 업데이트 API 호출
                updateSchedule(
                    scheduleId = scheduleId,
                    title = title,
                    category = category,
                    startDate = startDate,
                    endDate = endDate,
                    startTime = startTime,
                    endTime = endTime,
                    repeatType = repeatType,
                    repeatDays = repeatDays,
                    note = note
                )

                // 다이얼로그 닫기
                dismiss()
            }

            // 삭제 버튼 클릭 리스너 추가
            ivDelete.setOnClickListener {
                // 확인 다이얼로그 표시
                showDeleteConfirmationDialog(scheduleId)

                // 현재 다이얼로그 닫기
                dismiss()
            }

            // 닫기 버튼 클릭 리스너
            ivClose.setOnClickListener {
                dismiss()
            }

            // 상태에 따라 버튼 표시 설정
            setupActionButtons(
                scheduleId = scheduleItem.id,
                status = scheduleItem.status,
                btnStart = btnStartSchedule,
                btnPostpone = btnPostponeSchedule,
                btnComplete = btnCompleteSchedule
            )
        }

        // 다이얼로그 표시
        scheduleDetailDialog?.show()
    }

    // 액션 버튼 설정 함수 추가
    private fun setupActionButtons(
        scheduleId: String,
        status: ScheduleStatus,
        btnStart: Button,
        btnPostpone: Button,
        btnComplete: Button
    ) {
        when (status) {
            ScheduleStatus.NOT_STARTED -> {
                // 시작 전 상태: 시작하기, 연기하기 버튼 표시
                btnStart.visibility = View.VISIBLE
                btnPostpone.visibility = View.VISIBLE
                btnComplete.visibility = View.GONE
            }
            ScheduleStatus.IN_PROGRESS -> {
                // 진행 중 상태: 완료하기 버튼만 표시
                btnStart.visibility = View.GONE
                btnPostpone.visibility = View.GONE
                btnComplete.visibility = View.VISIBLE
            }
            ScheduleStatus.COMPLETED -> {
                // 완료 상태: 모든 버튼 숨김
                btnStart.visibility = View.GONE
                btnPostpone.visibility = View.GONE
                btnComplete.visibility = View.GONE
            }
            ScheduleStatus.POSTPONED -> {
                // 연기 상태: 시작하기 버튼만 표시
                btnStart.visibility = View.VISIBLE
                btnPostpone.visibility = View.GONE
                btnComplete.visibility = View.GONE
            }
        }

        // 버튼 클릭 리스너 설정
        btnStart.setOnClickListener {
            handleScheduleAction(scheduleId, "start")
        }

        btnPostpone.setOnClickListener {
            handleScheduleAction(scheduleId, "postpone")
        }

        btnComplete.setOnClickListener {
            handleScheduleAction(scheduleId, "complete")
        }
    }

    // 일정 업데이트 API 호출
    private fun updateSchedule(
        scheduleId: String,
        title: String,
        category: String,
        startDate: String,
        endDate: String,
        startTime: String,
        endTime: String,
        repeatType: String,
        repeatDays: List<String>,
        note: String
    ) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                // API 호출
                val result = scheduleRepository.updateSchedule(
                    scheduleId = scheduleId,
                    title = title,
                    category = category,
                    startDate = startDate,
                    endDate = endDate,
                    startTime = startTime,
                    endTime = endTime,
                    repeatType = repeatType,
                    repeatDays = repeatDays,
                    note = note
                )

                // 결과 처리
                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(requireContext(), "일정이 업데이트되었습니다", Toast.LENGTH_SHORT).show()
                        // 일정 목록 새로고침
                        fetchTodaySchedules()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), "일정 업데이트 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is NetworkResult.Loading -> {
                        // 로딩 상태 처리
                    }
                }

                progressBar.visibility = View.GONE

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "일정 업데이트 오류", e)
            }
        }
    }

    // 시간 표시 형식을 API 형식으로 변환 (오전 7시 → 07:00)
    private fun convertDisplayTimeToApiTime(displayTime: String): String {
        return try {
            val amPm = if (displayTime.contains("오전")) 0 else 12
            val hour = displayTime.replace("오전 ", "").replace("오후 ", "").replace("시", "").trim().toInt()
            val actualHour = if (amPm == 12 && hour == 12) 12 else (amPm + hour) % 24

            String.format("%02d:00", actualHour)
        } catch (e: Exception) {
            "00:00" // 기본값
        }
    }

    // 현재 날짜를 포맷팅하여 반환
    private fun getCurrentFormattedDate(): String {
        val dateFormat = SimpleDateFormat("yyyy.MM.dd(E)", Locale.KOREAN)
        return dateFormat.format(Calendar.getInstance().time)
    }

    // 날짜 선택 다이얼로그 표시
    private fun showDatePicker(textView: TextView, onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                // 선택된 날짜를 API 형식(YYYY-MM-DD)과 표시 형식(YYYY.MM.DD(요일))으로 변환
                val selectedCalendar = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay)
                }

                val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val displayDateFormat = SimpleDateFormat("yyyy.MM.dd(E)", Locale.KOREAN)

                val apiFormattedDate = apiDateFormat.format(selectedCalendar.time)
                val displayFormattedDate = displayDateFormat.format(selectedCalendar.time)

                // 표시용 날짜 설정
                textView.text = displayFormattedDate

                // API용 날짜 반환
                onDateSelected(apiFormattedDate)
            },
            year, month, day
        )

        datePickerDialog.show()
    }

    // 시간 범위 선택 다이얼로그 표시
    private fun showTimeRangePicker(textView: TextView, onTimeSelected: (String, String) -> Unit) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE) // 현재 분 가져오기

        // 시작 시간 선택
        val startTimePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                // 종료 시간 선택
                val endTimePickerDialog = TimePickerDialog(
                    requireContext(),
                    { _, selectedEndHour, selectedEndMinute ->
                        // API 형식 시간 (HH:MM)
                        val apiStartTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                        val apiEndTime = String.format("%02d:%02d", selectedEndHour, selectedEndMinute)

                        // 표시 형식 시간 (오전/오후 H시 M분)
                        val displayStartTime = formatTimeForDisplay(selectedHour, selectedMinute)
                        val displayEndTime = formatTimeForDisplay(selectedEndHour, selectedEndMinute)

                        // 텍스트뷰에 표시
                        textView.text = "$displayStartTime - $displayEndTime"

                        // API 형식 시간 반환
                        onTimeSelected(apiStartTime, apiEndTime)
                    },
                    hour + 1, // 기본값: 현재 시간 + 1
                    minute,
                    false
                )

                endTimePickerDialog.show()
            },
            hour,
            minute,
            false
        )

        startTimePickerDialog.show()
    }

    // 시간을 표시 형식으로 변환 (7:30 → 오전 7시 30분)
    private fun formatTimeForDisplay(hour: Int, minute: Int): String {
        val timePrefix = when {
            hour == 0 -> "오전 12시"
            hour < 12 -> "오전 ${hour}시"
            hour == 12 -> "오후 12시"
            else -> "오후 ${hour - 12}시"
        }

        return if (minute == 0) {
            timePrefix // 분이 0인 경우 "시" 까지만 표시
        } else {
            "$timePrefix ${minute}분" // 분이 있는 경우 "분"까지 표시
        }
    }

    // 일정 삭제 확인 다이얼로그 표시
    private fun showDeleteConfirmationDialog(scheduleId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("일정 삭제")
            .setMessage("이 일정을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { dialog, _ ->
                deleteSchedule(scheduleId)
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    // 일정 삭제 API 호출
    private fun deleteSchedule(scheduleId: String) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                // API 호출
                val result = scheduleRepository.deleteSchedule(scheduleId)

                // 결과 처리
                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(requireContext(), "일정이 삭제되었습니다", Toast.LENGTH_SHORT).show()
                        // 일정 목록 새로고침
                        fetchTodaySchedules()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), "일정 삭제 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                    is NetworkResult.Loading -> {
                        // 로딩 상태 처리
                    }
                }

                progressBar.visibility = View.GONE

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "일정 삭제 오류", e)
            }
        }
    }


    // 모달 다이얼로그 닫기 함수
    private fun dismissScheduleDetailModal() {
        scheduleDetailDialog?.dismiss()
        scheduleDetailDialog = null
    }

    // Fragment 라이프사이클에 맞춰 다이얼로그 정리
    override fun onDestroyView() {
        super.onDestroyView()
        dismissAddScheduleModal()
        dismissScheduleDetailModal()
    }
}