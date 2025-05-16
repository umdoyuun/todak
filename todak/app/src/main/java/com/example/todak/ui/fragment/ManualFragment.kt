package com.example.todak.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.repository.ApiRepository
import com.example.todak.ui.adapter.Shop
import com.example.todak.ui.adapter.ShopAdapter
import kotlinx.coroutines.launch

class ManualFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var shopAdapter: ShopAdapter
    private val apiRepository = ApiRepository()

    private val TAG = "ManualFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_manual, container, false)

        // RecyclerView 초기화
        recyclerView = view.findViewById(R.id.recycler_shops)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Adapter 설정
        shopAdapter = ShopAdapter(emptyList()) { shop ->
            // 아이템 클릭 시 MenuListFragment로 이동
            val menuListFragment = MenuListFragment.newInstance(
                shopId = shop.id,
                shopName = shop.name,
            )

            // 프래그먼트 트랜잭션 실행
            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_container, menuListFragment)
                .addToBackStack(null)
                .commit()
        }
        recyclerView.adapter = shopAdapter

        fetchStoresFromApi()

        return view
    }

    private fun fetchStoresFromApi() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = apiRepository.getStores()) {
                is NetworkResult.Success -> {
                    val storesList = result.data

                    // status가 "active"인 가게만 필터링하고 Shop 객체로 변환
                    val shops = storesList
                        .filter { it.store_info.status == "active" }
                        .map { store ->
                            Shop(
                                id = store.store_id,
                                name = store.store_info.store_name,
                                imageResId = R.drawable.img_shop_sample // 기본 이미지 사용

                            )
                        }

                    // UI 업데이트
                    shopAdapter = ShopAdapter(shops) { shop ->
                        // 아이템 클릭 시 MenuListFragment로 이동
                        val menuListFragment = MenuListFragment.newInstance(
                            shopId = shop.id,
                            shopName = shop.name,
                        )

                        // 프래그먼트 트랜잭션 실행
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.frame_container, menuListFragment)
                            .addToBackStack(null)
                            .commit()
                    }
                    recyclerView.adapter = shopAdapter

                    // 데이터가 없을 경우 처리
                    if (shops.isEmpty()) {
                        Toast.makeText(context, "표시할 가게가 없습니다", Toast.LENGTH_SHORT).show()
                    }
                }

                is NetworkResult.Error -> {
                    // 에러 메시지 표시
                    Log.e(TAG, "가게 목록 가져오기 실패: ${result.message}")
                    Toast.makeText(context, "가게 목록을 불러오는데 실패했습니다: ${result.message}", Toast.LENGTH_SHORT).show()
                }

                is NetworkResult.Loading -> {
                    // 로딩 중일 때의 처리 (필요한 경우)
                }
            }
        }
    }

    // 로딩 표시를 위한 함수 (필요한 경우)
    private fun showLoading(isLoading: Boolean) {
        // 프로그레스 바 등을 표시하거나 숨기는 로직
    }
}