package com.example.todak.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.todak.R
import kotlin.math.min

class BudgetDonutChartView : View {
    private var backgroundPaint: Paint? = null // 회색 기본 도넛
    private var progressPaint: Paint? = null // 진행상태 (초록색 또는 빨간색)
    private var rectF: RectF? = null

    private var usagePercent = 0f // 기본값을 0으로 변경
    private var isDataSet = false // 데이터가 설정되었는지 여부

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        backgroundPaint = Paint()
        backgroundPaint!!.isAntiAlias = true
        backgroundPaint!!.style = Paint.Style.STROKE
        backgroundPaint!!.color = Color.LTGRAY

        progressPaint = Paint()
        progressPaint!!.isAntiAlias = true
        progressPaint!!.style = Paint.Style.STROKE
        progressPaint!!.strokeCap = Paint.Cap.ROUND

        rectF = RectF()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 레이아웃 에디터에서는 간단한 표시만 제공
        if (isInEditMode) {
            drawEditModePreview(canvas)
            return
        }

        // 데이터가 설정되지 않은 경우 아무것도 그리지 않음
        if (!isDataSet) {
            return
        }

        drawDonutChart(canvas)
    }

    // 디자인 뷰에서 미리보기 그리기
    private fun drawEditModePreview(canvas: Canvas) {
        val paint = Paint()
        paint.color = Color.DKGRAY
        paint.textSize = 30f
        paint.textAlign = Paint.Align.CENTER

        val centerX = width / 2
        val centerY = height / 2

        canvas.drawText("Budget Donut Chart", centerX.toFloat(), centerY.toFloat(), paint)

        // 도넛 차트 미리보기 그리기
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 40f
        paint.color = Color.LTGRAY
        val radius = (min(width.toDouble(), height.toDouble()) / 3f).toFloat()
        canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), radius, paint)

        // 진행 부분 그리기 (예시)
        paint.color = Color.GREEN
        paint.strokeCap = Paint.Cap.ROUND
        val rectF =
            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawArc(rectF, -90f, 270f, false, paint)
    }

    // 실제 도넛 차트 그리기
    private fun drawDonutChart(canvas: Canvas) {
        val width = width
        val height = height
        val centerX = width / 2
        val centerY = height / 2

        // 두께를 더 두껍게 설정 (반지름의 20%)
        val strokeWidth = (min(width.toDouble(), height.toDouble()) / 10).toInt()
        backgroundPaint!!.strokeWidth = strokeWidth.toFloat()
        progressPaint!!.strokeWidth = strokeWidth.toFloat()

        val radius = (min(width.toDouble(), height.toDouble()) / 2 - strokeWidth).toInt()

        rectF!![(centerX - radius).toFloat(), (centerY - radius).toFloat(), (centerX + radius).toFloat()] =
            (centerY + radius).toFloat()

        // 배경 도넛 그리기 (회색)
        canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), radius.toFloat(), backgroundPaint!!)

        // 진행 상태 그리기
        val sweepAngle = (360f * min(usagePercent.toDouble(), 100.0) / 100f).toFloat()

        // 색상 결정 (100% 이하: 초록색, 초과: 빨간색)
        if (usagePercent <= 100f) {
            progressPaint!!.color = ContextCompat.getColor(context, R.color.middlegreen)
        } else {
            progressPaint!!.color = ContextCompat.getColor(context, R.color.red)
        }

        canvas.drawArc(rectF!!, -90f, -sweepAngle, false, progressPaint!!)
    }

    // 사용률 설정 메서드
    fun setUsagePercent(usagePercent: Float) {
        this.usagePercent = usagePercent
        isDataSet = true
        invalidate()
    }

    // 초기화 메서드 - 데이터를 리셋하고 아무것도 표시하지 않음
    fun reset() {
        usagePercent = 0f
        isDataSet = false
        invalidate()
    }
}