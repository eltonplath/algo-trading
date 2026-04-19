package com.positions.aggregator.data.net

import java.io.IOException
import okhttp3.Response

internal fun Response.requireSuccessBody(): String {
    val responseBody = body?.string().orEmpty()
    if (!isSuccessful) {
        throw IOException("HTTP $code: $responseBody")
    }
    return responseBody
}

internal fun Throwable.humanReadableMessage(): String {
    return when (this) {
        is IOException -> message ?: "Network error"
        else -> message ?: "Unexpected error"
    }
}
