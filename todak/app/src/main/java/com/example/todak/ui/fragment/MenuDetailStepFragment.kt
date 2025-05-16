package com.example.todak.ui.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.todak.R
import com.example.todak.data.model.ManualDetailResponse
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.Step as ApiStep
import com.example.todak.data.repository.ApiRepository
import com.example.todak.ui.activity.MainActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import kotlinx.coroutines.launch
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MenuDetailStepFragment : Fragment() {

    private lateinit var tvStepTitle: TextView
    private lateinit var imgStepImage: ImageView
    private lateinit var tvStepDescription: TextView
    private lateinit var btnNext: Button
    private lateinit var btnManualComplete: Button

    private var shopId: String = ""
    private var shopName: String = ""
    private var menuName: String = ""
    private var menuImageResId: Int = 0
    private var currentStep: Int = 1
    private var totalSteps: Int = 1

    private val apiRepository = ApiRepository()
    private var manualDetail: ManualDetailResponse? = null
    private val steps = mutableListOf<Step>()

    private val TAG = "MenuDetailStepFragment"

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var dataClient: DataClient

    // UI에 표시할 Step 데이터 클래스
    data class Step(
        val title: String,
        val description: String,
        val imageResId: Int = R.drawable.img_shop_sample, // 기본 이미지
        val imageUrl: String? = null,
        val requiredItems: String = ""
    )

    companion object {
        private const val ARG_SHOP_ID = "shop_id"
        private const val ARG_SHOP_NAME = "shop_name"
        private const val ARG_MENU_NAME = "menu_name"
        private const val ARG_MENU_IMAGE = "menu_image"
        private const val ARG_CURRENT_STEP = "current_step"

        fun newInstance(
            shopId: String,
            shopName: String,
            menuName: String,
            menuImageResId: Int,
            currentStep: Int = 1
        ): MenuDetailStepFragment {
            val fragment = MenuDetailStepFragment()
            val args = Bundle()
            args.putString(ARG_SHOP_ID, shopId)
            args.putString(ARG_SHOP_NAME, shopName)
            args.putString(ARG_MENU_NAME, menuName)
            args.putInt(ARG_MENU_IMAGE, menuImageResId)
            args.putInt(ARG_CURRENT_STEP, currentStep)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 타이틀 설정 (필요한 경우)
        (activity as? MainActivity)?.setToolbarTitle("매뉴얼")
        arguments?.let {
            shopId = it.getString(ARG_SHOP_ID, "")
            shopName = it.getString(ARG_SHOP_NAME, "")
            menuName = it.getString(ARG_MENU_NAME, "")
            menuImageResId = it.getInt(ARG_MENU_IMAGE)
            currentStep = it.getInt(ARG_CURRENT_STEP, 1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_menu_detail_step, container, false)

        // 뷰 초기화
        tvStepTitle = view.findViewById(R.id.tv_step_title)
        imgStepImage = view.findViewById(R.id.img_step)
        tvStepDescription = view.findViewById(R.id.tv_step_description)
        btnNext = view.findViewById(R.id.btn_next)
        btnManualComplete = view.findViewById(R.id.btn_manual_complete)

        // 데이터 클라이언트 초기화 - Fragment가 Activity에 attach된 후
        dataClient = Wearable.getDataClient(requireActivity())

        // API에서 매뉴얼 상세 정보 가져오기
        fetchManualDetail()

        // 다음 버튼 클릭 리스너
        btnNext.setOnClickListener {
            if (currentStep < totalSteps) {
                navigateToNextStep()
            }
        }

        // 매뉴얼 종료 버튼 클릭 리스너 - 메뉴 목록으로 돌아가기
        btnManualComplete.setOnClickListener {
            navigateToMenuList()
        }

        return view
    }

    private fun fetchManualDetail() {
        if (shopId.isEmpty() || menuName.isEmpty()) {
            Log.e(TAG, "가게 ID 또는 메뉴 이름이 비어 있습니다")
            Toast.makeText(context, "메뉴 정보가 올바르지 않습니다", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = apiRepository.getManualDetail(shopId, menuName)) {
                is NetworkResult.Success -> {
                    manualDetail = result.data
                    Log.d(TAG, "매뉴얼 상세 정보 로드 성공")

                    // API 응답에서 단계 정보 변환
                    convertApiStepsToUiSteps()

                    // 총 단계 수 업데이트
                    totalSteps = steps.size

                    // UI 업데이트
                    updateStepUI()
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "매뉴얼 상세 정보 가져오기 실패: ${result.message}")
                    Toast.makeText(context, "매뉴얼 정보를 불러오는데 실패했습니다", Toast.LENGTH_SHORT).show()
                }
                is NetworkResult.Loading -> {
                    // 로딩 중 처리
                }
            }
        }
    }

    private fun convertApiStepsToUiSteps() {
        steps.clear()

        manualDetail?.let { response ->
            // 단계 키를 숫자 순서대로 정렬 (step1, step2, ...)
            val sortedStepKeys = response.steps.keys
                .sortedBy { key -> key.replace("step", "").toIntOrNull() ?: 0 }

            // 정렬된 순서대로 Step 객체 생성
            sortedStepKeys.forEachIndexed { index, key ->
                val apiStep = response.steps[key]
                apiStep?.let { step ->
                    steps.add(
                        Step(
                            title = key, // step1, step2, ...
                            description = step.des,
                            imageUrl = step.img,
                            requiredItems = "" // API는 필요 도구 정보를 제공하지 않음
                        )
                    )
                }
            }
        }
    }

    private fun updateStepUI() {
        // 현재 단계가 유효한지 확인
        if (currentStep < 1 || currentStep > steps.size) {
            return
        }

        // 현재 스텝 정보
        val currentStepData = steps[currentStep - 1]

        // UI 업데이트
        tvStepTitle.text = "Step ${currentStep}"

        // 이미지 로드 (URL 또는 리소스 ID)
        if (!currentStepData.imageUrl.isNullOrEmpty()) {
            // URL 이미지 로드
            Glide.with(requireContext())
                .load(currentStepData.imageUrl)
                .error(R.drawable.img_shop_sample)
                .into(imgStepImage)
        } else {
            // 리소스 이미지 로드
            imgStepImage.setImageResource(currentStepData.imageResId)
        }

        tvStepDescription.text = currentStepData.description


        // 마지막 스텝인 경우 다음 버튼만 숨김, 매뉴얼 종료 버튼은 항상 표시
        if (currentStep == totalSteps) {
            btnNext.visibility = View.GONE
        } else {
            btnNext.visibility = View.VISIBLE
        }

        // 매뉴얼 종료 버튼은 항상 표시
        btnManualComplete.visibility = View.VISIBLE

        // 워치에 현재 단계 정보 전송
        sendStepInfoToWatch()
    }

    private fun navigateToNextStep() {
        if (currentStep < totalSteps) {
            val nextFragment = newInstance(
                shopId = shopId,
                shopName = shopName,
                menuName = menuName,
                menuImageResId = menuImageResId,
                currentStep = currentStep + 1
            )

            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_container, nextFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun navigateToPreviousStep() {
        // 이전 스텝으로는 백스택을 사용
        parentFragmentManager.popBackStack()
    }

    private fun navigateToMenuList() {
        // 메뉴 목록으로 돌아가기
        Toast.makeText(context, "매뉴얼 완료!", Toast.LENGTH_SHORT).show()

        if (currentStep == 1) {
            // 첫 번째 스텝이면 한 번만 뒤로가기 (메뉴 목록으로)
            parentFragmentManager.popBackStack()
        } else {
            // 여러 스텝을 탐색한 경우, 현재 스텝 체인을 모두 제거하고 메뉴 목록으로
            val menuListState = parentFragmentManager.getBackStackEntryAt(
                parentFragmentManager.backStackEntryCount - currentStep
            )
            parentFragmentManager.popBackStack(
                menuListState.id,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }
    }

    // 워치에 현재 단계 정보 전송
    private fun sendStepInfoToWatch() {
        if (steps.isEmpty() || currentStep > steps.size) return

        val currentStepData = steps[currentStep - 1]

        scope.launch {
            try {
                val dataClient = Wearable.getDataClient(requireContext())

                val request = PutDataMapRequest.create("/manual_step_update").run {
                    dataMap.putString("shop_id", shopId)
                    dataMap.putString("menu_name", menuName)
                    dataMap.putInt("current_step", currentStep)
                    dataMap.putInt("total_steps", steps.size)
                    dataMap.putString("image_url", currentStepData.imageUrl ?: "")
                    dataMap.putString("step_description", currentStepData.description)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                    asPutDataRequest()
                }

                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "워치에 단계 정보 전송 결과: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "워치에 단계 정보 전송 실패", e)
            }
        }
    }

    // 데이터 리스너 추가
    private val dataListener = DataClient.OnDataChangedListener { dataEvents ->
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val path = uri.path ?: ""

                if (path == "/manual_step_change") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val updatedShopId = dataMap.getString("shop_id", "")
                    val updatedMenuName = dataMap.getString("menu_name", "")

                    // 현재 보고 있는 매뉴얼과 일치하는지 확인
                    if (updatedShopId == shopId && updatedMenuName == menuName) {
                        val updatedStep = dataMap.getInt("current_step", 1)

                        // 현재 단계 업데이트 (UI 스레드에서 처리)
                        scope.launch(Dispatchers.Main) {
                            if (currentStep != updatedStep) {
                                currentStep = updatedStep
                                updateStepUI()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(dataListener)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(dataListener)
    }
}