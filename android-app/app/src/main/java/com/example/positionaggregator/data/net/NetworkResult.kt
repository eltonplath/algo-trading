package com.example.positionaggregator.data.net

sealed class NetworkResult<out T> {
    data class Success<T>(val value: T) : NetworkResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : NetworkResult<Nothing>()
}
