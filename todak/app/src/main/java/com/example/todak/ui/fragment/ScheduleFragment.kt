package com.example.todak.ui.fragment

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.LineBackgroundSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CalendarView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
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
    private lateinit var btnAddScheduleCal: MaterialButton
    private lateinit var btnAddSchedule: MaterialButton
    private lateinit var btnBackToCalendar: MaterialButton
    private lateinit var calendarView: MaterialCalendarView
    private lateinit var calendarContainer: ConstraintLayout
    private lateinit var listContainer: ConstraintLayout
    private lateinit var tvCurrentMonth: TextView
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton

    private lateinit var scheduleAdapter: ScheduleAdapter
    private val scheduleRepository = ScheduleRepository()
    private val TAG = "ScheduleFragment"

    // 선택된 날짜 상태 변수
    private var selectedDate: String = ""
    private var selectedCalendar: Calendar = Calendar.getInstance()
    private var allSchedules: List<ScheduleItem> = emptyList()

    // 모달 다이얼로그
    private var scheduleDetailDialog: Dialog? = null
    private var scheduleAddDialog: Dialog? = null

    // 현재 UI 모드 (캘린더 또는 목록)
    private var isListMode = false

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
        btnAddScheduleCal = view.findViewById(R.id.btn_add_schedule_calender)
        btnAddSchedule = view.findViewById(R.id.btn_add_schedule)
        btnBackToCalendar = view.findViewById(R.id.btn_back_to_calendar)
        calendarView = view.findViewById(R.id.calendarView)
        calendarContainer = view.findViewById(R.id.calendar_container)
        listContainer = view.findViewById(R.id.list_container)
        tvCurrentMonth = view.findViewById(R.id.tv_current_month)
        btnPrevMonth = view.findViewById(R.id.btn_prev_month)
        btnNextMonth = view.findViewById(R.id.btn_next_month)

        // 현재 날짜 선택
        val currentDate = Calendar.getInstance()
        selectedCalendar = currentDate
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate.time)

        setupDateHeader()
        setupRecyclerView()
        setupCalendarView()
        setupButtonListeners()

        // 전체 일정 데이터를 가져옵니다 (캘린더 데코레이터를 위해)
        fetchAllSchedules()

        // 초기에는 캘린더 모드로 시작
        showCalendarMode()

        arguments?.let { args ->
            val scheduleId = args.getString("schedule_id")
            val action = args.getString("action")
            val showDetail = args.getBoolean("show_detail", false)

            if (showDetail && scheduleId != null) {
                // 자세한 일정 보기로 전환
                showListMode()

                lifecycleScope.launch {
                    when (val result = scheduleRepository.getSchedulesByDate(selectedDate)) {
                        is NetworkResult.Success -> {
                            val schedules = result.data
                            val schedule = schedules.find { it.id == scheduleId }

                            schedule?.let {
                                showScheduleDetailModal(it)

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
            }
        }
    }

    // 뒤로가기 처리 메서드 추가
    fun handleBackPressed(): Boolean {
        return if (isListMode) {
            // 목록 모드인 경우 달력 모드로 전환
            showCalendarMode()
            true // 뒤로가기 이벤트 소비함 (더 이상 처리하지 않음)
        } else {
            // 달력 모드인 경우 기본 뒤로가기 동작 수행 (MainActivity에서 처리)
            false
        }
    }

    private fun setupButtonListeners() {

        btnAddScheduleCal.setOnClickListener {
            showAddScheduleModal()
        }

        // 일정 추가 버튼 클릭 리스너
        btnAddSchedule.setOnClickListener {
            showAddScheduleModal()
        }

        // 달력으로 돌아가기 버튼 클릭 리스너
        btnBackToCalendar.setOnClickListener {
            showCalendarMode()
        }

        // 이전 달 버튼 클릭 리스너
        btnPrevMonth.setOnClickListener {
            calendarView.goToPrevious()
        }

        // 다음 달 버튼 클릭 리스너
        btnNextMonth.setOnClickListener {
            calendarView.goToNext()
        }
    }
    // 캘린더 모드로 전환
    private fun showCalendarMode() {
        isListMode = false

        // 헤더 텍스트 업데이트
        val dayFormat = SimpleDateFormat("M월 yyyy", Locale.KOREAN)
        tvCurrentMonth.text = dayFormat.format(selectedCalendar.time)

        // UI 컨테이너 전환
        calendarContainer.visibility = View.VISIBLE
        listContainer.visibility = View.GONE

        // 달력으로 돌아가기 버튼 숨기기
        btnBackToCalendar.visibility = View.GONE

        // 날짜 헤더 업데이트
        setupDateHeader()

        // 캘린더 뷰로 돌아갈 때 항상 새로 데이터 로딩
        lifecycleScope.launch {
            // 로딩 표시
            progressBar.visibility = View.VISIBLE

            // 전체 일정 데이터 다시 로드
            when (val result = scheduleRepository.getAllSchedules()) {
                is NetworkResult.Success -> {
                    allSchedules = result.data

                    // 캘린더 데코레이터 업데이트
                    updateCalendarDecorators()

                    progressBar.visibility = View.GONE
                }
                is NetworkResult.Error -> {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "캘린더 데이터 새로고침 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    // 목록 모드로 전환
    private fun showListMode() {
        isListMode = true

        // UI 컨테이너 전환
        calendarContainer.visibility = View.GONE
        listContainer.visibility = View.VISIBLE

        // 달력으로 돌아가기 버튼 표시
        btnBackToCalendar.visibility = View.VISIBLE

        // 날짜 헤더 업데이트
        setupDateHeader()

        // 선택된 날짜의 일정 로드
        fetchSchedulesByDate(selectedDate)
    }

    private fun setupCalendarView() {
        // MaterialCalendarView 설정
        calendarView.setSelectedDate(CalendarDay.today())
        calendarView.topbarVisible = false  // 내장 헤더 숨기기 (코드에서도 설정)

        // 달 표시 업데이트
        updateMonthDisplay()

        // 선택 색상을 초록색으로 변경
        calendarView.selectionColor = ContextCompat.getColor(requireContext(), R.color.middlegreen)

        // 날짜 선택 리스너
        calendarView.setOnDateChangedListener { widget, date, selected ->
            val calendar = Calendar.getInstance()
            calendar.set(date.year, date.month - 1, date.day)
            selectedCalendar = calendar
            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

            // 목록 모드로 전환
            showListMode()
        }

        // 월 변경 리스너 (필요한 경우)
        calendarView.setOnMonthChangedListener { widget, date ->
            // 달 표시 업데이트
            updateMonthDisplay()

            // 전체 일정 데이터를 다시 로드하고 캘린더 데코레이터 업데이트
            fetchAllSchedules()
        }

        // 이전/다음 달 버튼 리스너 설정
        btnPrevMonth.setOnClickListener {
            calendarView.goToPrevious()
        }

        btnNextMonth.setOnClickListener {
            calendarView.goToNext()
        }
    }

    // 달 표시 업데이트
    private fun updateMonthDisplay() {
        val currentDate = calendarView.currentDate
        val calendar = Calendar.getInstance()
        calendar.set(currentDate.year, currentDate.month - 1, 1)

        val monthFormat = SimpleDateFormat("M월 yyyy", Locale.KOREAN)
        tvCurrentMonth.text = monthFormat.format(calendar.time)
    }

    private fun setupDateHeader() {
        // 현재 모드에 따라 날짜 헤더 텍스트 업데이트
        val dayFormat = if (isListMode) {
            SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN)
        } else {
            SimpleDateFormat("M월 yyyy", Locale.KOREAN)
        }
        tvDateHeader.text = dayFormat.format(selectedCalendar.time)
    }

    // 전체 일정 데이터 가져오기
    private fun fetchAllSchedules() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            when (val result = scheduleRepository.getAllSchedules()) {
                is NetworkResult.Success -> {
                    progressBar.visibility = View.GONE
                    allSchedules = result.data

                    // 캘린더에 일정 표시 업데이트
                    updateCalendarDecorators()

                    // 현재 선택된 날짜의 일정 로드
                    fetchSchedulesByDate(selectedDate)
                }
                is NetworkResult.Error -> {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "전체 일정 가져오기 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    // 캘린더 일정 데코레이터 업데이트
    private fun updateCalendarDecorators() {
        // 기존 데코레이터 모두 제거
        calendarView.removeDecorators()

        // 기본 데코레이터 추가 (오늘 날짜 등)
        calendarView.addDecorators(
            TodayDecorator(requireContext())
        )

        // 일정별 데코레이터 추가
        val eventDates = mutableMapOf<CalendarDay, MutableList<ScheduleItem>>()

        // 현재 표시 중인 달의 범위 구하기
        val calendarMonth = calendarView.currentDate
        val year = calendarMonth.year
        val month = calendarMonth.month

        // 일정을 날짜별로 그룹화
        for (schedule in allSchedules) {
            try {
                // 기본 시작 날짜 처리
                val startDateParts = schedule.startDate.split("-")
                val endDateParts = schedule.endDate.split("-")

                if (startDateParts.size == 3) {
                    val startYear = startDateParts[0].toInt()
                    val startMonth = startDateParts[1].toInt()
                    val startDay = startDateParts[2].toInt()

                    val endYear = endDateParts[0].toInt()
                    val endMonth = endDateParts[1].toInt()
                    val endDay = endDateParts[2].toInt()

                    // 일반 일정 처리
                    val startDate = CalendarDay.from(startYear, startMonth, startDay)

                    // 하루짜리 일정이거나 표시 중인 달에 시작일이 있으면 표시
                    if ((startYear == endYear && startMonth == endMonth && startDay == endDay) ||
                        (startYear == year && startMonth == month)) {
                        addScheduleToEventDates(eventDates, startDate, schedule)
                    }

                    // 반복 일정 처리
                    if (schedule.repeat?.type != "none" && schedule.repeat?.type != null) {
                        processRecurringSchedule(schedule, eventDates, year, month)
                    }

                    // 여러 날짜에 걸친 일정 처리 (반복이 아닌 경우)
                    if (!(startYear == endYear && startMonth == endMonth && startDay == endDay) &&
                        (schedule.repeat?.type == "none" || schedule.repeat?.type == null)) {
                        processMultiDaySchedule(schedule, eventDates, year, month)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "날짜 변환 오류: ${e.message}")
            }
        }

        // 각 날짜에 이벤트 데코레이터 추가
        eventDates.forEach { (day, schedules) ->
            calendarView.addDecorator(EventDecorator(requireContext(), day, schedules))
        }
    }

    // 일정을 이벤트 날짜 맵에 추가하는 헬퍼 함수
    private fun addScheduleToEventDates(
        eventDates: MutableMap<CalendarDay, MutableList<ScheduleItem>>,
        calendarDay: CalendarDay,
        schedule: ScheduleItem
    ) {
        if (!eventDates.containsKey(calendarDay)) {
            eventDates[calendarDay] = mutableListOf()
        }
        eventDates[calendarDay]?.add(schedule)
    }

    // 반복 일정 처리 함수
    private fun processRecurringSchedule(
        schedule: ScheduleItem,
        eventDates: MutableMap<CalendarDay, MutableList<ScheduleItem>>,
        year: Int,
        month: Int
    ) {
        // 표시할 월의 첫날과 마지막 날 계산
        val firstDayOfMonth = CalendarDay.from(year, month, 1)
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val lastDayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lastCalendarDay = CalendarDay.from(year, month, lastDayOfMonth)

        // 시작일 날짜 파싱
        val startDateParts = schedule.startDate.split("-")
        val startYear = startDateParts[0].toInt()
        val startMonth = startDateParts[1].toInt()
        val startDay = startDateParts[2].toInt()
        val startCalendarDay = CalendarDay.from(startYear, startMonth, startDay)

        // 종료일 날짜 파싱 (있다면)
        val endDateParts = schedule.endDate.split("-")
        val endYear = endDateParts[0].toInt()
        val endMonth = endDateParts[1].toInt()
        val endDay = endDateParts[2].toInt()
        val endCalendarDay = CalendarDay.from(endYear, endMonth, endDay)

        // 시작일이 표시 월보다 나중이면 처리하지 않음
        if (startCalendarDay.isAfter(lastCalendarDay)) {
            return
        }

        // 종료일이 표시 월보다 이전이면 처리하지 않음
        if (endCalendarDay.isBefore(firstDayOfMonth)) {
            return
        }

        when (schedule.repeat?.type) {
            "daily" -> {
                // 매일 반복
                calendar.set(year, month - 1, 1) // 현재 월의 첫날
                while (calendar.get(Calendar.MONTH) == month - 1) {
                    // 시작일 이후이고 종료일 이전인지 확인
                    val currentCalendarDay = CalendarDay.from(
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH) + 1,
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )

                    if (!currentCalendarDay.isBefore(startCalendarDay) &&
                        !currentCalendarDay.isAfter(endCalendarDay)) {
                        addScheduleToEventDates(eventDates, currentCalendarDay, schedule)
                    }

                    // 다음 날짜로 이동
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            "weekly" -> {
                // 매주 반복 (특정 요일)
                val daysOfWeek = schedule.repeat?.days ?: emptyList()
                val dayMapping = mapOf(
                    "월" to Calendar.MONDAY,
                    "화" to Calendar.TUESDAY,
                    "수" to Calendar.WEDNESDAY,
                    "목" to Calendar.THURSDAY,
                    "금" to Calendar.FRIDAY,
                    "토" to Calendar.SATURDAY,
                    "일" to Calendar.SUNDAY
                )

                calendar.set(year, month - 1, 1) // 현재 월의 첫날
                while (calendar.get(Calendar.MONTH) == month - 1) {
                    // 현재 날짜가 지정된 요일인지 확인
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

                    // 현재 요일이 반복 요일 목록에 있는지 확인
                    val isScheduledDay = daysOfWeek.any { day ->
                        dayMapping[day] == dayOfWeek
                    }

                    if (isScheduledDay) {
                        // 시작일 이후이고 종료일 이전인지 확인
                        val currentCalendarDay = CalendarDay.from(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1,
                            calendar.get(Calendar.DAY_OF_MONTH)
                        )

                        if (!currentCalendarDay.isBefore(startCalendarDay) &&
                            !currentCalendarDay.isAfter(endCalendarDay)) {
                            addScheduleToEventDates(eventDates, currentCalendarDay, schedule)
                        }
                    }

                    // 다음 날짜로 이동
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            "monthly" -> {
                // 매월 반복 (같은 날짜)
                val dayOfMonth = startCalendarDay.day

                // 현재 월에 해당 날짜가 있는지 확인
                if (dayOfMonth <= lastDayOfMonth) {
                    val currentCalendarDay = CalendarDay.from(year, month, dayOfMonth)

                    // 시작일 이후이고 종료일 이전인지 확인
                    if (!currentCalendarDay.isBefore(startCalendarDay) &&
                        !currentCalendarDay.isAfter(endCalendarDay)) {
                        addScheduleToEventDates(eventDates, currentCalendarDay, schedule)
                    }
                }
            }
        }
    }

    // 여러 날짜에 걸친 일정 처리 함수
    private fun processMultiDaySchedule(
        schedule: ScheduleItem,
        eventDates: MutableMap<CalendarDay, MutableList<ScheduleItem>>,
        year: Int,
        month: Int
    ) {
        // 시작일과 종료일 파싱
        val startDateParts = schedule.startDate.split("-")
        val endDateParts = schedule.endDate.split("-")

        val startYear = startDateParts[0].toInt()
        val startMonth = startDateParts[1].toInt()
        val startDay = startDateParts[2].toInt()

        val endYear = endDateParts[0].toInt()
        val endMonth = endDateParts[1].toInt()
        val endDay = endDateParts[2].toInt()

        // 현재 월의 첫날과 마지막 날
        val firstDayOfMonth = 1
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val lastDayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 현재 월에 표시할 날짜 범위 계산
        val displayStartDay = if (startYear == year && startMonth == month) startDay else firstDayOfMonth
        val displayEndDay = if (endYear == year && endMonth == month) endDay else lastDayOfMonth

        // 일정이 현재 월에 있는지 확인
        if ((startYear < year || (startYear == year && startMonth <= month)) &&
            (endYear > year || (endYear == year && endMonth >= month))) {

            // 표시 범위 내의 모든 날짜에 일정 추가
            for (day in displayStartDay..displayEndDay) {
                val calendarDay = CalendarDay.from(year, month, day)
                addScheduleToEventDates(eventDates, calendarDay, schedule)
            }
        }
    }

    // 데코레이터 클래스들
    inner class TodayDecorator(context: Context) : DayViewDecorator {
        private val drawable = ContextCompat.getDrawable(context, R.drawable.bg_circle_button)?.mutate()?.apply {
            // 크기를 코드에서 조정
            val size = 32 * context.resources.displayMetrics.density.toInt() // 32dp 크기로 설정
            setBounds(0, 0, size, size)
        }
        override fun shouldDecorate(day: CalendarDay): Boolean {
            return day == CalendarDay.today()
        }

        override fun decorate(view: DayViewFacade) {
            drawable?.let {
                view.setBackgroundDrawable(it)
            }
        }
    }

    inner class EventDecorator(
        private val context: Context,
        private val day: CalendarDay,
        private val schedules: List<ScheduleItem>
    ) : DayViewDecorator {

        override fun shouldDecorate(day: CalendarDay): Boolean {
            return this.day == day
        }

        override fun decorate(view: DayViewFacade) {
            // 모든 일정에 대해 스팬 추가
            if (schedules.isNotEmpty()) {
                // 3개 이상인 경우 첫 번째 일정만 표시하고 +N개로 나머지 표시
                if (schedules.size >= 3) {
                    // 첫 번째 일정 표시
                    view.addSpan(ScheduleDotSpan(context, schedules[0], 0))

                    // +N개 표시 (2개 이상인 경우)
                    val moreText = "+${schedules.size - 1}개"
                    view.addSpan(MoreEventsSpan(context, moreText, 1))
                } else {
                    // 2개 이하면 모두 표시
                    schedules.forEachIndexed { index, schedule ->
                        view.addSpan(ScheduleDotSpan(context, schedule, index))
                    }
                }
            }
        }
    }

    inner class ScheduleDotSpan(
        private val context: Context,
        private val schedule: ScheduleItem,
        private val index: Int
    ) : LineBackgroundSpan {

        private val paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.lightgreen)
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        override fun drawBackground(
            canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int,
            baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int,
            lineNumber: Int
        ) {
            val density = context.resources.displayMetrics.density
            val margin = 4 * density
            val itemHeight = 12 * density
            val yOffset = bottom + margin + (index * (itemHeight + margin))

            // 사각형 그리기
            val rect = RectF(
                left + margin,
                yOffset,
                right - margin,
                yOffset + itemHeight
            )

            canvas.drawRoundRect(rect, 8f, 8f, this.paint)

            // 텍스트 그리기
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 9 * density
                isAntiAlias = true
                typeface = Typeface.DEFAULT
            }

            val title = if (schedule.title.length > 7)
                "${schedule.title.substring(0, 5)}.."
            else
                schedule.title

            val textWidth = textPaint.measureText(title)
            val textX = (left + right) / 2 - textWidth / 2
            val textY = yOffset + itemHeight / 2 + textPaint.textSize / 3

            canvas.drawText(title, textX, textY, textPaint)
        }
    }

    inner class MoreEventsSpan(
        private val context: Context,
        private val text: String,
        private val index: Int
    ) : LineBackgroundSpan {

        private val paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.darkgreen)
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        override fun drawBackground(
            canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int,
            baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int,
            lineNumber: Int
        ) {
            val density = context.resources.displayMetrics.density
            val margin = 4 * density
            val itemHeight = 12 * density
            val yOffset = bottom + margin + (index * (itemHeight + margin))

            // 사각형 그리기
            val rect = RectF(
                left + margin,
                yOffset,
                right - margin,
                yOffset + itemHeight
            )

            canvas.drawRoundRect(rect, 8f, 8f, this.paint)

            // 텍스트 그리기
            val textPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 9 * density
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
            }

            val textWidth = textPaint.measureText(this.text)
            val textX = (left + right) / 2 - textWidth / 2
            val textY = yOffset + itemHeight / 2 + textPaint.textSize / 3

            canvas.drawText(this.text, textX, textY, textPaint)
        }
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

        // 월 변경 리스너 (필요한 경우)
        calendarView.setOnMonthChangedListener { widget, date ->
            // 모든 일정 데이터를 다시 로드하고 현재 표시 중인 달에 대한 데코레이터 업데이트
            fetchAllSchedules()
        }
    }

    // 일정 목록 로드하는 함수 (목록 모드에서 호출)
    private fun fetchSchedulesByDate(date: String) {
        progressBar.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            when (val result = scheduleRepository.getSchedulesByDate(date)) {
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

                    Log.d(TAG, "$date 일정 가져오기 성공: ${schedules.size}개 항목")
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
                        fetchSchedulesByDate(selectedDate)

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

    // 기존의 일정 추가 모달에서 날짜 초기값을 선택된 날짜로 설정
    private fun showAddScheduleModal() {
        // 이미 열려있는 다이얼로그가 있다면 닫기
        dismissAddScheduleModal()

        // 선택된 날짜 정보 초기화
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var startDate = selectedDate
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

            // 날짜 초기화 및 클릭 리스너 설정 - 선택된 날짜로 초기화
            val displayDateFormat = SimpleDateFormat("yyyy.MM.dd(E)", Locale.KOREAN)
            tvStartDate.text = displayDateFormat.format(selectedCalendar.time)
            tvEndDate.text = displayDateFormat.format(selectedCalendar.time)

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
                        // 현재 모드에 따라 다른 새로고침 방식 적용
                        if (isListMode) {
                            // 목록 모드인 경우 선택된 날짜의 일정 목록 새로고침
                            refreshScheduleList()
                        } else {
                            // 전체 일정 데이터를 새로 로드하여 캘린더 및 목록 모두 갱신
                            fetchAllSchedules()
                            // 캘린더 모드인 경우 캘린더 데코레이터 업데이트
                            updateCalendarDecorators()
                        }
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

    // 일정 생성 API 호출 성공 후 호출할 함수
    private fun refreshScheduleList() {
        lifecycleScope.launch {
            // 기존 어댑터 상태 저장
            val oldAdapter = scheduleAdapter

            // 새 어댑터 생성 및 설정
            scheduleAdapter = ScheduleAdapter()
            rvSchedule.adapter = scheduleAdapter

            // 아이템 클릭 리스너 다시 설정
            scheduleAdapter.setOnItemClickListener(object : ScheduleAdapter.OnItemClickListener {
                override fun onItemClick(scheduleItem: ScheduleItem) {
                    showScheduleDetailModal(scheduleItem)
                }
            })

            // 데이터 다시 로드
            fetchSchedulesByDate(selectedDate)
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
                ivDelete.visibility = View.GONE
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
                        fetchSchedulesByDate(selectedDate)
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
                        fetchSchedulesByDate(selectedDate)
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