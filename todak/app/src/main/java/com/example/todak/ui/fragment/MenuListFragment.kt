package com.example.todak.ui.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.ui.adapter.MenuAdapter
import com.example.todak.R
import com.example.todak.data.model.MenuListResponse
import com.example.todak.data.model.MenuResponse
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.repository.ApiRepository
import com.example.todak.ui.activity.MainActivity
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch


class MenuListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var menuAdapter: MenuAdapter
    private lateinit var btnScanReceipt: Button
    private lateinit var tabLayout: TabLayout
    private val apiRepository = ApiRepository()

    private var shopId: String = ""
    private var shopName: String = ""

    // 카테고리별 메뉴 데이터 저장
    private val categorizedMenus = mutableMapOf<String, List<MenuResponse>>()
    private val categories = mutableListOf<String>()
    private var allMenus = listOf<MenuResponse>()


    private val TAG = "MenuListFragment"

    companion object {
        private const val ARG_SHOP_ID = "shop_id"
        private const val ARG_SHOP_NAME = "shop_name"

        fun newInstance(shopId: String, shopName: String): MenuListFragment {
            val fragment = MenuListFragment()
            val args = Bundle()
            args.putString(ARG_SHOP_ID, shopId)
            args.putString(ARG_SHOP_NAME, shopName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 타이틀 설정 (필요한 경우)
        (activity as? MainActivity)?.setToolbarTitle("메뉴 목록")
        arguments?.let {
            shopId = it.getString(ARG_SHOP_ID, "")
            shopName = it.getString(ARG_SHOP_NAME, "")
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_menu_list, container, false)


        recyclerView = view.findViewById(R.id.recycler_menus)
        btnScanReceipt = view.findViewById(R.id.btn_scan_receipt)
        tabLayout = view.findViewById(R.id.tab_layout)  // 탭 레이아웃 초기화

        // 타이틀 설정 (필요한 경우)
        (activity as? MainActivity)?.setToolbarTitle(shopName)

        // 영수증 스캔하기 버튼 클릭 리스너
        btnScanReceipt.setOnClickListener {
            // ReceiptScannerFragment로 이동하면서 shopId 전달
            val receiptScannerFragment = ReceiptScannerFragment().apply {
                arguments = Bundle().apply {
                    putString("store_id", shopId)
                    putString("shop_name", shopName)
                }
            }
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.frame_container, receiptScannerFragment)
                .addToBackStack(null)  // 백스택에 추가하여 뒤로가기 가능하도록 함
                .commit()
        }

        // 리사이클러뷰 설정 - 그리드 레이아웃으로 변경
        val spanCount = 2 // 한 줄에 2개의 아이템 표시
        recyclerView.layoutManager = GridLayoutManager(context, spanCount)

        // 탭 선택 리스너 설정
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val category = it.text.toString()
                    displayMenusByCategory(category)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // API에서 메뉴 목록 가져오기
        fetchMenusFromApi()

        return view
    }

    private fun navigateToMenuDetailStep(menu: MenuResponse) {
        // 단계별 매뉴얼 페이지로 이동
        try {
            val detailStepFragment = MenuDetailStepFragment.newInstance(
                shopId = shopId,
                shopName = shopName,
                menuName = menu.title,
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
            Toast.makeText(context, "${menu.title} 메뉴얼을 확인합니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchMenusFromApi() {
        if (shopId.isEmpty()) {
            Log.e(TAG, "가게 ID가 비어 있습니다")
            Toast.makeText(context, "가게 정보가 올바르지 않습니다", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // 로딩 표시 시작
            // showLoading(true)

            Log.d(TAG, "메뉴 목록 가져오기 시작: 가게 ID = $shopId")

            when (val result = apiRepository.getMenus(shopId)) {
                is NetworkResult.Success -> {
                    allMenus = result.data
                    Log.d(TAG, "메뉴 목록 로드 성공: ${allMenus.size}개 항목")

                    // 카테고리별로 메뉴 분류
                    categorizeMenus(allMenus)

                    // 탭 레이아웃 설정
                    setupCategoryTabs()

                    // 첫 번째 카테고리의 메뉴 표시
                    if (categories.isNotEmpty()) {
                        displayMenusByCategory(categories[0])
                        // 첫 번째 탭 선택
                        tabLayout.getTabAt(0)?.select()
                    } else {
                        // 카테고리가 없는 경우 전체 메뉴 표시
                        displayMenusByCategory("전체")
                    }

                    // 데이터가 없을 경우 처리
                    if (allMenus.isEmpty()) {
                        Toast.makeText(context, "표시할 메뉴가 없습니다", Toast.LENGTH_SHORT).show()
                    }
                }

                is NetworkResult.Error -> {
                    // 에러 메시지 표시
                    Log.e(TAG, "메뉴 목록 가져오기 실패: ${result.message}")
                    Toast.makeText(context, "메뉴 목록을 불러오는데 실패했습니다", Toast.LENGTH_SHORT).show()
                }

                is NetworkResult.Loading -> {
                    // 로딩 중 처리
                }
            }

            // 로딩 표시 종료
            // showLoading(false)
        }
    }
    private fun categorizeMenus(menus: List<MenuResponse>) {
        // 메뉴 카테고리별로 분류
        categorizedMenus.clear()
        categories.clear()

        // 카테고리별로 그룹화
        menus.groupBy { it.category }.forEach { (category, menuList) ->
            val categoryName = if (category.isNullOrEmpty()) "기타" else category
            categorizedMenus[categoryName] = menuList
            categories.add(categoryName)
        }

        // "전체" 카테고리 추가
        categories.add(0, "전체")
        categorizedMenus["전체"] = menus

        Log.d(TAG, "카테고리 분류 완료: ${categories.size}개 카테고리")
    }

    private fun setupCategoryTabs() {
        // 기존 탭 모두 제거
        tabLayout.removeAllTabs()

        // 카테고리별 탭 추가
        categories.forEach { category ->
            tabLayout.addTab(tabLayout.newTab().setText(category))
        }

        // 탭 레이아웃 표시
        tabLayout.visibility = if (categories.size > 1) View.VISIBLE else View.GONE
    }

    private fun displayMenusByCategory(category: String) {
        // 선택된 카테고리의 메뉴 가져오기
        val menusToDisplay = categorizedMenus[category] ?: allMenus

        // 어댑터 설정
        menuAdapter = MenuAdapter(menusToDisplay) { menuResponse ->
            // 메뉴 클릭 이벤트 처리
            navigateToMenuDetailStep(menuResponse)
        }
        recyclerView.adapter = menuAdapter

        Log.d(TAG, "$category 카테고리 메뉴 표시: ${menusToDisplay.size}개 항목")
    }
}