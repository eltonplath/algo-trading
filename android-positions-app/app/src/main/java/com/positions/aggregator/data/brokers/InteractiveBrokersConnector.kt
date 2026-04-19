package com.positions.aggregator.data.brokers

import com.positions.aggregator.data.model.Broker
import com.positions.aggregator.data.model.BrokerCredentials
import com.positions.aggregator.data.model.Position
import com.positions.aggregator.data.model.PositionDirection
import com.positions.aggregator.data.net.humanReadableMessage
import com.positions.aggregator.data.net.requireSuccessBody
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class InteractiveBrokersConnector(
    private val httpClient: OkHttpClient,
    private val json: Json
) : BrokerConnector {

    override val broker: Broker = Broker.INTERACTIVE_BROKERS

    override suspend fun fetchPositions(credentials: BrokerCredentials): Result<List<Position>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = credentials.ibBaseUrl.trim().trimEnd('/')
                if (base.isBlank()) {
                    error("Provide Interactive Brokers Client Portal base URL.")
                }

                val accountIds = if (credentials.ibAccountId.isNotBlank()) {
                    listOf(credentials.ibAccountId.trim())
                } else {
                    loadAccountIds(base, credentials)
                }
                if (accountIds.isEmpty()) {
                    error("No IB accounts returned. Log in via Client Portal Gateway, or set Account ID.")
                }

                val out = mutableListOf<Position>()
                for (acct in accountIds) {
                    var page = 0
                    while (page < MAX_PAGES) {
                        val endpoint = "$base/portfolio/$acct/positions/$page"
                        val responseBody = httpClient.newCall(
                            buildGet(endpoint, credentials)
                        ).execute().use { it.requireSuccessBody() }
                        val payload = json.parseToJsonElement(responseBody)
                        val rows = positionsArray(payload)
                        if (rows.isEmpty()) break
                        out += parseRows(rows, acct)
                        page++
                    }
                }
                out
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(IllegalStateException(it.humanReadableMessage(), it)) }
            )
        }

    private fun loadAccountIds(base: String, credentials: BrokerCredentials): List<String> {
        val body = httpClient.newCall(buildGet("$base/portfolio/accounts", credentials)).execute()
            .use { it.requireSuccessBody() }
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            val arr = json.parseToJsonElement(trimmed).jsonArray
            return extractAccountIds(arr)
        }
        val obj = json.parseToJsonElement(trimmed).jsonObject
        val arr = obj["accounts"]?.jsonArray ?: return emptyList()
        return extractAccountIds(arr)
    }

    private fun extractAccountIds(arr: JsonArray): List<String> {
        val ids = mutableListOf<String>()
        for (el in arr) {
            when (el) {
                is JsonObject -> {
                    val id = el["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: el["accountId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    if (id != null) ids += id
                }
                is JsonPrimitive -> {
                    val s = el.contentOrNull
                    if (!s.isNullOrBlank()) ids += s
                }
                else -> Unit
            }
        }
        return ids
    }

    private fun buildGet(url: String, credentials: BrokerCredentials): Request {
        val b = Request.Builder().url(url).get()
        val token = credentials.ibSessionToken.trim()
        if (token.isNotBlank()) {
            if (token.startsWith("Bearer ", ignoreCase = true)) {
                b.addHeader("Authorization", token)
            } else if (token.contains("=")) {
                b.addHeader("Cookie", token)
            } else {
                b.addHeader("Authorization", "Bearer $token")
            }
        }
        return b.build()
    }

    private fun positionsArray(payload: JsonElement): JsonArray = when (payload) {
        is JsonArray -> payload
        is JsonObject -> payload["positions"]?.jsonArray
            ?: payload["portfolioPositions"]?.jsonArray
            ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }

    private fun parseRows(rows: JsonArray, defaultAcct: String): List<Position> =
        rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val rawQty = obj.doubleValue("position") ?: obj.doubleValue("pos") ?: return@mapNotNull null
            if (rawQty == 0.0) return@mapNotNull null
            val symbol = obj.stringValue("contractDesc")
                ?: obj.stringValue("symbol")
                ?: obj.stringValue("ticker")
                ?: obj.stringValue("conid")
                ?: return@mapNotNull null

            Position(
                broker = Broker.INTERACTIVE_BROKERS,
                symbol = symbol,
                quantity = abs(rawQty),
                direction = if (rawQty >= 0.0) PositionDirection.LONG else PositionDirection.SHORT,
                avgOpenPrice = obj.doubleValue("avgCost"),
                markPrice = obj.doubleValue("mktPrice"),
                unrealizedPnl = obj.doubleValue("unrealizedPnl"),
                currency = obj.stringValue("currency"),
                accountId = obj.stringValue("acctId") ?: obj.stringValue("account") ?: defaultAcct
            )
        }

    private fun JsonObject.doubleValue(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        private const val MAX_PAGES = 50
    }
}
