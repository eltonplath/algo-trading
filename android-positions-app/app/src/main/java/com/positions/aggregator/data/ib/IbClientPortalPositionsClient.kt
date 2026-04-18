package com.positions.aggregator.data.ib

import com.positions.aggregator.domain.Broker
import com.positions.aggregator.domain.BrokerFetchResult
import com.positions.aggregator.domain.BrokerPosition
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches open positions via the IB Client Portal Web API (local gateway on https://localhost:5000/v1/api).
 * Requires the IBKR Client Portal Gateway running and authenticated on the device or reachable network.
 */
class IbClientPortalPositionsClient(
    private val http: OkHttpClient = defaultClient()
) {

    fun fetch(baseUrl: String): BrokerFetchResult {
        val root = baseUrl.trim().trimEnd('/')
        if (root.isBlank()) {
            return BrokerFetchResult(Broker.INTERACTIVE_BROKERS, error = "IB base URL not set.")
        }
        return try {
            val accounts = loadAccounts(root)
            if (accounts.isEmpty()) {
                return BrokerFetchResult(
                    Broker.INTERACTIVE_BROKERS,
                    error = "No IB accounts returned. Open the Client Portal Gateway and call /portfolio/accounts first."
                )
            }
            val all = mutableListOf<BrokerPosition>()
            for (accountId in accounts) {
                all += loadAllPages(root, accountId)
            }
            BrokerFetchResult(Broker.INTERACTIVE_BROKERS, positions = all)
        } catch (e: Exception) {
            BrokerFetchResult(Broker.INTERACTIVE_BROKERS, error = e.message ?: e.toString())
        }
    }

    private fun loadAccounts(root: String): List<String> {
        val url = "$root/portfolio/accounts"
        val body = get(url)
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            val ids = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val el = arr.get(i)
                when (el) {
                    is String -> ids += el
                    is JSONObject -> {
                        val id = el.optString("id").ifBlank { el.optString("accountId") }
                        if (id.isNotBlank()) ids += id
                    }
                }
            }
            return ids
        }
        val obj = JSONObject(trimmed)
        val arr = obj.optJSONArray("accounts")
        if (arr != null) {
            val ids = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val el = arr.get(i)
                when (el) {
                    is String -> ids += el
                    is JSONObject -> {
                        val id = el.optString("id").ifBlank { el.optString("accountId") }
                        if (id.isNotBlank()) ids += id
                    }
                }
            }
            return ids
        }
        return emptyList()
    }

    private fun loadAllPages(root: String, accountId: String): List<BrokerPosition> {
        val out = mutableListOf<BrokerPosition>()
        var page = 0
        while (page < MAX_PAGES) {
            val url = "$root/portfolio/$accountId/positions/$page"
            val body = get(url)
            val trimmed = body.trim()
            val arr = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("positions")
                        ?: obj.optJSONArray("portfolioPositions")
                        ?: JSONArray()
                }
                else -> JSONArray()
            }
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val symbol = p.optString("contractDesc")
                    .ifBlank { p.optString("name") }
                    .ifBlank { p.optString("ticker") }
                    .ifBlank { "conid ${p.optInt("conid", p.optLong("conid", 0L))}" }
                val qty = p.optDouble("position", Double.NaN).let { d ->
                    if (d.isNaN()) p.optString("position").ifBlank { "—" } else d.toString()
                }
                val side = when {
                    qty == "—" -> "—"
                    qty.startsWith("-") -> "Short"
                    qty == "0" || qty == "0.0" -> "Flat"
                    else -> "Long"
                }
                val detail = listOfNotNull(
                    p.optString("currency").takeIf { it.isNotBlank() }?.let { "ccy $it" },
                    p.optString("assetClass").takeIf { it.isNotBlank() }?.let { it },
                    "acct $accountId"
                ).joinToString(" · ")
                out += BrokerPosition(
                    broker = Broker.INTERACTIVE_BROKERS,
                    symbol = symbol,
                    side = side,
                    quantity = qty.trimStart('+'),
                    detail = detail
                )
            }
            page++
        }
        return out
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("IB HTTP ${response.code}: ${response.message}\n$raw")
            }
            return raw
        }
    }

    companion object {
        private const val MAX_PAGES = 50

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
