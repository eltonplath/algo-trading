package com.positions.aggregator.data.xtb

import com.positions.aggregator.domain.Broker
import com.positions.aggregator.domain.BrokerFetchResult
import com.positions.aggregator.domain.BrokerPosition
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Minimal xAPI client: login then getTrades(openedOnly=true) over WebSocket.
 * @see <a href="https://peterszombati.github.io/xapi-node/">xAPI protocol</a>
 */
class XtbXApiTradesClient(
    private val http: OkHttpClient = defaultClient()
) {

    fun fetch(userId: String, password: String, demo: Boolean): BrokerFetchResult {
        if (userId.isBlank() || password.isBlank()) {
            return BrokerFetchResult(Broker.XTB, error = "XTB user ID or password not set.")
        }
        val host = if (demo) "wss://ws.xtb.com/demo" else "wss://ws.xtb.com/real"
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<BrokerFetchResult?>(null)
        val request = Request.Builder().url(host).build()
        val ws = http.newWebSocket(
            request,
            object : WebSocketListener() {
                private var loggedIn = false

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val login = JSONObject()
                        .put("command", "login")
                        .put(
                            "arguments",
                            JSONObject()
                                .put("userId", userId.trim())
                                .put("password", password.trim())
                                .put("appName", "PositionsAggregator")
                        )
                    webSocket.send(login.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    for (chunk in text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }) {
                        val json = runCatching { JSONObject(chunk) }.getOrNull() ?: continue
                        if (!loggedIn) {
                            if (json.optBoolean("status")) {
                                loggedIn = true
                                val tradesReq = JSONObject()
                                    .put("command", "getTrades")
                                    .put(
                                        "arguments",
                                        JSONObject().put("openedOnly", true)
                                    )
                                webSocket.send(tradesReq.toString())
                            } else {
                                finish(
                                    webSocket,
                                    BrokerFetchResult(
                                        Broker.XTB,
                                        error = "XTB login: ${json.optString("errorDescr")} (${json.optString("errorCode")})"
                                    )
                                )
                            }
                            continue
                        }
                        if (json.optBoolean("status")) {
                            val arr = json.optJSONArray("returnData") ?: JSONArray()
                            val positions = mutableListOf<BrokerPosition>()
                            for (i in 0 until arr.length()) {
                                val t = arr.optJSONObject(i) ?: continue
                                val symbol = t.optString("symbol").ifBlank { "—" }
                                val cmd = t.optInt("cmd", -1)
                                val side = when (cmd) {
                                    0 -> "Buy"
                                    1 -> "Sell"
                                    else -> "—"
                                }
                                val vol = t.optDouble("volume", Double.NaN).let { d ->
                                    if (d.isNaN()) t.optString("volume").ifBlank { "—" } else d.toString()
                                }
                                val openPrice = t.optDouble("open_price", Double.NaN).let { d ->
                                    if (d.isNaN()) t.optString("open_price").ifBlank { null } else d.toString()
                                }
                                val order = t.optLong("order", 0L).takeIf { it != 0L }
                                val detail = listOfNotNull(
                                    openPrice?.let { "open $it" },
                                    order?.let { "order $it" }
                                ).joinToString(" · ").ifBlank { null }
                                positions += BrokerPosition(
                                    broker = Broker.XTB,
                                    symbol = symbol,
                                    side = side,
                                    quantity = vol,
                                    detail = detail
                                )
                            }
                            finish(webSocket, BrokerFetchResult(Broker.XTB, positions = positions))
                        } else {
                            finish(
                                webSocket,
                                BrokerFetchResult(
                                    Broker.XTB,
                                    error = "XTB getTrades: ${json.optString("errorDescr")} (${json.optString("errorCode")})"
                                )
                            )
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    resultRef.compareAndSet(
                        null,
                        BrokerFetchResult(Broker.XTB, error = t.message ?: t.toString())
                    )
                    latch.countDown()
                }

                private fun finish(webSocket: WebSocket, result: BrokerFetchResult) {
                    resultRef.compareAndSet(null, result)
                    runCatching {
                        webSocket.send(JSONObject().put("command", "logout").toString())
                    }
                    webSocket.close(1000, null)
                    latch.countDown()
                }
            }
        )
        val ok = latch.await(45, TimeUnit.SECONDS)
        if (!ok) {
            runCatching { ws.cancel() }
            return BrokerFetchResult(Broker.XTB, error = "XTB request timed out.")
        }
        return resultRef.get() ?: BrokerFetchResult(Broker.XTB, error = "XTB: no response.")
    }

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
    }
}
