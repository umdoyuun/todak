package com.example.todak.util

import android.content.Context
import android.util.Log
import com.example.todak.data.repository.FcmApiRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FCMManager {
    private const val TAG = "FCMManager"
    private val fcmRepository = FcmApiRepository()

    fun checkAndRegisterToken(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // FCM 토큰 가져오기
                val token = getFirebaseToken()

                // 서버에 토큰 등록
                val result = fcmRepository.registerDeviceToken(token)

                withContext(Dispatchers.Main) {
                    if (result is com.example.todak.data.model.NetworkResult.Success) {
                        Log.d(TAG, "FCM 토큰 서버 등록 성공: $token")
                    } else if (result is com.example.todak.data.model.NetworkResult.Error) {
                        Log.e(TAG, "FCM 토큰 서버 등록 실패: ${result.message}")
                    } else {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM 토큰 처리 오류", e)
            }
        }
    }

    fun unregisterToken() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // FCM 토큰 가져오기
                val token = getFirebaseToken()

                // 서버에서 토큰 등록 해제
                val result = fcmRepository.unregisterDeviceToken(token)

                if (result is com.example.todak.data.model.NetworkResult.Success) {
                    Log.d(TAG, "FCM 토큰 서버 등록 해제 성공")
                } else if (result is com.example.todak.data.model.NetworkResult.Error) {
                    Log.e(TAG, "FCM 토큰 서버 등록 해제 실패: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM 토큰 등록 해제 오류", e)
            }
        }
    }

    private suspend fun getFirebaseToken(): String {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.e(TAG, "FCM 토큰 가져오기 오류", e)
            throw e
        }
    }
}