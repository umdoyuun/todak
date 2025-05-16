package com.example.todak.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    private val TAG = "LocationHelper"
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // 마지막으로 알려진 위치를 캐시
    private var lastKnownLocation: Location? = null

    init {
        // 저장된 위치 정보 로드
        loadLastKnownLocation()
    }

    // 현재 위치를 코루틴으로 가져오는 함수
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            // 이미 캐시된 위치가 있다면 반환
            lastKnownLocation?.let {
                // 캐시된 위치가 10분 이내면 재사용
                if (System.currentTimeMillis() - it.time < 10 * 60 * 1000) {
                    continuation.resume(it)
                    return@suspendCancellableCoroutine
                }
            }

            // 위치 요청 설정
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdateDelayMillis(15000)
                .build()

            // 위치 콜백
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        // 위치 캐시 업데이트
                        updateLastKnownLocation(location)
                        // 코루틴 재개
                        continuation.resume(location)
                        // 위치 업데이트 중지
                        fusedLocationClient.removeLocationUpdates(this)
                    } ?: run {
                        // 위치를 가져오지 못한 경우 캐시된 위치 반환
                        continuation.resume(lastKnownLocation)
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }

            // 위치 업데이트 요청
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // 코루틴 취소 시 위치 업데이트 중지
            continuation.invokeOnCancellation {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }

            // 최근 위치 확인 (빠른 응답을 위해)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    updateLastKnownLocation(it)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "위치 정보 가져오기 오류: ${e.message}")
            continuation.resume(lastKnownLocation)
        }
    }

    // 백그라운드에서 호출: 최근 캐시된 위치만 반환 (네트워크 요청 없음)
    fun getLastKnownLocation(): Location? {
        return lastKnownLocation
    }

    // 위치 정보 캐시 업데이트
    private fun updateLastKnownLocation(location: Location) {
        lastKnownLocation = location

        // 위치 정보 저장
        saveLastKnownLocation(location)

        Log.d(TAG, "위치 정보 업데이트: ${location.latitude}, ${location.longitude}")
    }

    // 위치 정보를 SharedPreferences에 저장
    private fun saveLastKnownLocation(location: Location) {
        val prefs = context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
        val locationJson = JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy)
            put("time", location.time)
        }.toString()

        prefs.edit {
            putString(KEY_LAST_LOCATION, locationJson)
            apply()
        }
    }

    // 저장된 위치 정보 로드
    private fun loadLastKnownLocation() {
        val prefs = context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
        val locationJson = prefs.getString(KEY_LAST_LOCATION, null) ?: return

        try {
            val json = JSONObject(locationJson)
            val location = Location("cached").apply {
                latitude = json.getDouble("latitude")
                longitude = json.getDouble("longitude")
                accuracy = json.getDouble("accuracy").toFloat()
                time = json.getLong("time")
            }

            lastKnownLocation = location
            Log.d(TAG, "저장된 위치 정보 로드: ${location.latitude}, ${location.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "저장된 위치 정보 로드 오류: ${e.message}")
        }
    }

    companion object {
        private const val LOCATION_PREFS = "location_prefs"
        private const val KEY_LAST_LOCATION = "last_location"
    }
}