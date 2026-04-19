package com.positions.aggregator.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val credentials: BrokerCredentials = BrokerCredentials()
)
