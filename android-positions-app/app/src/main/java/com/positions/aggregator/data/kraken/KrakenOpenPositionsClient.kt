package com.positions.aggregator.data.kraken

import com.positions.aggregator.domain.Broker
import com.positions.aggregator.domain.BrokerFetchResult
import com.positions.aggregator.domain.BrokerPosition
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class KrakenOpenPositionsClient(
    private val http: OkHttpClient = defaultClient()
) {

    fun fetch(apiKey: String, apiSecret: String): BrokerFetchResult {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return BrokerFetchResult(Broker.KRAKEN, error = "Kraken API key or secret not set.")
        }
        val path = "/0/private/OpenPositions"
        val nonce = System.currentTimeMillis().toString()
        val postBody = "nonce=$nonce"
        val sign = KrakenSigner.sign(path, postBody, apiSecret)
        val url = "https://api.kraken.com$path"
        val body = postBody.toRequestBody(FORM)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("API-Key", apiKey.trim())
            .header("API-Sign", sign)
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return BrokerFetchResult(
                        Broker.KRAKEN,
                        error = "Kraken HTTP ${response.code}: ${response.message}\n$raw"
                    )
                }
                parseResponse(raw)
            }
        } catch (e: Exception) {
            BrokerFetchResult(Broker.KRAKEN, error = e.message ?: e.toString())
        }
    }

    private fun parseResponse(raw: String): BrokerFetchResult {
        val root = JSONObject(raw)
        val errors = root.optJSONArray("error")
        if (errors != null && errors.length() > 0) {
            val msg = (0 until errors.length()).joinToString("; ") { errors.getString(it) }
            return BrokerFetchResult(Broker.KRAKEN, error = msg)
        }
        val result = root.optJSONObject("result") ?: return BrokerFetchResult(
            Broker.KRAKEN,
            error = "Kraken: missing result object."
        )
        val openBlock = result.optJSONObject("open") ?: result
        val positions = mutableListOf<BrokerPosition>()
        val keys = openBlock.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val obj = openBlock.optJSONObject(id) ?: continue
            val pair = obj.optString("pair").ifBlank { obj.optString("instrument") }
            val type = obj.optString("type").ifBlank { "—" }
            val vol = obj.optString("vol").ifBlank { obj.optString("volume") }
            val cost = obj.optString("cost").ifBlank { null }
            val detail = listOfNotNull(
                if (cost != null) "cost $cost" else null,
                if (obj.has("net")) "net ${obj.optString("net")}" else null
            ).joinToString(" · ").ifBlank { null }
            positions += BrokerPosition(
                broker = Broker.KRAKEN,
                symbol = pair.ifBlank { id },
                side = type,
                quantity = vol.ifBlank { "—" },
                detail = detail
            )
        }
        return BrokerFetchResult(Broker.KRAKEN, positions = positions)
    }

    companion object {
        private val FORM = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
    }
}
