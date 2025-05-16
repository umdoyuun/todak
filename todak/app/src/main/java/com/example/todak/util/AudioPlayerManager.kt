package com.example.todak.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.todak.util.Constants.S3_URL

class AudioPlayerManager {
    private val TAG = "AudioPlayerManager"
    private var mediaPlayer: MediaPlayer? = null

    fun playTtsFromS3(s3Key: String, onCompletion: () -> Unit = {}, onError: (String) -> Unit = {}) {
        try {
            releaseMediaPlayer()

            val fileUrl = "$S3_URL$s3Key"
            Log.d(TAG, "TTS 스트리밍 URL: $fileUrl")

            // MediaPlayer 설정 및 재생
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setDataSource(fileUrl)

                setOnPreparedListener {
                    it.start()
                    Log.d(TAG, "TTS 오디오 재생 시작")
                }

                setOnCompletionListener {
                    releaseMediaPlayer()
                    onCompletion()
                    Log.d(TAG, "TTS 오디오 재생 완료")
                }

                setOnErrorListener { _, what, extra ->
                    val errorMsg = "MediaPlayer 오류: what=$what, extra=$extra"
                    Log.e(TAG, errorMsg)
                    releaseMediaPlayer()
                    onError(errorMsg)
                    true
                }

                // 비동기 준비 (네트워크 작업은 메인 스레드에서 하지 않기 위해)
                prepareAsync()
            }
        } catch (e: Exception) {
            val errorMsg = "오디오 스트리밍 준비 실패: ${e.message}"
            Log.e(TAG, errorMsg, e)
            onError(errorMsg)
        }
    }

    fun releaseMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer 해제 실패", e)
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
}