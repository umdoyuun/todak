package com.example.todak.util

import android.util.Log
import com.example.todak.data.model.SpendingItem
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class SMSParser {
    private val TAG = "SMSParser"

    // 정규식 패턴
    private val amountPattern = Pattern.compile("금액:\\s*(\\d[\\d,]+)원")
    private val datePattern = Pattern.compile("일시:\\s*(\\d{2}/\\d{2})\\s*(\\d{2}:\\d{2})")
    private val storePattern = Pattern.compile("가맹점:\\s*(.+)")
    private val methodPattern = Pattern.compile("결제방식:\\s*(.+)")
    private val cardTypePattern = Pattern.compile("\\[싸피은행\\][\\s\\S]*?(신용카드|체크카드|교통카드)\\s*결제")

    // 한국의 주요 지역 목록
    private val koreanRegions = listOf(
        "강남", "서초", "송파", "종로", "중구", "용산", "성동", "광진", "동대문", "중랑",
        "성북", "강북", "도봉", "노원", "은평", "서대문", "마포", "양천", "강서", "구로",
        "금천", "영등포", "동작", "관악", "서울", "강동", "인천", "부천", "수원", "성남",
        "안양", "안산", "용인", "부산", "대구", "광주", "대전", "울산", "세종", "고양",
        "일산", "과천", "구리", "남양주", "시흥", "군포", "의왕", "하남", "파주", "이천",
        "김포", "광명", "평택", "동탄", "오산", "화성", "경기", "춘천", "원주", "강릉",
        "동해", "속초", "삼척", "태백", "청주", "충주", "천안", "아산", "공주", "보령",
        "서산", "논산", "계룡", "당진", "홍성", "예산", "전주", "군산", "익산", "정읍",
        "김제", "남원", "완주", "목포", "여수", "순천", "나주", "광양", "담양", "곡성",
        "구례", "고흥", "보성", "화순", "장흥", "강진", "해남", "영암", "무안", "함평",
        "영광", "장성", "완도", "진도", "신안", "포항", "경주", "김천", "안동", "구미",
        "영주", "영천", "상주", "문경", "경산", "창원", "진주", "통영", "사천", "김해",
        "밀양", "거제", "양산", "제주", "서귀포"
    )

    fun parse(smsBody: String): SpendingItem? {
        try {
            // 싸피은행 메시지가 아니면 무시
            if (!smsBody.contains("[싸피은행]")) {
                return null
            }

            // 금액 추출
            val amountMatcher = amountPattern.matcher(smsBody)
            if (!amountMatcher.find()) return null
            val amountStr = amountMatcher.group(1)?.replace(",", "") ?: return null
            val amount = amountStr.toIntOrNull() ?: return null

            // 일시 추출
            val dateMatcher = datePattern.matcher(smsBody)
            if (!dateMatcher.find()) return null
            val dateStr = dateMatcher.group(1) ?: return null
            val timeStr = dateMatcher.group(2) ?: return null
            val timestamp = parseDateTime(dateStr, timeStr)

            // 가맹점 추출
            val storeMatcher = storePattern.matcher(smsBody)
            if (!storeMatcher.find()) return null
            val storeName = storeMatcher.group(1) ?: return null

            // 지역 정보 추출
            val region = extractRegion(storeName)

            // 카드 타입 추출 (신용/체크/교통)
            var cardType = "신용카드" // 기본값
            val cardTypeMatcher = cardTypePattern.matcher(smsBody)
            if (cardTypeMatcher.find()) {
                cardType = cardTypeMatcher.group(1) ?: "신용카드"
            }

            // 결제방식 추출 (교통카드의 경우 결제방식 항목이 없을 수 있음)
            var method = "일시불" // 기본값
            if (cardType != "교통카드") {
                val methodMatcher = methodPattern.matcher(smsBody)
                if (methodMatcher.find()) {
                    method = methodMatcher.group(1) ?: "일시불"
                }
            }

            // 카테고리 결정
            val category = determineCategory(storeName, cardType)

            // ISO 8601 형식의 타임스탬프 문자열 생성
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val timestampStr = sdf.format(timestamp)

            return SpendingItem(
                amount = amount,
                category = category,
                store_name = storeName,
                region = region,
                method = method,
                timestamp = timestampStr,
                lat = 0.0,
                lon = 0.0
            )
        } catch (e: Exception) {
            Log.e(TAG, "SMS 파싱 오류: ${e.message}")
            return null
        }
    }

    // 가맹점 이름에서 지역 정보 추출
    private fun extractRegion(storeName: String): String {
        // 가맹점 이름에서 지역 정보 추출
        // 예: "스타벅스 강남점" -> "강남"

        Log.d(TAG, "Extracting region from store name: $storeName")

        for (region in koreanRegions) {
            if (storeName.contains(region)) {
                return region
            }
        }

        val locationPattern = Pattern.compile(".+?([\\w가-힣]+)[점|지점|센터|역|공항|터미널|매장|지하|광장]")
        val locationMatcher = locationPattern.matcher(storeName)
        if (locationMatcher.find()) {
            val potentialRegion = locationMatcher.group(1)
            if (potentialRegion.length >= 2) { // 너무 짧은 지역명 방지
                return potentialRegion
            }
        }

        return ""
    }

    private fun determineCategory(storeName: String, cardType: String): String {
        // 교통카드인 경우 바로 교통 카테고리 반환
        if (cardType == "교통카드" ||
            storeName.contains("교통") ||
            storeName.contains("버스") ||
            storeName.contains("지하철") ||
            storeName.contains("택시")) {
            return "교통"
        }

        // 일반적인 카테고리 매핑
        return CategoryMapper.mapStoreToCategory(storeName)
    }

    private fun parseDateTime(dateStr: String, timeStr: String): Date {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)

        try {
            // MM/DD HH:MM 형식의 날짜/시간을 Date로 변환
            val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            val fullDateStr = "$currentYear/$dateStr $timeStr"

            val parsedDate = dateFormat.parse(fullDateStr)

            // 만약 파싱된 날짜가 현재 날짜보다 미래라면, 작년으로 조정
            if (parsedDate != null && parsedDate.after(Date()) &&
                !isSameDay(parsedDate, Date())) {
                calendar.time = parsedDate
                calendar.add(Calendar.YEAR, -1)
                return calendar.time
            }

            return parsedDate ?: Date()
        } catch (e: ParseException) {
            Log.e(TAG, "날짜 파싱 오류: ${e.message}")
            return Date()
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}