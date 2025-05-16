package com.example.todak.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

object AppStateMonitor : DefaultLifecycleObserver {
    private const val TAG = "AppStateMonitor"
    private var isAppInForeground = false
    private var smsProcessorCallback: (() -> Unit)? = null

    fun init(context: Context) {
        val applicationContext = context.applicationContext
        if (applicationContext is Application) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } else {
            Log.e(TAG, "Invalid context type. Application context required.")
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        Log.d(TAG, "앱이 포어그라운드로 전환됨")

        // 포어그라운드로 전환될 때 저장된 SMS 처리
        processSavedSms()
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
        Log.d(TAG, "앱이 백그라운드로 전환됨")
    }

    fun isAppInForeground(): Boolean {
        return isAppInForeground
    }

    // SMS 처리 콜백 설정
    fun setSmsProcessorCallback(callback: () -> Unit) {
        this.smsProcessorCallback = callback
        Log.d(TAG, "SMS 프로세서 콜백 설정됨")
    }

    // 저장된 SMS 처리
    private fun processSavedSms() {
        if (smsProcessorCallback != null) {
            Log.d(TAG, "포어그라운드 전환 시 저장된 SMS 처리 시작")
            smsProcessorCallback?.invoke()
        } else {
            Log.d(TAG, "SMS 프로세서 콜백이 설정되지 않았습니다")
        }
    }
}