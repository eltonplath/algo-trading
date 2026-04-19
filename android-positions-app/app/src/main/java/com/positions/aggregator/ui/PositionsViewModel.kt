package com.positions.aggregator.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.positions.aggregator.data.PositionRepository
import com.positions.aggregator.data.SecureConfigStore
import com.positions.aggregator.data.brokers.EtoroConnector
import com.positions.aggregator.data.brokers.InteractiveBrokersConnector
import com.positions.aggregator.data.brokers.KrakenConnector
import com.positions.aggregator.data.brokers.XtbConnector
import com.positions.aggregator.data.model.AppConfig
import com.positions.aggregator.data.model.BrokerCredentials
import com.positions.aggregator.data.model.BrokerFetchResult
import com.positions.aggregator.data.net.HttpClientFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class PositionsUiState(
    val credentials: BrokerCredentials = BrokerCredentials(),
    val brokerResults: List<BrokerFetchResult> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

class PositionsViewModel(
    application: Application,
    private val repository: PositionRepository,
    private val configStore: SecureConfigStore
) : AndroidViewModel(application) {

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
            _uiState.value = _uiState.value.copy(message = "Configuration saved securely on this device.")
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
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

class PositionsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PositionsViewModel::class.java)) {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val client = HttpClientFactory.createDefault()
            val repository = PositionRepository(
                listOf(
                    EtoroConnector(client, json),
                    InteractiveBrokersConnector(client, json),
                    XtbConnector(client, json),
                    KrakenConnector(client, json)
                )
            )
            return PositionsViewModel(application, repository, SecureConfigStore(application)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
