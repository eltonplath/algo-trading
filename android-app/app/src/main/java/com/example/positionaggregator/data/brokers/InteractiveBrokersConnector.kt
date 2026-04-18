package com.example.positionaggregator.data.brokers

import com.example.positionaggregator.data.model.Broker
import com.example.positionaggregator.data.model.BrokerCredentials
import com.example.positionaggregator.data.model.Position
import com.example.positionaggregator.data.model.PositionDirection
import com.example.positionaggregator.data.net.humanReadableMessage
import com.example.positionaggregator.data.net.requireSuccessBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
            if (credentials.ibAccountId.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Provide Interactive Brokers account id.")
                )
            }

            runCatching {
                val normalizedBaseUrl = credentials.ibBaseUrl.trimEnd('/')
                val endpoint = "$normalizedBaseUrl/portfolio/${credentials.ibAccountId}/positions/0"
                val requestBuilder = Request.Builder().url(endpoint).get()

                val token = credentials.ibSessionToken.trim()
                if (token.isNotBlank()) {
                    if (token.startsWith("Bearer ", ignoreCase = true)) {
                        requestBuilder.addHeader("Authorization", token)
                    } else if (token.contains("=")) {
                        requestBuilder.addHeader("Cookie", token)
                    } else {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                val responseBody = httpClient.newCall(requestBuilder.build()).execute().use {
                    it.requireSuccessBody()
                }
                val payload = json.parseToJsonElement(responseBody)
                parsePositions(payload)
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(IllegalStateException(it.humanReadableMessage(), it)) }
            )
        }

    private fun parsePositions(payload: JsonElement): List<Position> {
        val rows = when (payload) {
            is JsonArray -> payload
            is JsonObject -> payload["positions"]?.jsonArray ?: emptyList<JsonElement>().toJsonArray()
            else -> emptyList<JsonElement>().toJsonArray()
        }
        return rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val quantity = obj.doubleValue("position") ?: obj.doubleValue("pos")
            val symbol = obj.stringValue("contractDesc")
                ?: obj.stringValue("symbol")
                ?: obj.stringValue("ticker")
                ?: obj.stringValue("conid")
                ?: return@mapNotNull null
            if (quantity == null || quantity == 0.0) return@mapNotNull null

            Position(
                broker = Broker.INTERACTIVE_BROKERS,
                symbol = symbol,
                quantity = kotlin.math.abs(quantity),
                direction = if (quantity >= 0.0) PositionDirection.LONG else PositionDirection.SHORT,
                avgOpenPrice = obj.doubleValue("avgCost"),
                markPrice = obj.doubleValue("mktPrice"),
                unrealizedPnl = obj.doubleValue("unrealizedPnl"),
                currency = obj.stringValue("currency"),
                accountId = obj.stringValue("acctId") ?: obj.stringValue("account")
            )
        }
    }

    private fun JsonObject.doubleValue(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun List<JsonElement>.toJsonArray(): JsonArray = JsonArray(this)
}
