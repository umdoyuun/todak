package com.example.todak.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.Group
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.ChatMessage
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.repository.ApiRepository
import com.example.todak.data.repository.ChatRepository
import com.example.todak.ui.adapter.ChatAdapter
import com.example.todak.ui.fragment.EmojiFragment
import com.example.todak.ui.fragment.HomeFragment
import com.example.todak.ui.fragment.ManualFragment
import com.example.todak.ui.fragment.MissionFragment
import com.example.todak.ui.fragment.ScheduleFragment
import com.example.todak.util.WakeWordService
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import com.example.todak.data.repository.FcmApiRepository
import com.example.todak.receiver.SMSReceiver
import com.example.todak.ui.fragment.RoutineFragment
import com.example.todak.ui.fragment.SettingFragment
import com.example.todak.ui.modal.EmotionCheckModal
import com.example.todak.util.AppStateMonitor
import com.example.todak.util.FcmService
import com.example.todak.util.LocationHelper
import com.example.todak.util.NotificationActionReceiver
import com.example.todak.util.SMSManager
import com.example.todak.util.WearableService
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity(), WakeWordService.VoiceResponseListener {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var toolbarTitle: TextView

    private var chatDialog: Dialog? = null

    // 음성 인식 서비스 관련 변수 추가
    private var wakeWordService: WakeWordService? = null
    private var isBound = false
    private var isVoiceServiceActive = false // 음성 인식 활성화 상태 추적
    private var voiceResponseText: String? = null // 음성 인식 결과 저장 변수

    private lateinit var dynamicSmsReceiver: SMSReceiver
    private var isDynamicReceiverRegistered = false

    // SMS 및 위치 관리자 추가
    private lateinit var smsManager: SMSManager
    private lateinit var locationHelper: LocationHelper

    private lateinit var backIcon: ImageView

    // 서비스 연결을 위한 ServiceConnection 추가
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WakeWordService.LocalBinder
            wakeWordService = binder.getService()
            wakeWordService?.setVoiceResponseListener(this@MainActivity)
            isBound = true
            isVoiceServiceActive = true

            Log.d(TAG, "서비스 연결됨")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            wakeWordService = null
            isBound = false
            isVoiceServiceActive = false

            Log.d(TAG, "서비스 연결 해제됨")
        }
    }

    // 호출어 감지 이벤트 수신을 위한 BroadcastReceiver 추가
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                val keywordIndex = intent.getIntExtra(WakeWordService.EXTRA_KEYWORD_INDEX, -1)

                Log.d(TAG, "호출어 감지됨! 인덱스: $keywordIndex")

                // 호출어 감지 시 진행 중인 작업을 알려주는 토스트 메시지 표시
                Toast.makeText(this@MainActivity, "토닥이 듣고 있어요...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 타이틀 설정 메서드 분리 (유지)
    fun setToolbarTitle(title: String) {
        if (::toolbarTitle.isInitialized) {
            toolbarTitle.text = title
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 툴바 타이틀 초기화
        toolbarTitle = findViewById(R.id.toolbar_title)

        // 뒤로가기 아이콘 초기화
        backIcon = findViewById(R.id.back_icon)
        backIcon.setOnClickListener {
            onBackPressed()  // 뒤로가기 동작 실행
        }

        // 백 스택 변경 리스너 추가
        supportFragmentManager.addOnBackStackChangedListener {
            updateBackIconVisibility()
        }

        // 초기 상태 설정
        updateBackIconVisibility()

        // 기존 네비게이션 설정
        findViewById<View>(R.id.nav_home).setOnClickListener {
            updateNavSelection(it)
            replaceFragment(HomeFragment(), "")
        }
        findViewById<View>(R.id.nav_schedule).setOnClickListener {
            updateNavSelection(it)
            replaceFragment(ScheduleFragment(), "일정")
        }
        findViewById<View>(R.id.nav_manual).setOnClickListener {
            updateNavSelection(it)
            replaceFragment(ManualFragment(), "가게 목록")
        }
        findViewById<View>(R.id.nav_mission).setOnClickListener {
            updateNavSelection(it)
            replaceFragment(MissionFragment(), "미션 목록")
        }
        findViewById<View>(R.id.nav_routine).setOnClickListener {
            updateNavSelection(it)
            replaceFragment(RoutineFragment(), "루틴")
        }

        val settingIcon = findViewById<ImageView>(R.id.setting_icon)
        settingIcon.setOnClickListener {
            replaceFragment(SettingFragment(), "설정")
        }

        Log.d(TAG, "MainActivity onCreate - Intent: ${intent?.action}, Extras: ${intent?.extras}")
        handleIntent(intent)

        // 채팅 버튼
        val chatButton = findViewById<CardView>(R.id.chat_button)
        chatButton.setOnClickListener {
            showChatModal()
        }

        // 앱 상태 모니터 초기화 - application 객체 사용
        AppStateMonitor.init(application)

        //checkSmsPermissions()

        // 음성 인식 서비스 시작 (앱 시작 시 자동으로 호출어 인식 시작)
        checkPermissionsAndStartWakeWordService()

        // SMS 및 위치 관련 권한 확인 및 처리
        checkPermissionsAndProcessSMS()

        val chatRefreshFilter = IntentFilter(WakeWordService.ACTION_REFRESH_CHAT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                chatRefreshReceiver,
                chatRefreshFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(chatRefreshReceiver, chatRefreshFilter)
        }

        startService(Intent(this, WearableService::class.java))

        checkFcmToken()

        registerSmsReceiver()

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent == null) {
            Log.d(TAG, "handleIntent: 인텐트가 null입니다")
            return
        }

        Log.d(TAG, "handleIntent 호출됨: ${intent.action}, 엑스트라: ${intent.extras?.keySet()?.joinToString()}")

        if (intent.action == NotificationActionReceiver.ACTION_REFRESH_SCHEDULE) {
            Log.d(TAG, "일정 새로고침 인텐트 처리")
            updateNavSelection(findViewById(R.id.nav_schedule))
            replaceFragment(ScheduleFragment(), "일정")
            return
        }
        // 일정 상세 페이지 표시 여부 확인
        val showScheduleFragment = intent.getBooleanExtra("show_schedule_fragment", false)
        val scheduleId = intent.getStringExtra("schedule_id")
        val action = intent.getStringExtra("action")
        val showDetail = intent.getBooleanExtra("show_schedule_detail", false)

        // 일정 ID가 있고 일정 페이지 표시가 요청된 경우
        if (showScheduleFragment && scheduleId != null) {
            Log.d(TAG, "일정 페이지로 이동: 일정 ID=$scheduleId, 액션=$action")
            updateNavSelection(findViewById(R.id.nav_schedule))
            showScheduleFragment(scheduleId, action)
            return
        }

        // 원래 딥링크 처리 코드
        val data = intent.data
        if (data != null && data.scheme == "borderlineiq" && data.host == "schedule" && data.path == "/detail") {
            val deepLinkScheduleId = data.getQueryParameter("id")
            val deepLinkAction = data.getQueryParameter("action")

            if (deepLinkScheduleId != null) {
                Log.d(TAG, "딥링크를 통한 일정 페이지 이동: 일정 ID=$deepLinkScheduleId, 액션=$deepLinkAction")
                updateNavSelection(findViewById(R.id.nav_schedule))
                showScheduleFragment(deepLinkScheduleId, deepLinkAction)
                return
            }
        }

        // 감정 등록 인텐트 처리
        if (intent?.action == "EMOTION_REGISTRATION") {
            val context = intent.getStringExtra("context") ?: ""
            val scheduleId = intent.getStringExtra("schedule_id") ?: ""

            // 감정 등록 모달 표시
            val message = "일정을 완료했어요! 감정은 어땠나요?"
            EmotionCheckModal.show(this, context, message)
            return
        }

        // 인텐트에 특별한 지시가 없으면 기본 화면 표시
        if (intent.action == null) {
            Log.d(TAG, "기본 화면(홈) 표시")
            clearNavSelection()
            replaceFragment(HomeFragment(), "")
        }
    }

    // 저장된 모든 SMS 처리
    private fun processAllSavedSMS() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                smsManager.processAllSavedSMS()
                Log.d(TAG, "저장된 SMS 처리 완료")
            } catch (e: Exception) {
                Log.e(TAG, "저장된 SMS 처리 오류: ${e.message}")
            }
        }
    }

    // 저장된 SMS 처리 함수
    private fun processStoredSMS() {
        lifecycleScope.launch {
            try {
                smsManager.processAllSavedSMS()
                Log.d(TAG, "저장된 SMS 처리 완료")
            } catch (e: Exception) {
                Log.e(TAG, "저장된 SMS 처리 오류: ${e.message}")
            }
        }
    }

    private fun showScheduleFragment(scheduleId: String?, action: String?) {
        Log.d(TAG, "showScheduleFragment 호출: 일정 ID=$scheduleId, 액션=$action")

        val scheduleFragment = ScheduleFragment()

        if (scheduleId != null) {
            val args = Bundle().apply {
                putString("schedule_id", scheduleId)
                putString("action", action)
                putBoolean("show_detail", true)
            }
            scheduleFragment.arguments = args
        }

        replaceFragment(scheduleFragment, "일정")
    }

    private fun checkFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "FCM 토큰: $token")

                lifecycleScope.launch {
                    try {
                        val fcmRepository = FcmApiRepository()
                        val result = fcmRepository.registerDeviceToken(token)
                        if (result is NetworkResult.Success) {
                            Log.d("FCM", "FCM 토큰 서버 등록 성공")
                        } else if (result is NetworkResult.Error) {
                            Log.e("FCM", "FCM 토큰 서버 등록 실패: ${result.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("FCM", "FCM 토큰 서버 등록 오류", e)
                    }
                }
            } else {
                Log.w("FCM", "FCM 토큰 가져오기 실패", task.exception)
            }
        }
    }

    // 권한 확인 및 서비스 시작 함수 추가
    private fun checkPermissionsAndStartWakeWordService() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        // Android 13 (Tiramisu) 이상에서는 POST_NOTIFICATIONS 권한 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, REQUEST_WAKEWORD_PERMISSIONS)
        } else {
            startWakeWordService()
        }
    }

    // SMS 및 위치 관련 권한 확인 및 처리 함수
    private fun checkPermissionsAndProcessSMS() {
        val permissions = listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "SMS 관리자 및 위치 헬퍼 권한 요청")
            ActivityCompat.requestPermissions(this, permissionsToRequest, REQUEST_SMS_PERMISSIONS)
        } else {
            // SMS 관리자 및 위치 헬퍼 초기화
            Log.d(TAG, "SMS 관리자 및 위치 헬퍼 초기화")
            setupSmsComponents()
            processStoredSMS()
        }
    }

    private fun checkSmsPermissions(): Boolean {
        val receivePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val readPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        Log.e(TAG, "SMS 권한 상태: RECEIVE_SMS=$receivePermission, READ_SMS=$readPermission")

        return receivePermission && readPermission
    }

    // SMS 컴포넌트 설정 함수
    private fun setupSmsComponents() {
        // SMS 관리자 및 위치 헬퍼 초기화
        Log.d(TAG, "SMS 컴포넌트 설정")
        smsManager = SMSManager(this)
        locationHelper = LocationHelper(this)
    }

    // 서비스 시작 함수 추가
    private fun startWakeWordService() {
        val serviceIntent = Intent(this, WakeWordService::class.java)

        // 서비스 시작
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // 서비스에 바인딩
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        isVoiceServiceActive = true

        Log.d(TAG, "음성 인식 서비스 시작됨")
    }

    // 서비스 중지 함수 추가
    private fun stopWakeWordService() {
        // 서비스 바인딩 해제
        if (isBound) {
            wakeWordService?.setVoiceResponseListener(null)
            unbindService(serviceConnection)
            isBound = false
        }

        // 서비스 중지
        val serviceIntent = Intent(this, WakeWordService::class.java)
        stopService(serviceIntent)

        isVoiceServiceActive = false

        Log.d(TAG, "음성 인식 서비스 중지됨")
    }

    // 권한 요청 결과 처리 함수 수정
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_WAKEWORD_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // 음성 인식 관련 권한이 허용됨
                    startWakeWordService()
                } else {
                    // 권한 거부됨
                    Toast.makeText(this, "음성 인식을 위해 필요한 권한이 거부되었습니다", Toast.LENGTH_LONG).show()
                    isVoiceServiceActive = false
                }
            }
            REQUEST_SMS_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // SMS 및 위치 관련 권한이 허용됨
                    Log.e(TAG, "SMS 관련 권한 허용됨, 컴포넌트 설정 및 저장된 SMS 처리")
                    setupSmsComponents()
                    processStoredSMS()
                } else {
                    // 권한 거부됨
                    Log.e(TAG, "SMS 관련 권한 거부됨")
                    Toast.makeText(this, "SMS 처리 및 위치 정보를 위한 권한이 거부되었습니다", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // WakeWordService.VoiceResponseListener 인터페이스 구현
    override fun onVoiceResponseReceived(response: String) {
        Log.d(TAG, "음성 응답 수신: $response")

        // 음성 응답 저장
        voiceResponseText = response

        // 채팅 모달이 이미 열려있으면 바로 표시
        if (chatDialog != null && chatDialog!!.isShowing) {
            updateChatModalWithVoiceResponse()
        } else {
            // 채팅 모달이 닫혀있으면 모달 열고 결과 표시
            showChatModal()
        }
    }

    // 음성 결과를 채팅 모달에 표시하는 함수 추가
    private fun updateChatModalWithVoiceResponse() {
        chatDialog?.let { dialog ->
            voiceResponseText?.let { text ->
                // 채팅 입력 필드에 음성 인식 결과 설정
                val chatInput = dialog.findViewById<EditText>(R.id.chat_input)
                chatInput.setText(text)

                // 입력 필드가 포커스를 받도록 설정
                chatInput.requestFocus()

                // 음성 응답 텍스트 초기화
                voiceResponseText = null
            }
        }
    }

    // 뒤로가기 버튼 두 번 클릭 관련 변수 추가
    private var backPressedTime: Long = 0
    private val backPressedTimeInterval: Long = 2000 // 2초 이내에 두 번 클릭해야 함

    @Deprecated("Deprecated in Java")
    // 기존 onBackPressed 함수 유지, 아이콘 업데이트 추가
    override fun onBackPressed() {
        // 백스택에 프래그먼트가 있는지 확인
        if (supportFragmentManager.backStackEntryCount > 0) {
            // 백스택이 있으면 일반적인 뒤로가기 작동 (프래그먼트 pop)
            super.onBackPressed()
            // 뒤로가기 후 아이콘 상태 업데이트
            updateBackIconVisibility()
        } else {
            // 현재 표시된 프래그먼트 확인
            val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_container)

            // 현재 프래그먼트가 HomeFragment가 아닐 경우에만 홈으로 이동
            if (currentFragment != null && currentFragment !is HomeFragment) {
                clearNavSelection()
                replaceFragment(HomeFragment(), "")
                // 홈으로 이동 후 아이콘 상태 업데이트
                updateBackIconVisibility()
            } else {
                // 홈 화면에서의 뒤로가기 - 두 번 클릭 확인
                if (backPressedTime + backPressedTimeInterval > System.currentTimeMillis()) {
                    // 짧은 시간 내에 두 번째 클릭이면 앱 종료
                    super.onBackPressed()
                } else {
                    // 첫 번째 클릭이면 안내 메시지 표시
                    backPressedTime = System.currentTimeMillis()
                    Toast.makeText(this, "한 번 더 누르면 종료됩니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 뒤로가기 아이콘 표시 여부 업데이트
    private fun updateBackIconVisibility() {
        // 백 스택 크기 확인
        val backStackEntryCount = supportFragmentManager.backStackEntryCount

        // 현재 프래그먼트 확인
        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_container)
        val isHomeFragment = currentFragment is HomeFragment

        // 백 스택이 있거나 현재 홈 프래그먼트가 아닐 경우에만 뒤로가기 아이콘 표시
        backIcon.visibility = if (backStackEntryCount > 0 || (currentFragment != null && !isHomeFragment))
            View.VISIBLE else View.GONE
    }

    // 기존 replaceFragment 메서드 수정
    fun replaceFragment(fragment: Fragment, title: String = "") {

        // 타이틀 설정
        setToolbarTitle(title)

        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.frame_container, fragment)

        if (fragment is HomeFragment) {
            // 홈 프래그먼트로 이동할 때는 백 스택 비우기
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        } else {
            // 다른 프래그먼트는 백 스택에 추가
            transaction.addToBackStack(null)
        }

        transaction.commit()

        // 프래그먼트 교체 후 뒤로가기 아이콘 상태 업데이트
        updateBackIconVisibility()
    }

    fun updateNavSelection(selectedView: View) {
        val navItems = listOf(
            findViewById<View>(R.id.nav_home),
            findViewById<View>(R.id.nav_schedule),
            findViewById<View>(R.id.nav_manual),
            findViewById<View>(R.id.nav_mission),
            findViewById<View>(R.id.nav_routine)
        )
        navItems.forEach { item ->
            (item as LinearLayout).apply {
                (getChildAt(0) as ImageView).setColorFilter(
                    ContextCompat.getColor(this@MainActivity, R.color.brown)
                )
                (getChildAt(1) as TextView).setTextColor(
                    ContextCompat.getColor(this@MainActivity, R.color.brown)
                )
            }
        }
        (selectedView as LinearLayout).apply {
            (getChildAt(0) as ImageView).setColorFilter(
                ContextCompat.getColor(this@MainActivity, R.color.black)
            )
            (getChildAt(1) as TextView).setTextColor(
                ContextCompat.getColor(this@MainActivity, R.color.black)
            )
        }
    }

    fun clearNavSelection() {
        val navItems = listOf(
            findViewById<View>(R.id.nav_schedule),
            findViewById<View>(R.id.nav_manual),
            findViewById<View>(R.id.nav_mission),
            findViewById<View>(R.id.nav_routine)
        )

        navItems.forEach { item ->
            (item as LinearLayout).apply {
                (getChildAt(0) as ImageView).setColorFilter(
                    ContextCompat.getColor(this@MainActivity, R.color.brown)
                )
                (getChildAt(1) as TextView).setTextColor(
                    ContextCompat.getColor(this@MainActivity, R.color.brown)
                )
            }
        }
    }

    private fun showChatModal() {
        // 이미 모달이 표시되어 있다면 음성 결과 업데이트
        if (chatDialog != null && chatDialog!!.isShowing) {
            updateChatModalWithVoiceResponse()
            return
        }

        // 새 대화상자 생성
        chatDialog = Dialog(this, R.style.ChatDialogTheme)
        chatDialog?.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.modal_chat)

            // 채팅 대화상자가 화면 중앙에 표시되도록 설정
            window?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

                // 채팅 대화상자 위치를 중앙으로 설정
                setGravity(Gravity.CENTER)

                // 키보드가 화면을 가리지 않도록 설정
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

                // 대화상자 배경을 투명하게 설정
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }

            setCanceledOnTouchOutside(true)

            // 채팅 관련 뷰 초기화
            val chatRecyclerView = findViewById<RecyclerView>(R.id.chat_recycler_view)
            val emptyChatGroup = findViewById<Group>(R.id.empty_chat_group)
            val chatInput = findViewById<EditText>(R.id.chat_input)
            val sendButton = findViewById<ImageButton>(R.id.send_button)
            val clearChatButton = findViewById<ImageButton>(R.id.clear_chat_button)

            // 닫기 버튼 초기화 및 이벤트 설정
            val closeButton = findViewById<ImageButton>(R.id.close_button)
            closeButton.setOnClickListener {
                dismiss() // 채팅 모달 닫기
            }

            // 채팅 어댑터 초기화
            val chatAdapter = ChatAdapter()

            // RecyclerView 설정
            chatRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@MainActivity).apply {
                    stackFromEnd = true // 새 메시지가 아래에 추가됨
                }
                adapter = chatAdapter

                // 키보드가 나올 때 RecyclerView가 스크롤되도록 설정
                addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                    if (bottom < oldBottom) {
                        post {
                            if (adapter?.itemCount ?: 0 > 0) {
                                scrollToPosition(adapter?.itemCount?.minus(1) ?: 0)
                            }
                        }
                    }
                }
            }

            // 채팅 저장소 초기화
            val chatRepository = ChatRepository()

            // 기존 채팅 불러오기
            val savedMessages = chatRepository.getSavedMessages(this@MainActivity)
            if (savedMessages.isNotEmpty()) {
                chatAdapter.addMessages(savedMessages)
                emptyChatGroup.visibility = View.GONE
                chatRecyclerView.visibility = View.VISIBLE
                clearChatButton.visibility = View.VISIBLE
                chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            } else {
                emptyChatGroup.visibility = View.VISIBLE
                chatRecyclerView.visibility = View.GONE
                clearChatButton.visibility = View.GONE
            }

            clearChatButton.setOnClickListener {
                // 확인 대화상자 표시
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("채팅 내역 삭제")
                    .setMessage("모든 채팅 내역을 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ ->
                        // 저장소에서 메시지 삭제
                        chatRepository.clearMessages(this@MainActivity)

                        // 어댑터 초기화
                        chatAdapter.clear()

                        // UI 업데이트
                        emptyChatGroup.visibility = View.VISIBLE
                        chatRecyclerView.visibility = View.GONE
                        clearChatButton.visibility = View.GONE

                        // 삭제 완료 알림
                        Toast.makeText(this@MainActivity, "채팅 내역이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }

            // 음성 결과가 있으면 설정
            voiceResponseText?.let {
                chatInput.setText(it)
                voiceResponseText = null
            }

            // 전송 버튼 이벤트
            sendButton.setOnClickListener {
                val message = chatInput.text.toString().trim()
                if (message.isNotEmpty()) {
                    // 사용자 메시지 생성 및 추가
                    val myMessage = ChatMessage(
                        id = System.currentTimeMillis(),
                        message = message,
                        sender = ChatMessage.Sender.USER,
                        timestamp = System.currentTimeMillis()
                    )

                    chatAdapter.addMessage(myMessage)
                    chatRepository.saveMessage(this@MainActivity, myMessage)

                    // UI 업데이트
                    emptyChatGroup.visibility = View.GONE
                    chatRecyclerView.visibility = View.VISIBLE
                    clearChatButton.visibility = View.VISIBLE
                    chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

                    // 입력 필드 초기화
                    chatInput.text.clear()

                    // API 호출을 위한 로딩 표시 (선택사항)
                    val loadingId = System.currentTimeMillis()
                    val loadingMessage = ChatMessage(
                        id = loadingId,
                        message = "...",
                        sender = ChatMessage.Sender.TODAK,
                        timestamp = System.currentTimeMillis()
                    )
                    chatAdapter.addMessage(loadingMessage)
                    chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)

                    // API 호출을 위한 코루틴 시작
                    lifecycleScope.launch {
                        try {
                            val apiRepository = ApiRepository()
                            val result = apiRepository.sendChatMessage(message)

                            // 로딩 메시지 제거 (선택사항)
                            chatAdapter.removeMessage(loadingMessage.id)

                            when (result) {
                                is NetworkResult.Success -> {
                                    // API 응답에서 메시지 가져오기
                                    val response = result.data.bot_message ?: "응답을 받지 못했습니다."

                                    // 토닥이 응답 메시지 생성 및 추가
                                    val todakMessage = ChatMessage(
                                        id = System.currentTimeMillis(),
                                        message = response,
                                        sender = ChatMessage.Sender.TODAK,
                                        timestamp = System.currentTimeMillis()
                                    )

                                    // UI 업데이트
                                    chatAdapter.addMessage(todakMessage)
                                    chatRepository.saveMessage(this@MainActivity, todakMessage)
                                    chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                                }
                                is NetworkResult.Error -> {
                                    // 오류 메시지 생성 및 추가
                                    val errorMessage = ChatMessage(
                                        id = System.currentTimeMillis(),
                                        message = "죄송합니다. 응답을 받아오는 중 오류가 발생했습니다",
                                        sender = ChatMessage.Sender.TODAK,
                                        timestamp = System.currentTimeMillis()
                                    )

                                    chatAdapter.addMessage(errorMessage)
                                    chatRepository.saveMessage(this@MainActivity, errorMessage)
                                    chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                                }
                                is NetworkResult.Loading -> {
                                }
                            }
                        } catch (e: Exception) {
                            // 로딩 메시지 제거
                            chatAdapter.removeMessage(loadingMessage.id)

                            // 예외 발생 시 오류 메시지 추가
                            val exceptionMessage = ChatMessage(
                                id = System.currentTimeMillis(),
                                message = "죄송합니다. 문제가 발생했습니다: ${e.message}",
                                sender = ChatMessage.Sender.TODAK,
                                timestamp = System.currentTimeMillis()
                            )

                            chatAdapter.addMessage(exceptionMessage)
                            chatRepository.saveMessage(this@MainActivity, exceptionMessage)
                            chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }
                }
            }

            show()
        }
    }

    private val chatRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WakeWordService.ACTION_REFRESH_CHAT) {
                Log.d(TAG, "채팅 새로고침 브로드캐스트 수신됨")
                if (chatDialog != null && chatDialog!!.isShowing) {
                    Log.d(TAG, "채팅 모달 새로고침 시작")
                    refreshChatModalMessages()
                }
            }
        }
    }

    private val refreshScheduleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "일정 새로고침 브로드캐스트 수신: action=${intent.action}")
            if (intent.action == NotificationActionReceiver.ACTION_REFRESH_SCHEDULE) {
                // 기존 ScheduleFragment 교체하여 새로고침 효과
                updateNavSelection(findViewById(R.id.nav_schedule))
                replaceFragment(ScheduleFragment(), "일정")
            }
        }
    }

    private fun refreshChatModalMessages() {
        chatDialog?.let { dialog ->
            val chatRecyclerView = dialog.findViewById<RecyclerView>(R.id.chat_recycler_view)
            val turtleImage = dialog.findViewById<ImageView>(R.id.turtle_image)
            val chatInfoText = dialog.findViewById<TextView>(R.id.chat_info_text)

            // 채팅 어댑터 가져오기
            val chatAdapter = chatRecyclerView.adapter as? ChatAdapter ?: return@let

            // 채팅 저장소 초기화
            val chatRepository = ChatRepository()

            // 어댑터 데이터 초기화
            chatAdapter.clear()

            // 기존 채팅 메시지 다시 불러오기
            val savedMessages = chatRepository.getSavedMessages(this)

            if (savedMessages.isNotEmpty()) {
                // 채팅 기록이 있으면 메시지 표시
                chatAdapter.addMessages(savedMessages)

                // 빈 화면 숨기기
                turtleImage.visibility = View.GONE
                chatInfoText.visibility = View.GONE
                chatRecyclerView.visibility = View.VISIBLE
                chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    // MainActivity 클래스에 추가할 public 메서드들
    fun isVoiceServiceActive(): Boolean {
        return isVoiceServiceActive
    }

    fun startVoiceRecognition() {
        if (!isVoiceServiceActive) {
            checkPermissionsAndStartWakeWordService()
        }
    }

    fun stopVoiceRecognition() {
        if (isVoiceServiceActive) {
            stopWakeWordService()
        }
    }

    private fun registerSmsReceiver() {
        try {
            dynamicSmsReceiver = SMSReceiver()
            val intentFilter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
            intentFilter.priority = Int.MAX_VALUE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    dynamicSmsReceiver,
                    intentFilter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(dynamicSmsReceiver, intentFilter)
            }

            isDynamicReceiverRegistered = true
            Log.d(TAG, "SMS 리시버 동적 등록 성공")
        } catch (e: Exception) {
            Log.e(TAG, "SMS 리시버 동적 등록 실패: ${e.message}")
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                wakeWordReceiver,
                IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(wakeWordReceiver, IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED))
        }
    }

    override fun onPause() {
        super.onPause()
        // BroadcastReceiver 해제
        try {
            unregisterReceiver(wakeWordReceiver)
        } catch (e: IllegalArgumentException) {
            // 리시버가 등록되지 않은 경우 예외 발생 가능
        }
    }

    override fun onDestroy() {
        // 동적 등록한 리시버 해제
        if (isDynamicReceiverRegistered) {
            try {
                unregisterReceiver(dynamicSmsReceiver)
                isDynamicReceiverRegistered = false
                Log.e(TAG, "동적 등록 SMS 리시버 해제 성공")
            } catch (e: Exception) {
                Log.e(TAG, "동적 등록 SMS 리시버 해제 실패: ${e.message}")
            }
        }
        // 앱 종료 시 서비스 바인딩 해제
        if (isBound) {
            wakeWordService?.setVoiceResponseListener(null)
            unbindService(serviceConnection)
            isBound = false
        }

        try {
            unregisterReceiver(chatRefreshReceiver)
        } catch (e: IllegalArgumentException) {
            // 리시버가 등록되지 않은 경우 예외 발생 가능
        }

        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_WAKEWORD_PERMISSIONS = 101
        private const val REQUEST_SMS_PERMISSIONS = 102
    }
}