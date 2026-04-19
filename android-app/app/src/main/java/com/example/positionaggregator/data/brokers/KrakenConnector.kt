package com.example.positionaggregator.data.brokers

import android.util.Base64
import com.example.positionaggregator.data.model.Broker
import com.example.positionaggregator.data.model.BrokerCredentials
import com.example.positionaggregator.data.model.Position
import com.example.positionaggregator.data.model.PositionDirection
import com.example.positionaggregator.data.net.humanReadableMessage
import com.example.positionaggregator.data.net.requireSuccessBody
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class KrakenConnector(
    private val httpClient: OkHttpClient,
    private val json: Json
) : BrokerConnector {

    override val broker: Broker = Broker.KRAKEN

    override suspend fun fetchPositions(credentials: BrokerCredentials): Result<List<Position>> =
        withContext(Dispatchers.IO) {
            if (credentials.krakenApiKey.isBlank() || credentials.krakenApiSecret.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Provide Kraken API key and secret.")
                )
            }

            runCatching {
                val nonce = System.currentTimeMillis().toString()
                val path = "/0/private/OpenPositions"
                val body = FormBody.Builder()
                    .add("nonce", nonce)
                    .add("docalcs", "true")
                    .build()

                val postData = "nonce=$nonce&docalcs=true"
                val apiSign = buildApiSign(path, nonce, postData, credentials.krakenApiSecret)

                val request = Request.Builder()
                    .url("https://api.kraken.com$path")
                    .post(body)
                    .addHeader("API-Key", credentials.krakenApiKey)
                    .addHeader("API-Sign", apiSign)
                    .build()

                val responseBody = httpClient.newCall(request).execute().use { it.requireSuccessBody() }
                val response = json.decodeFromString(KrakenOpenPositionsResponse.serializer(), responseBody)
                if (response.error.isNotEmpty()) {
                    error("Kraken error: ${response.error.joinToString("; ")}")
                }

                response.result.values.map { openPosition ->
                    Position(
                        broker = Broker.KRAKEN,
                        symbol = openPosition.pair,
                        quantity = openPosition.volume.toDoubleOrNull() ?: 0.0,
                        direction = if (openPosition.type.equals("buy", ignoreCase = true)) {
                            PositionDirection.LONG
                        } else {
                            PositionDirection.SHORT
                        },
                        avgOpenPrice = openPosition.cost.toDoubleOrNull(),
                        markPrice = openPosition.currentPrice?.toDoubleOrNull(),
                        unrealizedPnl = openPosition.net?.toDoubleOrNull(),
                        currency = null,
                        accountId = null
                    )
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(IllegalStateException(it.humanReadableMessage(), it)) }
            )
        }

    private fun buildApiSign(path: String, nonce: String, postData: String, secret: String): String {
        val decodedSecret = Base64.decode(secret, Base64.DEFAULT)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val shaInput = nonce + postData
        val shaHash = sha256.digest(shaInput.toByteArray())
        val pathBytes = path.toByteArray()
        val hmacInput = ByteArray(pathBytes.size + shaHash.size)
        System.arraycopy(pathBytes, 0, hmacInput, 0, pathBytes.size)
        System.arraycopy(shaHash, 0, hmacInput, pathBytes.size, shaHash.size)

        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(decodedSecret, "HmacSHA512"))
        val hmac = mac.doFinal(hmacInput)
        return Base64.encodeToString(hmac, Base64.NO_WRAP)
    }
}

@Serializable
private data class KrakenOpenPositionsResponse(
    val error: List<String> = emptyList(),
    val result: Map<String, KrakenOpenPositionPayload> = emptyMap()
)

@Serializable
private data class KrakenOpenPositionPayload(
    @SerialName("pair")
    val pair: String,
    @SerialName("type")
    val type: String,
    @SerialName("vol")
    val volume: String,
    @SerialName("cost")
    val cost: String? = null,
    @SerialName("net")
    val net: String? = null,
    @SerialName("cprice")
    val currentPrice: String? = null
)
