package com.example.positionaggregator.data.brokers

import com.example.positionaggregator.data.model.Broker
import com.example.positionaggregator.data.model.BrokerCredentials
import com.example.positionaggregator.data.model.Position
import com.example.positionaggregator.data.model.PositionDirection
import com.example.positionaggregator.data.net.humanReadableMessage
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class XtbConnector(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : BrokerConnector {

    override val broker: Broker = Broker.XTB

    override suspend fun fetchPositions(credentials: BrokerCredentials): Result<List<Position>> =
        withContext(Dispatchers.IO) {
            if (credentials.xtbLogin.isBlank() || credentials.xtbPassword.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Provide XTB login and password.")
                )
            }

            runCatching {
                val webSocketClient = XtbWsSession(okHttpClient, json, credentials.xtbWsUrl)
                webSocketClient.connect()
                try {
                    val loginResponse = webSocketClient.sendCommand(
                        command = "login",
                        arguments = buildJsonObject {
                            put("userId", JsonPrimitive(credentials.xtbLogin))
                            put("password", JsonPrimitive(credentials.xtbPassword))
                        }
                    )
                    if (!loginResponse.status) {
                        error(loginResponse.errorDescr ?: "XTB login failed.")
                    }

                    val positionsResponse = webSocketClient.sendCommand(
                        command = "getTrades",
                        arguments = buildJsonObject {
                            put("openedOnly", JsonPrimitive(true))
                        }
                    )
                    if (!positionsResponse.status) {
                        error(positionsResponse.errorDescr ?: "Could not fetch XTB positions.")
                    }

                    val trades = positionsResponse.returnData?.let {
                        json.decodeFromJsonElement(ListSerializer(XtbTradePayload.serializer()), it)
                    } ?: emptyList()

                    trades.map { trade ->
                        Position(
                            broker = Broker.XTB,
                            symbol = trade.symbol,
                            quantity = abs(trade.volume),
                            direction = if (trade.cmd in listOf(0, 2, 4)) {
                                PositionDirection.LONG
                            } else {
                                PositionDirection.SHORT
                            },
                            avgOpenPrice = trade.openPrice,
                            markPrice = null,
                            unrealizedPnl = trade.profit,
                            currency = trade.currency,
                            accountId = credentials.xtbLogin
                        )
                    }
                } finally {
                    webSocketClient.close()
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(IllegalStateException(it.humanReadableMessage(), it)) }
            )
        }
}

private class XtbWsSession(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val wsUrl: String
) {
    private val connectSignal = ArrayBlockingQueue<Boolean>(1)
    private val responses = ArrayBlockingQueue<XtbResponse>(8)
    private val failureRef = AtomicReference<Throwable?>(null)
    private lateinit var socket: WebSocket

    fun connect() {
        val request = Request.Builder().url(wsUrl).build()
        socket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    connectSignal.offer(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching {
                        json.decodeFromString(XtbResponse.serializer(), text)
                    }.onSuccess { parsed ->
                        responses.offer(parsed)
                    }.onFailure {
                        failureRef.set(it)
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    failureRef.set(t)
                    connectSignal.offer(false)
                }
            }
        )

        val connected = connectSignal.poll(10, TimeUnit.SECONDS)
        if (connected != true) {
            throw IllegalStateException("Timed out connecting to XTB websocket.")
        }
        failureRef.get()?.let { throw it }
    }

    fun sendCommand(command: String, arguments: JsonObject): XtbResponse {
        val payload = XtbCommand(command = command, arguments = arguments)
        val sent = socket.send(json.encodeToString(XtbCommand.serializer(), payload))
        if (!sent) {
            throw IllegalStateException("Failed to send XTB command: $command")
        }

        val response = responses.poll(10, TimeUnit.SECONDS)
            ?: throw IllegalStateException("Timed out waiting for XTB response.")
        failureRef.get()?.let { throw it }
        return response
    }

    fun close() {
        if (::socket.isInitialized) {
            socket.close(1000, "done")
        }
    }
}

@Serializable
private data class XtbCommand(
    val command: String,
    val arguments: JsonObject
)

@Serializable
private data class XtbResponse(
    val status: Boolean,
    @SerialName("errorDescr")
    val errorDescr: String? = null,
    @SerialName("returnData")
    val returnData: JsonElement? = null
)

@Serializable
private data class XtbTradePayload(
    val symbol: String,
    val cmd: Int,
    @SerialName("open_price")
    val openPrice: Double? = null,
    val volume: Double,
    val profit: Double? = null,
    val currency: String? = null
)
