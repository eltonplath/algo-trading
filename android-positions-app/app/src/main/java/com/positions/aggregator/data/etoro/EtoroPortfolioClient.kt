package com.positions.aggregator.data.etoro

import com.positions.aggregator.domain.Broker
import com.positions.aggregator.domain.BrokerFetchResult
import com.positions.aggregator.domain.BrokerPosition
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Uses eToro Public API PnL endpoint which includes open positions under clientPortfolio.
 * Base: https://public-api.etoro.com/api/v1
 * @see <a href="https://api-portal.etoro.com/">eToro API portal</a>
 */
class EtoroPortfolioClient(
    private val http: OkHttpClient = defaultClient()
) {

    fun fetch(bearerToken: String, demo: Boolean): BrokerFetchResult {
        if (bearerToken.isBlank()) {
            return BrokerFetchResult(Broker.ETORO, error = "eToro bearer token not set.")
        }
        val path = if (demo) "/trading/info/demo/pnl" else "/trading/info/real/pnl"
        val url = "https://public-api.etoro.com/api/v1$path"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer ${bearerToken.trim()}")
            .header("x-request-id", UUID.randomUUID().toString())
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return BrokerFetchResult(
                        Broker.ETORO,
                        error = "eToro HTTP ${response.code}: ${response.message}\n$raw"
                    )
                }
                parsePnl(raw)
            }
        } catch (e: Exception) {
            BrokerFetchResult(Broker.ETORO, error = e.message ?: e.toString())
        }
    }

    private fun parsePnl(raw: String): BrokerFetchResult {
        val root = JSONObject(raw)
        val portfolio = root.optJSONObject("clientPortfolio")
            ?: root.optJSONObject("ClientPortfolio")
            ?: root.optJSONObject("data")?.optJSONObject("clientPortfolio")
            ?: return BrokerFetchResult(Broker.ETORO, error = "eToro: clientPortfolio missing in response.")
        val positions = mutableListOf<BrokerPosition>()
        appendPositions(positions, portfolio.optJSONArray("positions"), "Direct")
        val mirrors = portfolio.optJSONArray("mirrors") ?: portfolio.optJSONArray("Mirrors")
        if (mirrors != null) {
            for (i in 0 until mirrors.length()) {
                val m = mirrors.optJSONObject(i) ?: continue
                val label = m.optString("userName").ifBlank { "Mirror ${m.optLong("mirrorID", 0L)}" }
                appendPositions(positions, m.optJSONArray("positions"), label)
            }
        }
        return BrokerFetchResult(Broker.ETORO, positions = positions)
    }

    private fun appendPositions(
        out: MutableList<BrokerPosition>,
        arr: JSONArray?,
        bucket: String
    ) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val symbol = p.optString("instrumentDisplayName")
                .ifBlank { p.optString("symbolFull") }
                .ifBlank { p.optString("internalSymbolFull") }
                .ifBlank { "instrument ${p.optLong("instrumentID", p.optLong("instrumentId", 0L))}" }
            val isBuy = p.optBoolean("isBuy", true)
            val side = if (isBuy) "Buy" else "Sell"
            val units = p.optDouble("units", Double.NaN).let { d ->
                if (d.isNaN()) p.optString("units").ifBlank { p.optString("openUnits") } else d.toString()
            }
            val amount = p.optDouble("amount", Double.NaN).let { d ->
                if (d.isNaN()) p.optString("amount").ifBlank { null } else d.toString()
            }
            val pid = p.optLong("positionID", p.optLong("positionId", 0L)).takeIf { it != 0L }
            val detail = listOfNotNull(
                "bucket $bucket",
                amount?.let { "invested $it" },
                pid?.let { "position $it" }
            ).joinToString(" · ")
            out += BrokerPosition(
                broker = Broker.ETORO,
                symbol = symbol,
                side = side,
                quantity = units.ifBlank { "—" },
                detail = detail
            )
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
