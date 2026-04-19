package com.positions.aggregator.data.model

data class BrokerFetchResult(
    val broker: Broker,
    val positions: List<Position>,
    val error: String? = null
)
