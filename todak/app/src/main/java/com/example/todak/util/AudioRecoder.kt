package com.example.todak.util

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.Manifest.permission.RECORD_AUDIO

class WavAudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var bufferSize = 0
    private var outputFilePath: String? = null

    fun startRecording(outputPath: String): Boolean {
        if (isRecording) return false

        try {
            if (ContextCompat.checkSelfPermission(context, RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "오디오 녹음 권한이 없습니다")
                return false
            }

            // 출력 파일 경로 저장
            outputFilePath = outputPath

            // 버퍼 크기 계산
            bufferSize = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize <= 0) {
                Log.e(TAG, "올바른 버퍼 크기를 얻을 수 없습니다")
                return false
            }

            // AudioRecord 초기화
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord를 초기화할 수 없습니다")
                return false
            }

            isRecording = true

            // 별도 스레드에서 녹음 실행
            recordingThread = Thread {
                writeAudioDataToFile(outputPath)
            }
            recordingThread?.start()

            Log.d(TAG, "녹음 시작: $outputPath")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "녹음 시작 실패", e)
            stopRecording()
            return false
        }
    }

    private fun writeAudioDataToFile(filePath: String) {
        val file = File(filePath)

        // 디렉토리 생성
        file.parentFile?.mkdirs()

        // 기존 파일 삭제
        if (file.exists()) {
            file.delete()
        }

        try {
            // 임시 raw 파일 생성
            val tempRawFile = File("${filePath}.raw")
            val rawOutputStream = FileOutputStream(tempRawFile)

            val data = ByteArray(bufferSize)

            audioRecord?.startRecording()

            // PCM 데이터 녹음
            while (isRecording) {
                val read = audioRecord?.read(data, 0, bufferSize) ?: -1
                if (read > 0) {
                    rawOutputStream.write(data, 0, read)
                }
            }

            rawOutputStream.close()

            // raw 파일을 WAV 파일로 변환
            rawToWave(tempRawFile, file)

            // 임시 파일 삭제
            tempRawFile.delete()

        } catch (e: IOException) {
            Log.e(TAG, "파일 쓰기 실패", e)
        }
    }

    // RAW PCM 데이터를 WAV 파일로 변환
    private fun rawToWave(rawFile: File, waveFile: File) {
        try {
            val rawData = rawFile.readBytes()
            val waveOutputStream = FileOutputStream(waveFile)

            // WAV 헤더 생성 및 쓰기
            val headerSize = 44
            val totalDataLen = rawData.size + headerSize - 8
            val audioDataLen = rawData.size
            val channels = 1 // 모노
            val byteRate = 16000 * 2 * channels // sampleRate * 2(16bit) * channels

            val header = ByteArray(headerSize)

            header[0] = 'R'.toByte() // RIFF/WAVE 헤더
            header[1] = 'I'.toByte()
            header[2] = 'F'.toByte()
            header[3] = 'F'.toByte()

            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = (totalDataLen shr 8 and 0xff).toByte()
            header[6] = (totalDataLen shr 16 and 0xff).toByte()
            header[7] = (totalDataLen shr 24 and 0xff).toByte()

            header[8] = 'W'.toByte() // WAVE
            header[9] = 'A'.toByte()
            header[10] = 'V'.toByte()
            header[11] = 'E'.toByte()

            header[12] = 'f'.toByte() // 'fmt ' 청크
            header[13] = 'm'.toByte()
            header[14] = 't'.toByte()
            header[15] = ' '.toByte()

            header[16] = 16 // 4 바이트: 크기
            header[17] = 0
            header[18] = 0
            header[19] = 0

            header[20] = 1 // 포맷 = 1 (PCM)
            header[21] = 0

            header[22] = channels.toByte()
            header[23] = 0

            header[24] = (16000 and 0xff).toByte() // 샘플레이트
            header[25] = (16000 shr 8 and 0xff).toByte()
            header[26] = (16000 shr 16 and 0xff).toByte()
            header[27] = (16000 shr 24 and 0xff).toByte()

            header[28] = (byteRate and 0xff).toByte() // 바이트레이트
            header[29] = (byteRate shr 8 and 0xff).toByte()
            header[30] = (byteRate shr 16 and 0xff).toByte()
            header[31] = (byteRate shr 24 and 0xff).toByte()

            header[32] = (2 * channels).toByte() // 블록 정렬
            header[33] = 0

            header[34] = 16 // 비트 퍼 샘플
            header[35] = 0

            header[36] = 'd'.toByte() // 'data' 청크
            header[37] = 'a'.toByte()
            header[38] = 't'.toByte()
            header[39] = 'a'.toByte()

            header[40] = (audioDataLen and 0xff).toByte() // 데이터 크기
            header[41] = (audioDataLen shr 8 and 0xff).toByte()
            header[42] = (audioDataLen shr 16 and 0xff).toByte()
            header[43] = (audioDataLen shr 24 and 0xff).toByte()

            waveOutputStream.write(header)
            waveOutputStream.write(rawData)
            waveOutputStream.close()

        } catch (e: IOException) {
            Log.e(TAG, "WAV 파일 변환 실패", e)
        }
    }

    /**
     * 현재 오디오 진폭 가져오기 (침묵 감지에 사용)
     */
    fun getMaxAmplitude(): Int {
        return if (audioRecord != null && isRecording) {
            try {
                // AudioRecord에서는 최대 진폭을 쉽게 얻을 수 없음
                // 실시간으로 계산하려면 추가 작업 필요
                0
            } catch (e: Exception) {
                Log.e(TAG, "maxAmplitude 가져오기 실패", e)
                0
            }
        } else 0
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            // 녹음 스레드가 종료될 때까지 대기
            recordingThread?.join()
            recordingThread = null
        } catch (e: Exception) {
            Log.e(TAG, "녹음 중지 실패", e)
        }

        val outputFile = outputFilePath?.let { File(it) }
        return if (outputFile?.exists() == true && outputFile.length() > 0) {
            Log.d(TAG, "녹음 중지: ${outputFile.absolutePath} (크기: ${outputFile.length()} 바이트)")
            outputFile
        } else {
            Log.e(TAG, "녹음 파일이 생성되지 않았거나 비어 있습니다")
            null
        }
    }

    /**
     * 모든 리소스 해제
     */
    fun release() {
        if (isRecording) {
            stopRecording()
        } else {
            try {
                audioRecord?.release()
                audioRecord = null
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord 해제 실패", e)
            }
        }
    }

    companion object {
        private const val TAG = "WavAudioRecorder"
    }
}