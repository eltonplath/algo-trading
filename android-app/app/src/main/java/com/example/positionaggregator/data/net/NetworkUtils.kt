package com.example.positionaggregator.data.net

import java.io.IOException
import okhttp3.Response
import retrofit2.HttpException

internal fun Response.requireSuccessBody(): String {
    val responseBody = body?.string().orEmpty()
    if (!isSuccessful) {
        throw IOException("HTTP $code: $responseBody")
    }
    return responseBody
}

internal fun Throwable.humanReadableMessage(): String {
    return when (this) {
        is HttpException -> "HTTP ${code()}: ${message()}"
        is IOException -> message ?: "Network error"
        else -> message ?: "Unexpected error"
    }
}
