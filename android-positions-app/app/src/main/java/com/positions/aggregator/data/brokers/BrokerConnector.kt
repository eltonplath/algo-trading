package com.positions.aggregator.data.brokers

import com.positions.aggregator.data.model.Broker
import com.positions.aggregator.data.model.BrokerCredentials
import com.positions.aggregator.data.model.Position

interface BrokerConnector {
    val broker: Broker
    suspend fun fetchPositions(credentials: BrokerCredentials): Result<List<Position>>
}
