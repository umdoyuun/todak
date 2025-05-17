package com.example.todak.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.Center
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.UserProfile
import com.example.todak.data.repository.ApiRepository
import com.example.todak.ui.activity.MainActivity
import com.example.todak.ui.adapter.CenterAdapter
import com.example.todak.util.SessionManager
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private val TAG = "ProfileFragment"
    private val profileRepository = ApiRepository()

    // UI 요소들
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvBirth: TextView
    private lateinit var tvGender: TextView
    private lateinit var tvNeeds: TextView
    private lateinit var rvCenters: RecyclerView
    private lateinit var centerAdapter: CenterAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.setToolbarTitle("내 정보")

        // UI 요소 초기화
        initializeViews(view)

        // 리사이클러뷰 설정
        setupRecyclerView()

        // 사용자 데이터 로드
        loadUserData()

        // 센터 데이터 로드
        loadCenters()
    }

    private fun initializeViews(view: View) {
        tvName = view.findViewById(R.id.tv_name)
        tvEmail = view.findViewById(R.id.tv_email)
        tvPhone = view.findViewById(R.id.tv_phone)
        tvAddress = view.findViewById(R.id.tv_address)
        tvBirth = view.findViewById(R.id.tv_birth)
        tvGender = view.findViewById(R.id.tv_gender)
        tvNeeds = view.findViewById(R.id.tv_needs)
        rvCenters = view.findViewById(R.id.rv_centers)
    }

    private fun setupRecyclerView() {
        centerAdapter = CenterAdapter(emptyList())
        rvCenters.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = centerAdapter
        }
    }

    private fun loadUserData() {
        // 세션 체크
        if (!checkUserSession()) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = SessionManager.getUserId() ?: return

        lifecycleScope.launch {
            when (val result = profileRepository.getUserProfile(userId)) {
                is NetworkResult.Success -> {
                    displayUserProfile(result.data)
                    Log.d(TAG, "사용자 프로필 로드 성공: ${result.data.name}")
                }
                is NetworkResult.Error -> {
                    Toast.makeText(context, "사용자 정보를 불러오는데 실패했습니다: ${result.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "사용자 프로필 로드 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    // 로딩 처리
                }
            }
        }
    }

    private fun loadCenters() {
        // 세션 체크
        if (!checkUserSession()) return

        lifecycleScope.launch {
            when (val result = profileRepository.getUserCenters()) {
                is NetworkResult.Success -> {
                    centerAdapter.updateCenters(result.data)
                    Log.d(TAG, "센터 정보 로드 성공: ${result.data.size}개")
                }
                is NetworkResult.Error -> {
                    Toast.makeText(context, "센터 정보를 불러오는데 실패했습니다: ${result.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "센터 정보 로드 실패: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    // 로딩 처리
                }
            }
        }
    }

    private fun displayUserProfile(profile: UserProfile) {
        tvName.text = profile.name
        tvEmail.text = profile.email

        // null 가능성이 있는 필드들에 대한 처리
        tvPhone.text = profile.phone ?: "등록된 번호가 없습니다"
        tvAddress.text = profile.addr ?: "등록된 주소가 없습니다"
        tvBirth.text = profile.birth ?: "등록된 생년월일이 없습니다"
        tvGender.text = profile.gender ?: "등록된 성별이 없습니다"
        tvNeeds.text = profile.needs ?: "등록된 요구사항이 없습니다"
    }

    private fun checkUserSession(): Boolean {
        val accessToken = SessionManager.getAuthToken()
        val userId = SessionManager.getUserId()
        return !accessToken.isNullOrEmpty() && !userId.isNullOrEmpty()
    }

    companion object {
        fun newInstance() = ProfileFragment()
    }
}