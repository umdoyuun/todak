package com.example.todak.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

// 예산 업데이트 이벤트를 관리하는 싱글톤 객체
object BudgetUpdateEvent {
    private val _updateEvent = MutableLiveData<Long>()
    val updateEvent: LiveData<Long> = _updateEvent

    fun triggerUpdate() {
        _updateEvent.postValue(System.currentTimeMillis())
    }
}