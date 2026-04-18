package com.positions.aggregator.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.positions.aggregator.data.SecureBrokerSettingsStore
import com.positions.aggregator.data.etoro.EtoroPortfolioClient
import com.positions.aggregator.data.ib.IbClientPortalPositionsClient
import com.positions.aggregator.data.kraken.KrakenOpenPositionsClient
import com.positions.aggregator.data.xtb.XtbXApiTradesClient
import com.positions.aggregator.domain.Broker
import com.positions.aggregator.domain.BrokerFetchResult
import com.positions.aggregator.domain.BrokerPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val krakenKey: String = "",
    val krakenSecret: String = "",
    val ibBaseUrl: String = SecureBrokerSettingsStore.DEFAULT_IB_BASE,
    val xtbUser: String = "",
    val xtbPassword: String = "",
    val xtbDemo: Boolean = false,
    val etoroToken: String = "",
    val etoroDemo: Boolean = false
)

data class PositionsUiState(
    val loading: Boolean = false,
    val results: List<BrokerFetchResult> = emptyList(),
    val allPositions: List<BrokerPosition> = emptyList(),
    val settings: SettingsUiState = SettingsUiState(),
    val settingsDirty: Boolean = false
)

class PositionsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SecureBrokerSettingsStore(application)
    private val krakenClient = KrakenOpenPositionsClient()
    private val ibClient = IbClientPortalPositionsClient()
    private val xtbClient = XtbXApiTradesClient()
    private val etoroClient = EtoroPortfolioClient()

    private val _state = MutableStateFlow(
        PositionsUiState(
            settings = SettingsUiState(
                ibBaseUrl = store.getIbBaseUrl(),
                krakenKey = store.getKrakenKey(),
                krakenSecret = store.getKrakenSecret(),
                xtbUser = store.getXtbUserId(),
                xtbPassword = store.getXtbPassword(),
                xtbDemo = store.isXtbDemo(),
                etoroToken = store.getEtoroBearerToken(),
                etoroDemo = store.isEtoroDemo()
            )
        )
    )
    val state: StateFlow<PositionsUiState> = _state.asStateFlow()

    fun updateKrakenKey(v: String) = updateSettings { copy(krakenKey = v) }
    fun updateKrakenSecret(v: String) = updateSettings { copy(krakenSecret = v) }
    fun updateIbBaseUrl(v: String) = updateSettings { copy(ibBaseUrl = v) }
    fun updateXtbUser(v: String) = updateSettings { copy(xtbUser = v) }
    fun updateXtbPassword(v: String) = updateSettings { copy(xtbPassword = v) }
    fun updateXtbDemo(v: Boolean) = updateSettings { copy(xtbDemo = v) }
    fun updateEtoroToken(v: String) = updateSettings { copy(etoroToken = v) }
    fun updateEtoroDemo(v: Boolean) = updateSettings { copy(etoroDemo = v) }

    fun saveSettings() {
        val s = _state.value.settings
        store.setKraken(s.krakenKey, s.krakenSecret)
        store.setIbBaseUrl(s.ibBaseUrl.ifBlank { SecureBrokerSettingsStore.DEFAULT_IB_BASE })
        store.setXtb(s.xtbUser, s.xtbPassword, s.xtbDemo)
        store.setEtoroBearerToken(s.etoroToken, s.etoroDemo)
        _state.update { it.copy(settingsDirty = false) }
    }

    fun refresh() {
        if (_state.value.settingsDirty) {
            saveSettings()
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val results = withContext(Dispatchers.IO) {
                listOf(
                    async { krakenClient.fetch(store.getKrakenKey(), store.getKrakenSecret()) },
                    async { ibClient.fetch(store.getIbBaseUrl()) },
                    async {
                        xtbClient.fetch(
                            store.getXtbUserId(),
                            store.getXtbPassword(),
                            store.isXtbDemo()
                        )
                    },
                    async {
                        etoroClient.fetch(
                            store.getEtoroBearerToken(),
                            store.isEtoroDemo()
                        )
                    }
                ).awaitAll()
            }
            val flat = results.flatMap { it.positions }
            _state.update {
                it.copy(
                    loading = false,
                    results = results,
                    allPositions = flat
                )
            }
        }
    }

    private inline fun updateSettings(transform: SettingsUiState.() -> SettingsUiState) {
        _state.update { st ->
            st.copy(
                settings = st.settings.transform(),
                settingsDirty = true
            )
        }
    }
}

fun Broker.label(): String = when (this) {
    Broker.KRAKEN -> "Kraken"
    Broker.INTERACTIVE_BROKERS -> "Interactive Brokers"
    Broker.XTB -> "XTB"
    Broker.ETORO -> "eToro"
}
