package com.positions.aggregator.data.model

data class Position(
    val broker: Broker,
    val symbol: String,
    val quantity: Double,
    val direction: PositionDirection,
    val avgOpenPrice: Double?,
    val markPrice: Double?,
    val unrealizedPnl: Double?,
    val currency: String?,
    val accountId: String?
)

enum class PositionDirection {
    LONG,
    SHORT
}
