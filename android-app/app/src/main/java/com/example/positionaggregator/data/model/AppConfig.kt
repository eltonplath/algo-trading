package com.example.positionaggregator.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val credentials: BrokerCredentials = BrokerCredentials()
)
