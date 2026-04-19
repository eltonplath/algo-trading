package com.example.positionaggregator.data.brokers

import com.example.positionaggregator.data.model.Broker
import com.example.positionaggregator.data.model.BrokerCredentials
import com.example.positionaggregator.data.model.Position

interface BrokerConnector {
    val broker: Broker
    suspend fun fetchPositions(credentials: BrokerCredentials): Result<List<Position>>
}
