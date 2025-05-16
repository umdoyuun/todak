package com.example.todak.util

object CategoryMapper {
    private val categoryStoreMap = mapOf(
        "간식" to listOf("GS25", "CU", "스타벅스", "투썸", "이디야"),
        "생활용품" to listOf("다이소", "이마트", "홈플러스", "롯데마트", "하이마트"),
        "외식" to listOf("배달의민족", "맘스터치", "김밥천국", "마라탕", "쌀국수"),
        "교통" to listOf("버스", "지하철", "택시", "카카오택시"),
        "의류" to listOf("유니클로", "무신사", "지그재그", "H&M", "ABC마트"),
        "전자기기" to listOf("하이마트", "전자랜드", "애플스토어", "삼성디지털프라자")
    )

    fun mapStoreToCategory(storeName: String): String {
        for ((category, stores) in categoryStoreMap) {
            for (store in stores) {
                if (storeName.contains(store, ignoreCase = true)) {
                    return category
                }
            }
        }

        return "기타"
    }
}