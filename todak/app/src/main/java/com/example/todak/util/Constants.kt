package com.example.todak.util

import com.example.todak.BuildConfig

object Constants {
    const val BASE_URL = BuildConfig.SERVER_ADDRESS
    const val S3_URL = BuildConfig.S3_ADDRESS
    const val API_TIMEOUT_SECONDS = 60L

    // 앱 설정 관련 상수
    const val DEBUG = true  // 배포 시 false로 변경

    // 오디오 관련 상수
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_CHANNEL_CONFIG = 16
    const val AUDIO_ENCODING_FORMAT = 2
    const val RECORD_AUDIO_PERMISSION_CODE = 100

    // 로그 태그
    const val LOG_TAG = "TodakApp"
}