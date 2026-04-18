package com.example.positionaggregator.data.brokers

import com.example.positionaggregator.data.model.Broker
import com.example.positionaggregator.data.model.BrokerCredentials
import com.example.positionaggregator.data.model.Position
import com.example.positionaggregator.data.model.PositionDirection
import com.example.positionaggregator.data.net.humanReadableMessage
import com.example.positionaggregator.data.net.requireSuccessBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class EtoroConnector(
    private val httpClient: OkHttpClient,
    private val json: Json
) : BrokerConnector {

    override val broker: Broker = Broker.ETORO

    override suspend fun fetchPositions(credentials: BrokerCredentials): Result<List<Position>> =
        withContext(Dispatchers.IO) {
            if (credentials.etoroApiKey.isBlank() || credentials.etoroUserKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Provide eToro API key and user key. Access requires eToro partner API credentials."
                    )
                )
            }

            runCatching {
                val baseUrl = credentials.etoroApiBaseUrl.trimEnd('/')
                val requestBuilder = Request.Builder()
                    .url("$baseUrl/api/v1/trading/info/portfolio")
                    .get()
                    .addHeader("x-request-id", java.util.UUID.randomUUID().toString())
                    .addHeader("x-api-key", credentials.etoroApiKey)
                    .addHeader("x-user-key", credentials.etoroUserKey)
                if (credentials.etoroAccountId.isNotBlank()) {
                    requestBuilder.addHeader("x-account-id", credentials.etoroAccountId)
                }
                val request = requestBuilder.build()

                val responseBody = httpClient.newCall(request).execute().use { it.requireSuccessBody() }
                val response = json.decodeFromString(EtoroPortfolioResponse.serializer(), responseBody)
                response.clientPortfolio.positions.map { row ->
                    Position(
                        broker = Broker.ETORO,
                        symbol = row.instrumentId ?: row.cid ?: row.positionId ?: "Unknown",
                        quantity = row.units?.toDoubleOrNull() ?: row.amount?.toDoubleOrNull() ?: 0.0,
                        direction = if (row.isBuy == true) {
                            PositionDirection.LONG
                        } else {
                            PositionDirection.SHORT
                        },
                        avgOpenPrice = row.openRate?.toDoubleOrNull(),
                        markPrice = null,
                        unrealizedPnl = row.netProfit?.toDoubleOrNull(),
                        currency = row.currencyCode,
                        accountId = null
                    )
                }.filter { it.quantity != 0.0 }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(IllegalStateException(it.humanReadableMessage(), it)) }
            )
        }
}

@Serializable
private data class EtoroPortfolioResponse(
    @SerialName("clientPortfolio")
    val clientPortfolio: EtoroClientPortfolio = EtoroClientPortfolio()
)

@Serializable
private data class EtoroClientPortfolio(
    val positions: List<EtoroPositionPayload> = emptyList()
)

@Serializable
private data class EtoroPositionPayload(
    @SerialName("positionID")
    val positionId: String? = null,
    @SerialName("CID")
    val cid: String? = null,
    @SerialName("instrumentID")
    val instrumentId: String? = null,
    @SerialName("isBuy")
    val isBuy: Boolean? = null,
    @SerialName("amount")
    val amount: String? = null,
    @SerialName("units")
    val units: String? = null,
    @SerialName("openRate")
    val openRate: String? = null,
    @SerialName("netProfit")
    val netProfit: String? = null,
    @SerialName("currencyCode")
    val currencyCode: String? = null
)
