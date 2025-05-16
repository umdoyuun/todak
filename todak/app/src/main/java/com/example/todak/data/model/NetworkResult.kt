// NetworkResult.kt
package com.example.todak.data.model

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val errorBody: String? = null) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}