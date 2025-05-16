package com.example.todak.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.MenuOcrItem
import com.example.todak.data.model.MenuResponse
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.OcrResponse
import com.example.todak.data.repository.ApiRepository
import com.example.todak.ui.activity.MainActivity
import com.example.todak.ui.adapter.MenuAdapter
import com.example.todak.ui.adapter.ReceiptItemAdapter
import com.google.gson.Gson
import kotlinx.coroutines.launch

class ReceiptResultFragment : Fragment() {

    private lateinit var tvOrderTitle: TextView
    private lateinit var rvOrderItems: RecyclerView
    private lateinit var tvManualTitle: TextView
    private lateinit var rvMenuManuals: RecyclerView
    private lateinit var btnConfirm: Button
    private lateinit var btnRescan: Button
    private lateinit var tvTotalAmount: TextView

    private lateinit var receiptAdapter: ReceiptItemAdapter
    private lateinit var menuAdapter: MenuAdapter

    private var menuItems: List<MenuOcrItem> = emptyList()
    private var storeId: String = ""
    private var shopName: String = "" // 매장 이름 추가
    private val apiRepository = ApiRepository()

    companion object {
        private const val TAG = "ReceiptResultFragment"
        private const val ARG_OCR_RESPONSE = "ocr_response"
        private const val ARG_STORE_ID = "store_id"
        private const val ARG_SHOP_NAME = "shop_name"

        fun newInstance(ocrResponse: OcrResponse, storeId: String, shopName: String): ReceiptResultFragment {
            val fragment = ReceiptResultFragment()
            val args = Bundle()

            // OcrResponse 객체와 storeId, shopName을 전달
            args.putString(ARG_OCR_RESPONSE, Gson().toJson(ocrResponse))
            args.putString(ARG_STORE_ID, storeId)
            args.putString(ARG_SHOP_NAME, shopName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_receipt_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 타이틀 설정 (필요한 경우)
        (activity as? MainActivity)?.setToolbarTitle("스캔 결과")

        // 뷰 초기화
        tvOrderTitle = view.findViewById(R.id.tv_order_title)
        rvOrderItems = view.findViewById(R.id.rv_order_items)
        tvManualTitle = view.findViewById(R.id.tv_manual_title)
        rvMenuManuals = view.findViewById(R.id.rv_menu_manuals)
        btnConfirm = view.findViewById(R.id.btn_confirm)
        btnRescan = view.findViewById(R.id.btn_rescan)
        tvTotalAmount = view.findViewById(R.id.tv_total_amount)

        // arguments에서 OCR 결과 데이터와 storeId 가져오기
        try {
            arguments?.getString(ARG_OCR_RESPONSE)?.let { jsonString ->
                val ocrResponse = Gson().fromJson(jsonString, OcrResponse::class.java)
                menuItems = ocrResponse.menus

                // 헤더 업데이트
                tvOrderTitle.text = "주문 내역 (${menuItems.size})"

                // 총 금액 계산
                calculateTotalAmount()
            }

            arguments?.getString(ARG_STORE_ID)?.let {
                storeId = it
                Log.d(TAG, "매장 ID: $storeId")
            }
            arguments?.getString(ARG_SHOP_NAME)?.let {
                shopName = it
                Log.d(TAG, "매장 이름: $shopName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR 응답 데이터 파싱 오류", e)
            Toast.makeText(context, "결과 데이터를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
        }

        setupOrderItemsRecyclerView()
        setupButtons()

        // 해당 매장의 메뉴 목록 가져오기
        if (storeId.isNotEmpty()) {
            fetchMenusByOrderItems()
        }
    }

    private fun setupOrderItemsRecyclerView() {
        receiptAdapter = ReceiptItemAdapter(menuItems)
        rvOrderItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = receiptAdapter
        }
    }

    private fun setupMenuManualsRecyclerView(menus: List<MenuResponse>) {
        // 그리드 레이아웃으로 메뉴 매뉴얼 표시
        val spanCount = 2 // 한 줄에 2개의 아이템 표시
        rvMenuManuals.layoutManager = GridLayoutManager(requireContext(), spanCount)

        menuAdapter = MenuAdapter(menus) { menuResponse ->
            // 메뉴 클릭 이벤트 처리
            navigateToMenuDetailStep(menuResponse)
        }
        rvMenuManuals.adapter = menuAdapter

        // 매뉴얼 제목 업데이트
        tvManualTitle.text = "메뉴 매뉴얼 (${menus.size})"
        tvManualTitle.visibility = View.VISIBLE
    }

    private fun setupButtons() {
        // 확인 버튼 클릭 리스너
        btnConfirm.setOnClickListener {
            try {
                // 현재 백스택 상태
                val fragmentManager = requireActivity().supportFragmentManager
                val backStackCount = fragmentManager.backStackEntryCount

                if (backStackCount >= 2) {
                    // 현재 화면에서 2단계 이전의 백스택 엔트리 ID 가져오기
                    val targetEntryIndex = backStackCount - 2
                    val targetEntry = fragmentManager.getBackStackEntryAt(targetEntryIndex)

                    // 해당 엔트리까지 팝
                    fragmentManager.popBackStack(
                        targetEntry.id,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                } else {
                    // 백스택에 충분한 항목이 없으면 그냥 한 번만 팝
                    fragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                Log.e(TAG, "백스택 처리 오류", e)
                // 오류 발생 시 기본 동작으로 돌아가기
                requireActivity().supportFragmentManager.popBackStack()
            }
        }

        // 다시 스캔 버튼 클릭 리스너
        btnRescan.setOnClickListener {
            // 이전 화면(스캐너)으로 돌아가기
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun calculateTotalAmount() {
        var total = 0

        for (item in menuItems) {
            try {
                // "count" 필드의 숫자 부분만 추출하여 합산
                val countStr = item.count.replace("[^0-9]".toRegex(), "")
                if (countStr.isNotEmpty()) {
                    total += countStr.toInt()
                }
            } catch (e: Exception) {
                Log.e(TAG, "금액 변환 오류: ${item.count}", e)
            }
        }

        // 총액 표시 (천 단위 쉼표 추가)
        tvTotalAmount.text = "총 갯수: ${String.format("%,d", total)}"
    }

    private fun fetchMenusByOrderItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = apiRepository.getMenus(storeId)) {
                is NetworkResult.Success -> {
                    val allMenus = result.data

                    // OCR로 인식된 메뉴 이름과 일치하는 메뉴만 필터링
                    val orderMenuTitles = menuItems.map { it.title.trim().lowercase() }
                    val filteredMenus = allMenus.filter { menu ->
                        orderMenuTitles.any { orderTitle ->
                            menu.title.trim().lowercase().contains(orderTitle) ||
                                    orderTitle.contains(menu.title.trim().lowercase())
                        }
                    }

                    if (filteredMenus.isNotEmpty()) {
                        // 필터링된 메뉴로 RecyclerView 설정
                        setupMenuManualsRecyclerView(filteredMenus)
                    } else {
                        // 일치하는 메뉴가 없을 경우, 모든 메뉴 표시
                        Log.d(TAG, "주문 메뉴와 일치하는 메뉴가 없어 모든 메뉴를 표시합니다")
                        setupMenuManualsRecyclerView(allMenus)
                    }

                    Log.d(TAG, "메뉴 로드 성공: 전체 ${allMenus.size}개, 필터링 ${filteredMenus.size}개")
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "메뉴 목록 가져오기 실패: ${result.message}")
                    tvManualTitle.visibility = View.GONE
                    Toast.makeText(context, "메뉴 매뉴얼을 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
                }
                is NetworkResult.Loading -> {
                    // 로딩 중 처리
                }
            }
        }
    }

    private fun navigateToMenuDetailStep(menuResponse: MenuResponse) {
        // 단계별 매뉴얼 페이지로 이동
        try {
            val detailStepFragment = MenuDetailStepFragment.newInstance(
                shopId = storeId,
                shopName = shopName, // 매장 이름을 빈 문자열 대신 변수로 전달
                menuName = menuResponse.title,
                menuImageResId = R.drawable.img_shop_sample, // 기본 이미지 리소스 사용
                currentStep = 1 // 초기 단계는 1로 설정
            )

            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_container, detailStepFragment)
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "MenuDetailStepFragment 이동 오류", e)
            // MenuDetailStepFragment가 아직 구현되지 않았을 경우
            Toast.makeText(context, "${menuResponse.title} 메뉴얼을 확인합니다", Toast.LENGTH_SHORT).show()
        }
    }
}