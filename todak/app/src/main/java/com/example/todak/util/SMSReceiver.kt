package com.example.todak.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.todak.util.SMSManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SMSReceiver : BroadcastReceiver() {
    private val TAG = "SMSReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "SMSReceiver.onReceive 호출됨: action=${intent.action}")
            Log.d(TAG, "SMS 수신됨")

            // SMS 메시지 추출
            val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            Log.d(TAG, "SMS 메시지 개수: ${smsMessages.size}")

            // 여러 파트의 메시지를 하나로 합치기
            val smsMap = mutableMapOf<String, MutableList<Pair<String, String>>>()

            for (sms in smsMessages) {
                val from = sms.originatingAddress ?: "unknown"
                val body = sms.messageBody ?: ""
                val timestamp = sms.timestampMillis

                // 발신자 번호를 키로 사용하여 같은 발신자의 메시지를 그룹핑
                if (!smsMap.containsKey(from)) {
                    smsMap[from] = mutableListOf()
                }

                // 메시지 본문과 타임스탬프를 저장
                smsMap[from]?.add(Pair(body, timestamp.toString()))
            }

            // SMS 매니저 초기화
            val smsManager = SMSManager(context)

            // 각 발신자별로 메시지 처리
            CoroutineScope(Dispatchers.IO).launch {
                for ((from, messages) in smsMap) {
                    // 타임스탬프 기준으로 정렬 (오래된 순)
                    messages.sortBy { it.second.toLong() }

                    // 메시지 내용 합치기 - 줄바꿈 없이 공백으로 합치기
                    val combinedBody = messages.joinToString("") { it.first }.trim()
                    val latestTimestamp = messages.last().second.toLong()

                    // 합쳐진 메시지 로깅
                    Log.d(TAG, "SMS 처리 중: from=$from, body='$combinedBody'")

                    // 싸피은행 메시지 확인
                    if (combinedBody.contains("[싸피은행]")) {
                        Log.d(TAG, "싸피은행 메시지 발견, 처리 시작")

                        // 고유 ID 생성 (발신자 + 타임스탬프)
                        val messageId = "$from-$latestTimestamp"

                        // SMS 처리
                        smsManager.processSMS(messageId, from, combinedBody, latestTimestamp)
                    } else {
                        Log.d(TAG, "싸피은행 메시지가 아님, 무시함")
                    }
                }
            }
        }
    }
}