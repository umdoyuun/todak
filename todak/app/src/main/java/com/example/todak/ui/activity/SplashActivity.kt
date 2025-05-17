package com.example.todak.ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.todak.R

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ 스플래시 화면 처리
        val splashScreen = installSplashScreen()

        // 시스템 스플래시 화면을 즉시 제거하고 커스텀 스플래시 화면으로 전환
        splashScreen.setKeepOnScreenCondition { false }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 500)
    }
}