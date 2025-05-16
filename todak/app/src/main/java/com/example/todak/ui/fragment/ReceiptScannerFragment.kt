package com.example.todak.ui.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.todak.R
import com.example.todak.data.repository.ApiRepository
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.OcrResponse
import com.example.todak.ui.activity.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ReceiptScannerFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var guideText: TextView
    private lateinit var turtleImage: ImageView
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private val apiRepository = ApiRepository()

    // 현재 스캔 중인지 상태 관리
    private var isScanning = false

    // 현재 이미지 처리 중인지 상태 관리
    private var isProcessing = false

    // 스캔 작업을 관리하는 Job
    private var scanJob: Job? = null

    // 스캔 간격 (밀리초)
    private val scanInterval = 3000L

    // 현재 매장 ID
    private var storeId = "a872568c-2422-4bfc-bb55-3c35016f3962" // 기본값 설정

    // 카메라 설정 완료 여부
    private var isCameraSetup = false

    companion object {
        private const val TAG = "ReceiptScannerFragment"
        private const val REQUEST_CAMERA_PERMISSION = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_receipt_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 타이틀 설정 (필요한 경우)
        (activity as? MainActivity)?.setToolbarTitle("주문서 스캔")
        // 뷰 초기화
        previewView = view.findViewById(R.id.viewFinder)
        guideText = view.findViewById(R.id.tvGuide)
        turtleImage = view.findViewById(R.id.ivTurtle)

        // Log 추가
        Log.d(TAG, "onViewCreated 호출됨")

        // Arguments에서 storeId 가져오기
        arguments?.getString("store_id")?.let {
            storeId = it
            Log.d(TAG, "매장 ID: $storeId")
        }

        // 카메라 실행기 초기화
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 카메라 권한 확인 및 카메라 시작
        if (allPermissionsGranted()) {
            Log.d(TAG, "권한 확인 완료, 카메라 초기화 시작")
            setupCamera()
        } else {
            Log.d(TAG, "권한 요청 필요")
            requestPermissions()
        }
    }

    private fun setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        Log.d(TAG, "카메라 프로바이더 초기화")

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                Log.d(TAG, "카메라 프로바이더 가져오기 성공")

                // 이미지 캡처 설정
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // 프리뷰 설정
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // 후면 카메라 선택
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // 기존 카메라 바인딩 해제 후 새로 바인딩
                cameraProvider.unbindAll()

                try {
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    Log.d(TAG, "카메라 바인딩 성공")

                    // 스캔 시작
                    isCameraSetup = true
                    // UI 업데이트를 위해 메인 스레드에서 실행
                    lifecycleScope.launch(Dispatchers.Main) {
                        // 약간의 지연 후 스캔 시작 (카메라 초기화 완료 대기)
                        delay(500)
                        if (isAdded && isCameraSetup) {
                            startScanning()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "카메라 바인딩 중 오류 발생", e)
                    Toast.makeText(requireContext(), "카메라를 시작할 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "카메라 프로바이더 가져오기 실패", e)
                Toast.makeText(requireContext(), "카메라를 초기화할 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startScanning() {
        if (isScanning || !isCameraSetup) {
            Log.d(TAG, "스캔 시작 불가: isScanning=$isScanning, isCameraSetup=$isCameraSetup")
            return
        }

        isScanning = true
        guideText.text = "영수증을 프레임 안에 위치시켜주세요"

        // 기존 Job이 있으면 취소
        scanJob?.cancel()

        // 새로운 Job 시작
        scanJob = lifecycleScope.launch {
            Log.d(TAG, "스캔 작업 시작")

            // 첫 번째 캡처는 약간 지연 후 시작
            delay(1000)

            var scanCount = 0
            while (isScanning && isActive && isCameraSetup) {
                if (!isProcessing) {
                    scanCount++
                    Log.d(TAG, "스캔 시도 #$scanCount")
                    captureAndAnalyze()
                } else {
                    Log.d(TAG, "이미 처리 중이므로 스캔 건너뜀")
                }
                delay(scanInterval)
            }
        }

        Log.d(TAG, "자동 스캔 시작됨")
    }

    private fun stopScanning() {
        if (!isScanning) return

        isScanning = false
        scanJob?.cancel()
        scanJob = null
        Log.d(TAG, "스캔 중지됨")
    }

    private fun captureAndAnalyze() {
        if (!::imageCapture.isInitialized) {
            Log.e(TAG, "imageCapture가 초기화되지 않음")
            return
        }

        if (!isScanning || isProcessing) {
            Log.d(TAG, "캡처 무시: isScanning=$isScanning, isProcessing=$isProcessing")
            return
        }

        isProcessing = true
        Log.d(TAG, "이미지 캡처 시작")

        try {
            // UI 업데이트
            lifecycleScope.launch(Dispatchers.Main) {
                guideText.text = "스캔 중..."
            }

            // 임시 파일 생성
            val photoFile = File(
                requireContext().cacheDir,
                "JPEG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())}.jpg"
            )

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            // 이미지 캡처
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "이미지 캡처 성공: ${photoFile.absolutePath}, 크기: ${photoFile.length()} bytes")

                        // OCR 처리 시작
                        lifecycleScope.launch {
                            processOcrRequest(photoFile)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "이미지 캡처 오류: ${exception.message}", exception)
                        isProcessing = false

                        lifecycleScope.launch(Dispatchers.Main) {
                            if (isAdded) {
                                guideText.text = "이미지 캡처 오류. 다시 시도해주세요."
                                // 잠시 후 다시 스캔 가능하도록 설정
                                delay(1000)
                                if (isScanning && isAdded) {
                                    guideText.text = "영수증을 프레임 안에 위치시켜주세요"
                                }
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "이미지 캡처 준비 오류", e)
            isProcessing = false

            lifecycleScope.launch(Dispatchers.Main) {
                if (isAdded) {
                    guideText.text = "오류가 발생했습니다. 다시 시도해주세요."
                }
            }
        }
    }

    private suspend fun processOcrRequest(imageFile: File) {
        try {
            Log.d(TAG, "OCR 요청 시작: 파일 = ${imageFile.name}")

            when (val result = apiRepository.performOcr(imageFile, storeId)) {
                is NetworkResult.Success -> {
                    val ocrResponse = result.data
                    Log.d(TAG, "OCR 응답 성공: 메뉴 ${ocrResponse.menus.size}개 인식됨")

                    // 메뉴 리스트가 비어있지 않으면 결과 화면으로 이동
                    if (ocrResponse.menus.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                stopScanning()
                                // 결과 화면으로 이동
                                navigateToResultScreen(ocrResponse)
                            }
                        }
                    } else {
                        Log.d(TAG, "인식된 메뉴가 없습니다. 계속 스캔합니다.")
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                guideText.text = "영수증을 인식할 수 없습니다. 다시 시도해주세요."
                                // 잠시 후 다시 스캔 가능하도록 설정
                                delay(1500)
                                if (isScanning && isAdded) {
                                    guideText.text = "영수증을 프레임 안에 위치시켜주세요"
                                    isProcessing = false
                                }
                            }
                        }
                    }
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "OCR 요청 오류: ${result.message}")
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            guideText.text = "인식 오류가 발생했습니다. 다시 시도해주세요."
                            // 잠시 후 다시 스캔 가능하도록 설정
                            delay(1500)
                            if (isScanning && isAdded) {
                                guideText.text = "영수증을 프레임 안에 위치시켜주세요"
                                isProcessing = false
                            }
                        }
                    }
                }
                is NetworkResult.Loading -> {
                    // Loading 상태 처리
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            guideText.text = "영수증 분석 중..."
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR 처리 오류", e)
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    guideText.text = "처리 오류가 발생했습니다. 다시 시도해주세요."
                    // 잠시 후 다시 스캔 가능하도록 설정
                    delay(1500)
                    if (isScanning && isAdded) {
                        guideText.text = "영수증을 프레임 안에 위치시켜주세요"
                        isProcessing = false
                    }
                }
            }
        } finally {
            // 임시 파일 삭제
            try {
                if (imageFile.exists()) {
                    imageFile.delete()
                    Log.d(TAG, "임시 파일 삭제 완료: ${imageFile.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "임시 파일 삭제 오류", e)
            }

            // 오류 발생 시에도 처리 상태 초기화하도록 보장
            if (isScanning && isProcessing) {
                isProcessing = false
            }
        }
    }

    private fun navigateToResultScreen(ocrResponse: OcrResponse) {
        try {
            Log.d(TAG, "결과 화면으로 이동 시작")

            val shopName = arguments?.getString("shop_name") ?: ""

            val receiptResultFragment = ReceiptResultFragment.newInstance(
                ocrResponse = ocrResponse,
                storeId = storeId,
                shopName = shopName
            )

            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_container, receiptResultFragment)
                .addToBackStack(null)
                .commit()

            Log.d(TAG, "결과 화면으로 이동 성공")
        } catch (e: Exception) {
            Log.e(TAG, "결과 화면 이동 오류", e)
            Toast.makeText(context, "결과 화면으로 이동할 수 없습니다", Toast.LENGTH_SHORT).show()

            // 오류 발생 시 다시 스캔할 수 있도록 상태 초기화
            isProcessing = false
            startScanning()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CAMERA_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted()) {
                Log.d(TAG, "권한 획득 완료, 카메라 설정 시작")
                setupCamera()
            } else {
                Log.d(TAG, "권한 거부됨, 이전 화면으로 돌아가기")
                Toast.makeText(
                    context,
                    "카메라 권한이 필요합니다",
                    Toast.LENGTH_SHORT
                ).show()
                // 권한이 없으면 이전 화면으로 돌아가기
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause 호출됨")
        // 화면이 백그라운드로 갈 때 스캔 중지
        stopScanning()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume 호출됨")
        // 화면이 다시 보일 때 스캔 재시작 (카메라가 초기화된 경우에만)
        if (::imageCapture.isInitialized && isCameraSetup && !isScanning) {
            // 약간의 지연 후 스캔 시작
            lifecycleScope.launch {
                delay(500)
                if (isAdded && isCameraSetup) {
                    startScanning()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy 호출됨")
        // 스캔 중지 및 리소스 정리
        stopScanning()

        try {
            // 카메라 바인딩 해제
            if (::cameraProviderFuture.isInitialized) {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            }
        } catch (e: Exception) {
            Log.e(TAG, "카메라 바인딩 해제 오류", e)
        }

        // 카메라 실행기 종료
        cameraExecutor.shutdown()
    }
}