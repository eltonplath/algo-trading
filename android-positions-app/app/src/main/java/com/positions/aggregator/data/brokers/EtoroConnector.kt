package com.positions.aggregator.data.brokers

import com.positions.aggregator.data.model.Broker
import com.positions.aggregator.data.model.BrokerCredentials
import com.positions.aggregator.data.model.Position
import com.positions.aggregator.data.model.PositionDirection
import com.positions.aggregator.data.net.humanReadableMessage
import com.positions.aggregator.data.net.requireSuccessBody
import kotlin.math.abs
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class EtoroConnector(
    private val httpClient: OkHttpClient,
    private val json: Json
) : BrokerConnector {

    override val broker: Broker = Broker.ETORO

    override suspend fun fetchPositions(credentials: BrokerCredentials): Result<List<Position>> =
        withContext(Dispatchers.IO) {
            when (credentials.etoroAuthMode) {
                BrokerCredentials.ETORO_AUTH_API_KEY -> fetchApiKeyMode(credentials)
                else -> fetchBearerMode(credentials)
            }
        }

    private fun fetchBearerMode(credentials: BrokerCredentials): Result<List<Position>> {
        if (credentials.etoroBearerToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Provide eToro bearer token (Public API)."))
        }
        val base = credentials.etoroApiBaseUrl.trim().trimEnd('/')
        val path = if (credentials.etoroBearerDemo) {
            "/api/v1/trading/info/demo/pnl"
        } else {
            "/api/v1/trading/info/real/pnl"
        }
        val url = "$base$path"
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer ${credentials.etoroBearerToken.trim()}")
                .addHeader("x-request-id", UUID.randomUUID().toString())
                .build()
            val body = httpClient.newCall(request).execute().use { it.requireSuccessBody() }
            parseClientPortfolio(json.parseToJsonElement(body).jsonObject)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IllegalStateException(it.humanReadableMessage(), it)) }
        )
    }

    private fun fetchApiKeyMode(credentials: BrokerCredentials): Result<List<Position>> {
        if (credentials.etoroApiKey.isBlank() || credentials.etoroUserKey.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Provide eToro API key and user key (partner API), or switch to Bearer mode.")
            )
        }
        val base = credentials.etoroApiBaseUrl.trim().trimEnd('/')
        val url = "$base/api/v1/trading/info/portfolio"
        return runCatching {
            val b = Request.Builder()
                .url(url)
                .get()
                .addHeader("x-request-id", UUID.randomUUID().toString())
                .addHeader("x-api-key", credentials.etoroApiKey.trim())
                .addHeader("x-user-key", credentials.etoroUserKey.trim())
            if (credentials.etoroAccountId.isNotBlank()) {
                b.addHeader("x-account-id", credentials.etoroAccountId.trim())
            }
            val body = httpClient.newCall(b.build()).execute().use { it.requireSuccessBody() }
            parseClientPortfolio(json.parseToJsonElement(body).jsonObject)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IllegalStateException(it.humanReadableMessage(), it)) }
        )
    }

    private fun parseClientPortfolio(root: JsonObject): List<Position> {
        val portfolio = root["clientPortfolio"]?.jsonObject
            ?: root["ClientPortfolio"]?.jsonObject
            ?: root["data"]?.jsonObject?.get("clientPortfolio")?.jsonObject
            ?: error("eToro: clientPortfolio missing in response.")

        val out = mutableListOf<Position>()
        portfolio["positions"]?.jsonArray?.let { out += mapEtoroRows(it, "Direct") }
        val mirrors = portfolio["mirrors"]?.jsonArray ?: portfolio["Mirrors"]?.jsonArray
        if (mirrors != null) {
            for (m in mirrors) {
                val mo = m.jsonObject
                val label = mo["userName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: "Mirror ${mo["mirrorID"]?.jsonPrimitive?.contentOrNull.orEmpty()}"
                mo["positions"]?.jsonArray?.let { out += mapEtoroRows(it, label) }
            }
        }
        return out.filter { it.quantity != 0.0 }
    }

    private fun mapEtoroRows(arr: JsonArray, bucket: String): List<Position> =
        arr.mapNotNull { el -> mapEtoroPosition(el.jsonObject, bucket) }

    private fun mapEtoroPosition(obj: JsonObject, bucket: String): Position? {
        val symbol = obj.string("instrumentDisplayName")
            ?: obj.string("symbolFull")
            ?: obj.string("internalSymbolFull")
            ?: obj.string("instrumentID")
            ?: obj.string("instrumentId")
            ?: obj.string("positionID")
            ?: obj.string("positionId")
            ?: "Unknown"

        val qty = obj.double("units")
            ?: obj.double("openUnits")
            ?: obj.string("units")?.toDoubleOrNull()
            ?: obj.string("openUnits")?.toDoubleOrNull()
            ?: obj.double("amount")
            ?: obj.string("amount")?.toDoubleOrNull()
            ?: 0.0

        val isBuy = obj["isBuy"]?.jsonPrimitive?.booleanOrNull ?: true
        val openRate = obj.double("openRate") ?: obj.string("openRate")?.toDoubleOrNull()
        val netProfit = obj.double("netProfit") ?: obj.string("netProfit")?.toDoubleOrNull()
        val currency = obj.string("currencyCode")

        return Position(
            broker = Broker.ETORO,
            symbol = symbol,
            quantity = abs(qty),
            direction = if (isBuy) PositionDirection.LONG else PositionDirection.SHORT,
            avgOpenPrice = openRate,
            markPrice = obj.double("marketRate") ?: obj.string("marketRate")?.toDoubleOrNull(),
            unrealizedPnl = netProfit,
            currency = currency,
            accountId = bucket
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.double(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull
}
