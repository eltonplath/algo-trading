package com.example.positionaggregator.data.model

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
    val etoroApiBaseUrl: String = "https://api-portal.etoro.com",
    val etoroApiKey: String = "",
    val etoroUserKey: String = "",
    val etoroAccountId: String = ""
)
