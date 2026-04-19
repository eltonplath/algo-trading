package com.positions.aggregator.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BrokerCredentials(
    val ibBaseUrl: String = "https://localhost:5000/v1/api",
    val ibAccountId: String = "",
    val ibSessionToken: String = "",
    val xtbWsUrl: String = "wss://ws.xtb.com/real",
    val xtbLogin: String = "",
    val xtbPassword: String = "",
    val krakenApiKey: String = "",
    val krakenApiSecret: String = "",
    /** BEARER = public API token; API_KEY = partner keys (x-api-key + x-user-key). */
    val etoroAuthMode: String = ETORO_AUTH_BEARER,
    val etoroBearerToken: String = "",
    val etoroBearerDemo: Boolean = false,
    val etoroApiBaseUrl: String = "https://public-api.etoro.com",
    val etoroApiKey: String = "",
    val etoroUserKey: String = "",
    val etoroAccountId: String = ""
) {
    companion object {
        const val ETORO_AUTH_BEARER = "BEARER"
        const val ETORO_AUTH_API_KEY = "API_KEY"
    }
}
