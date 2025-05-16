package com.example.todak.wear.presentation

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.example.todak.wear.R
import com.example.todak.wear.util.WearableConstants
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ManualStepActivity : Activity(), DataClient.OnDataChangedListener {

    private lateinit var tvStepTitle: TextView
    private lateinit var imgStepImage: ImageView
    private lateinit var tvStepDescription: TextView
    private lateinit var btnNext: Button

    private var shopId: String = ""
    private var menuName: String = ""
    private var currentStep: Int = 1
    private var totalSteps: Int = 1
    private var imageUrl: String = ""
    private var stepDescription: String = ""

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val TAG = "ManualStepActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_step)

        // 뷰 초기화
        tvStepTitle = findViewById(R.id.tvStepTitle)
        imgStepImage = findViewById(R.id.imgStepImage)
        tvStepDescription = findViewById(R.id.tvStepDescription)
        btnNext = findViewById(R.id.btnNext)

        // 인텐트에서 데이터 가져오기
        shopId = intent.getStringExtra("shop_id") ?: ""
        menuName = intent.getStringExtra("menu_name") ?: ""
        currentStep = intent.getIntExtra("current_step", 1)
        totalSteps = intent.getIntExtra("total_steps", 1)
        imageUrl = intent.getStringExtra("image_url") ?: ""
        stepDescription = intent.getStringExtra("step_description") ?: ""

        // UI 업데이트
        updateUI()

        // 다음 버튼 클릭 리스너
        btnNext.setOnClickListener {
            if (currentStep < totalSteps) {
                goToNextStep()
            } else {
                finish() // 마지막 단계면 액티비티 종료
            }
        }
    }

    private fun updateUI() {
        tvStepTitle.text = "Step $currentStep"
        tvStepDescription.text = stepDescription

        // 이미지 로드
        if (imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.emoji_happy)
                .error(R.drawable.emoji_happy)
                .into(imgStepImage)
        } else {
            imgStepImage.setImageResource(R.drawable.emoji_happy)
        }

        // 마지막 단계이면 다음 버튼 텍스트 변경
        if (currentStep == totalSteps) {
            btnNext.text = "완료"
        } else {
            btnNext.text = "다음"
        }
    }

    private fun goToNextStep() {
        if (currentStep < totalSteps) {
            // 현재 단계 증가
            currentStep++

            // 모바일 앱에 단계 변경 알림
            sendStepChangeToMobile()

            // 다음 단계 정보 요청
            requestStepInfo()
        }
    }

    private fun sendStepChangeToMobile() {
        scope.launch {
            try {
                val dataClient = Wearable.getDataClient(this@ManualStepActivity)

                val request = PutDataMapRequest.create(WearableConstants.MANUAL_STEP_CHANGE_PATH).run {
                    dataMap.putString("shop_id", shopId)
                    dataMap.putString("menu_name", menuName)
                    dataMap.putInt("current_step", currentStep)
                    dataMap.putLong(WearableConstants.KEY_TIMESTAMP, System.currentTimeMillis())
                    asPutDataRequest()
                }

                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "단계 변경 전송 결과: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "단계 변경 전송 실패", e)
            }
        }
    }

    private fun requestStepInfo() {
        // 임시 데이터 (실제로는 모바일 앱에서 받아야 함)
        val dummyDescription = when (currentStep) {
            2 -> "냄비에 물을 넣고 끓여주세요."
            3 -> "면을 넣고 3분간 삶아주세요."
            4 -> "소스를 넣고 잘 저어주세요."
            else -> "완성되었습니다!"
        }

        stepDescription = dummyDescription

        // UI 업데이트
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val path = uri.path ?: ""

                when (path) {
                    WearableConstants.MANUAL_STEP_UPDATE_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val updatedShopId = dataMap.getString("shop_id", "")
                        val updatedMenuName = dataMap.getString("menu_name", "")

                        // 현재 보고 있는 매뉴얼과 일치하는지 확인
                        if (updatedShopId == shopId && updatedMenuName == menuName) {
                            val updatedStep = dataMap.getInt("current_step", 1)
                            val updatedTotalSteps = dataMap.getInt("total_steps", 1)
                            val updatedImageUrl = dataMap.getString("image_url", "")
                            val updatedDescription = dataMap.getString("step_description", "")

                            // 현재 단계 업데이트
                            currentStep = updatedStep
                            totalSteps = updatedTotalSteps
                            imageUrl = updatedImageUrl
                            stepDescription = updatedDescription

                            // UI 업데이트
                            runOnUiThread {
                                updateUI()
                            }
                        }
                    }
                }
            }
        }
    }
}