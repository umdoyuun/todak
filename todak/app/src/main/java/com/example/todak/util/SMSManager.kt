package com.example.todak.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.example.todak.data.model.NetworkResult
import com.example.todak.data.model.SpendingBatchRequest
import com.example.todak.data.repository.SpendingRepository
import com.example.todak.ui.activity.MainActivity
import com.example.todak.ui.fragment.HomeFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SMSManager(private val context: Context) {
    private val TAG = "SMSManager"
    private val dbHelper = SMSDBHelper(context)

    // SMS 데이터 모델
    data class SMSData(
        val id: String,
        val address: String,
        val body: String,
        val date: Long,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    )

    // 싸피은행 SMS 필터링 및 저장
    suspend fun processSMS(id: String, address: String, body: String, date: Long) {
        if (!body.contains("[싸피은행]")) {
            return
        }

        try {
            // 앱이 포어그라운드인지 확인
            val isAppInForeground = AppStateMonitor.isAppInForeground()

            if (isAppInForeground) {
                // 포어그라운드: 즉시 파싱 및 API 전송
                processInForeground(id, address, body, date)
            } else {
                // 백그라운드: 메시지와 위치 정보만 저장
                saveInBackground(id, address, body, date)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS 처리 오류: ${e.message}")
        }
    }

    // Date를 ISO 8601 형식의 문자열로 변환
    private fun formatDateToISO8601(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    // 포어그라운드에서 SMS 처리
    private suspend fun processInForeground(id: String, address: String, body: String, date: Long) {
        // 위치 정보 가져오기
        val locationHelper = LocationHelper(context)
        val location = locationHelper.getCurrentLocation()

        // SMS 파싱
        val smsParser = SMSParser()
        val spendingItem = smsParser.parse(body)?.copy(
            lat = location?.latitude ?: 0.0,
            lon = location?.longitude ?: 0.0
        )

        Log.d(TAG, "SMS 파싱 완료: $spendingItem")
        // API 전송
        spendingItem?.let {
            val spendingRepository = SpendingRepository()
            val result = spendingRepository.submitSpending(it)

            // 성공 시 처리된 것으로 표시하고 홈 프래그먼트 새로고침
            if (result is NetworkResult.Success) {
                markSmsAsProcessed(id)

                Log.d(TAG, "SMS 처리 완료: $spendingItem")
                // 예산 데이터 갱신 이벤트 발생
                BudgetUpdateEvent.triggerUpdate()
            }
        }
    }

    // 백그라운드에서 SMS 저장
    private suspend fun saveInBackground(id: String, address: String, body: String, date: Long) {
        // 현재 위치 정보 가져오기
        val locationHelper = LocationHelper(context)
        val location = locationHelper.getLastKnownLocation()

        // SMS 및 위치 정보 로컬 DB에 저장
        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(SMS_COLUMN_ID, id)
            put(SMS_COLUMN_ADDRESS, address)
            put(SMS_COLUMN_BODY, body)
            put(SMS_COLUMN_DATE, date)
            put(SMS_COLUMN_LATITUDE, location?.latitude ?: 0.0)
            put(SMS_COLUMN_LONGITUDE, location?.longitude ?: 0.0)
            put(SMS_COLUMN_PROCESSED, 0) // 처리되지 않음
        }

        db.insert(SMS_TABLE_NAME, null, values)
        db.close()

        Log.d(TAG, "SMS를 백그라운드에서 저장: $id")
    }

    // 저장된 SMS 불러오기
    suspend fun loadSavedSMS(): List<SMSData> = withContext(Dispatchers.IO) {
        val smsList = mutableListOf<SMSData>()
        val db = dbHelper.readableDatabase

        try {
            val cursor = db.query(
                SMS_TABLE_NAME,
                null,
                "$SMS_COLUMN_PROCESSED = ?",
                arrayOf("0"),
                null,
                null,
                "$SMS_COLUMN_DATE DESC"
            )

            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(SMS_COLUMN_ID))
                val address = cursor.getString(cursor.getColumnIndexOrThrow(SMS_COLUMN_ADDRESS))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(SMS_COLUMN_BODY))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(SMS_COLUMN_DATE))
                val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SMS_COLUMN_LATITUDE))
                val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SMS_COLUMN_LONGITUDE))

                smsList.add(SMSData(id, address, body, date, latitude, longitude))
            }

            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "저장된 SMS 불러오기 오류: ${e.message}")
        } finally {
            db.close()
        }

        smsList
    }

    // 처리된 SMS로 표시
    fun markSmsAsProcessed(id: String) {
        val db = dbHelper.writableDatabase

        try {
            val values = ContentValues().apply {
                put(SMS_COLUMN_PROCESSED, 1) // 처리됨
            }

            db.update(
                SMS_TABLE_NAME,
                values,
                "$SMS_COLUMN_ID = ?",
                arrayOf(id)
            )
        } catch (e: Exception) {
            Log.e(TAG, "SMS 처리 상태 업데이트 오류: ${e.message}")
        } finally {
            db.close()
        }
    }

    // 여러 SMS를 처리된 것으로 표시
    fun markMultipleSmsAsProcessed(ids: List<String>) {
        val db = dbHelper.writableDatabase

        try {
            db.beginTransaction()

            for (id in ids) {
                val values = ContentValues().apply {
                    put(SMS_COLUMN_PROCESSED, 1) // 처리됨
                }

                db.update(
                    SMS_TABLE_NAME,
                    values,
                    "$SMS_COLUMN_ID = ?",
                    arrayOf(id)
                )
            }

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "다중 SMS 처리 상태 업데이트 오류: ${e.message}")
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    // SMS가 이미 처리되었는지 확인
    private fun isProcessed(id: String): Boolean {
        val db = dbHelper.readableDatabase
        var processed = false

        try {
            val cursor = db.query(
                SMS_TABLE_NAME,
                arrayOf(SMS_COLUMN_PROCESSED),
                "$SMS_COLUMN_ID = ?",
                arrayOf(id),
                null,
                null,
                null
            )

            if (cursor.moveToFirst()) {
                processed = cursor.getInt(0) == 1
            }

            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "SMS 처리 상태 확인 오류: ${e.message}")
        } finally {
            db.close()
        }

        return processed
    }

    // 앱 실행 시 저장된 SMS 처리
    suspend fun processAllSavedSMS() {
        // 저장된 SMS 불러오기
        val savedSMS = loadSavedSMS()

        if (savedSMS.isEmpty()) {
            Log.d(TAG, "저장된 SMS가 없습니다.")
            return
        }

        // SMS 파싱 및 배치 처리
        val smsParser = SMSParser()
        val spendingItems = savedSMS.mapNotNull { sms ->
            val dateObj = Date(sms.date)
            val timestampStr = formatDateToISO8601(dateObj)

            smsParser.parse(sms.body)?.copy(
                lat = sms.latitude,
                lon = sms.longitude,
                timestamp = timestampStr
            )
        }

        if (spendingItems.isNotEmpty()) {
            // 배치 API 호출
            val spendingRepository = SpendingRepository()
            val request = SpendingBatchRequest(items = spendingItems)
            val result = spendingRepository.submitBatch(request)

            // 성공 시 처리된 것으로 표시하고 홈 프래그먼트 새로고침
            if (result is NetworkResult.Success) {
                val ids = savedSMS.map { it.id }
                markMultipleSmsAsProcessed(ids)
                Log.d(TAG, "저장된 ${spendingItems.size}개의 SMS 처리 완료")

                // 예산 데이터 갱신 이벤트 발생
                BudgetUpdateEvent.triggerUpdate()
            }
        }
    }

    // SQLite 헬퍼 클래스
    private class SMSDBHelper(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $SMS_TABLE_NAME (
                    $SMS_COLUMN_ID TEXT PRIMARY KEY,
                    $SMS_COLUMN_ADDRESS TEXT,
                    $SMS_COLUMN_BODY TEXT,
                    $SMS_COLUMN_DATE INTEGER,
                    $SMS_COLUMN_LATITUDE REAL,
                    $SMS_COLUMN_LONGITUDE REAL,
                    $SMS_COLUMN_PROCESSED INTEGER
                )
                """
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $SMS_TABLE_NAME")
            onCreate(db)
        }
    }

    companion object {
        private const val DATABASE_NAME = "sms_database"
        private const val DATABASE_VERSION = 1
        private const val SMS_TABLE_NAME = "sms_messages"
        private const val SMS_COLUMN_ID = "id"
        private const val SMS_COLUMN_ADDRESS = "address"
        private const val SMS_COLUMN_BODY = "body"
        private const val SMS_COLUMN_DATE = "date"
        private const val SMS_COLUMN_LATITUDE = "latitude"
        private const val SMS_COLUMN_LONGITUDE = "longitude"
        private const val SMS_COLUMN_PROCESSED = "processed"
    }
}