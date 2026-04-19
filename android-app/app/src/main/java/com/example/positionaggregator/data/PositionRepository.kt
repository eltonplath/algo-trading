package com.example.positionaggregator.data

import com.example.positionaggregator.data.brokers.BrokerConnector
import com.example.positionaggregator.data.model.BrokerFetchResult
import com.example.positionaggregator.data.model.BrokerCredentials
import com.example.positionaggregator.data.model.Position
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class PositionRepository(
    private val connectors: List<BrokerConnector>
) {
    suspend fun fetchAll(credentials: BrokerCredentials): List<BrokerFetchResult> = coroutineScope {
        connectors.map { connector ->
            async {
                val result = connector.fetchPositions(credentials)
                result.fold(
                    onSuccess = {
                        BrokerFetchResult(
                            broker = connector.broker,
                            positions = it
                        )
                    },
                    onFailure = {
                        BrokerFetchResult(
                            broker = connector.broker,
                            positions = emptyList(),
                            error = it.message ?: "Failed to fetch positions."
                        )
                    }
                )
            }
        }.awaitAll()
    }
}
