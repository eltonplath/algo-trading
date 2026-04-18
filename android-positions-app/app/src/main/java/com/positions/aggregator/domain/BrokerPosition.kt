package com.positions.aggregator.domain

enum class Broker {
    KRAKEN,
    INTERACTIVE_BROKERS,
    XTB,
    ETORO
}

data class BrokerPosition(
    val broker: Broker,
    val symbol: String,
    val side: String,
    val quantity: String,
    val detail: String? = null
)

data class BrokerFetchResult(
    val broker: Broker,
    val positions: List<BrokerPosition> = emptyList(),
    val error: String? = null
)
