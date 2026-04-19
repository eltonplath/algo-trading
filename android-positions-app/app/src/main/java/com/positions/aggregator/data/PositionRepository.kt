package com.positions.aggregator.data

import com.positions.aggregator.data.brokers.BrokerConnector
import com.positions.aggregator.data.model.BrokerCredentials
import com.positions.aggregator.data.model.BrokerFetchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class PositionRepository(
    private val connectors: List<BrokerConnector>
) {
    suspend fun fetchAll(credentials: BrokerCredentials): List<BrokerFetchResult> = coroutineScope {
        connectors.map { connector ->
            async {
                connector.fetchPositions(credentials).fold(
                    onSuccess = { BrokerFetchResult(broker = connector.broker, positions = it) },
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
