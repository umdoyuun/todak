package com.example.todak.data.model

data class StoreListResponse(
    val user_id: String,
    val stores: List<StoreItem>
)

// 개별 가게 항목
data class StoreItem(
    val store_id: String,
    val store_info: StoreInfo
)

// 가게 상세 정보
data class StoreInfo(
    val store_name: String,
    val store_image: String?,
    val start_date: String,
    val end_date: String?,
    val role: String,
    val status: String
)

data class MenuListResponse(
    val store_id: String,
    val manuals: List<MenuResponse>
)

// 개별 메뉴 항목
data class MenuResponse(
    val category: String,
    val title: String,
    val title_img: String
)

data class ManualDetailResponse(
    val store_id: String,
    val creator_id: String,
    val category: String,
    val title: String,
    val title_img: String,
    val steps: Map<String, Step>,  // step1, step2, ... 와 같은 키를 가진 Map
    val created_at: String,
    val updated_at: String
)

data class Step(
    val des: String,
    val img: String
)

data class OcrResponse(
    val menus: List<MenuOcrItem>
)

data class MenuOcrItem(
    val title: String,
    val count: String
)