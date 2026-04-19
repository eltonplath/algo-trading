package com.example.positionaggregator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.positionaggregator.data.ConfigStore
import com.example.positionaggregator.data.PositionRepository
import com.example.positionaggregator.data.brokers.EtoroConnector
import com.example.positionaggregator.data.brokers.InteractiveBrokersConnector
import com.example.positionaggregator.data.brokers.KrakenConnector
import com.example.positionaggregator.data.brokers.XtbConnector
import com.example.positionaggregator.data.model.AppConfig
import com.example.positionaggregator.data.model.BrokerCredentials
import com.example.positionaggregator.data.model.BrokerFetchResult
import com.example.positionaggregator.data.net.HttpClientFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class PositionsViewModel(
    private val repository: PositionRepository,
    private val configStore: ConfigStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(PositionsUiState())
    val uiState: StateFlow<PositionsUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch {
            val config = configStore.load()
            _uiState.value = _uiState.value.copy(credentials = config.credentials)
        }
    }

    fun updateCredentials(credentials: BrokerCredentials) {
        _uiState.value = _uiState.value.copy(credentials = credentials)
    }

    fun saveConfig() {
        viewModelScope.launch {
            configStore.save(AppConfig(credentials = _uiState.value.credentials))
            _uiState.value = _uiState.value.copy(message = "Configuration saved locally.")
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = null
            )
            val results = repository.fetchAll(_uiState.value.credentials)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                brokerResults = results,
                message = "Updated ${results.sumOf { it.positions.size }} positions."
            )
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

data class PositionsUiState(
    val credentials: BrokerCredentials = BrokerCredentials(),
    val brokerResults: List<BrokerFetchResult> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

class PositionsViewModelFactory(
    private val repository: PositionRepository,
    private val configStore: ConfigStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PositionsViewModel::class.java)) {
            return PositionsViewModel(repository, configStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

fun defaultRepository(): PositionRepository {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    val client = HttpClientFactory.createDefault()
    return PositionRepository(
        connectors = listOf(
            EtoroConnector(client, json),
            InteractiveBrokersConnector(client, json),
            XtbConnector(client, json),
            KrakenConnector(client, json)
        )
    )
}
