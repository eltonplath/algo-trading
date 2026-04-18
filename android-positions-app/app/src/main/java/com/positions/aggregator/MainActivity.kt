package com.positions.aggregator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.positions.aggregator.domain.BrokerPosition
import com.positions.aggregator.ui.PositionsUiState
import com.positions.aggregator.ui.PositionsViewModel
import com.positions.aggregator.ui.label

class MainActivity : ComponentActivity() {

    private val viewModel: PositionsViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()
                var tab by remember { mutableIntStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("All positions") },
                            actions = {
                                IconButton(onClick = { viewModel.refresh() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                                IconButton(onClick = { tab = 1 }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors()
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Column {
                            TabRow(selectedTabIndex = tab) {
                                Tab(
                                    selected = tab == 0,
                                    onClick = { tab = 0 },
                                    text = { Text("Positions") }
                                )
                                Tab(
                                    selected = tab == 1,
                                    onClick = { tab = 1 },
                                    text = { Text("Broker setup") }
                                )
                            }
                            when (tab) {
                                0 -> PositionsTab(
                                    state = state,
                                    onInitialLoad = { viewModel.refresh() }
                                )

                                1 -> SettingsTab(
                                    state = state,
                                    onKrakenKey = viewModel::updateKrakenKey,
                                    onKrakenSecret = viewModel::updateKrakenSecret,
                                    onIbUrl = viewModel::updateIbBaseUrl,
                                    onXtbUser = viewModel::updateXtbUser,
                                    onXtbPassword = viewModel::updateXtbPassword,
                                    onXtbDemo = viewModel::updateXtbDemo,
                                    onEtoroToken = viewModel::updateEtoroToken,
                                    onEtoroDemo = viewModel::updateEtoroDemo,
                                    onSave = {
                                        viewModel.saveSettings()
                                        tab = 0
                                        viewModel.refresh()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionsTab(
    state: PositionsUiState,
    onInitialLoad: () -> Unit
) {
    LaunchedEffect(Unit) {
        onInitialLoad()
    }
    Column(Modifier.fillMaxSize()) {
        if (state.loading) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.padding(8.dp))
                Text("Loading brokers…")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.results, key = { it.broker.name }) { res ->
                Card(colors = CardDefaults.cardColors()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            res.broker.label(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        if (res.error != null) {
                            Text(
                                res.error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else if (res.positions.isEmpty()) {
                            Text("No open positions returned.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            res.positions.forEach { p ->
                                PositionRow(p)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionRow(p: BrokerPosition) {
    Column {
        Text(
            "${p.symbol} · ${p.side} · ${p.quantity}",
            style = MaterialTheme.typography.bodyLarge
        )
        if (!p.detail.isNullOrBlank()) {
            Text(
                p.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsTab(
    state: PositionsUiState,
    onKrakenKey: (String) -> Unit,
    onKrakenSecret: (String) -> Unit,
    onIbUrl: (String) -> Unit,
    onXtbUser: (String) -> Unit,
    onXtbPassword: (String) -> Unit,
    onXtbDemo: (Boolean) -> Unit,
    onEtoroToken: (String) -> Unit,
    onEtoroDemo: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    val s = state.settings
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Credentials stay on this device (encrypted). Each broker uses its official API; you supply keys or run IB’s Client Portal Gateway for IBKR.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            Text("Kraken", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = s.krakenKey,
                onValueChange = onKrakenKey,
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.krakenSecret,
                onValueChange = onKrakenSecret,
                label = { Text("Private key (base64)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text("Interactive Brokers", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = s.ibBaseUrl,
                onValueChange = onIbUrl,
                label = { Text("Client Portal API base URL") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Default: https://localhost:5000/v1/api with gateway + login on the same network.")
                }
            )
        }
        item {
            Text("XTB (xStation)", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = s.xtbUser,
                onValueChange = onXtbUser,
                label = { Text("User ID") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.xtbPassword,
                onValueChange = onXtbPassword,
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.xtbDemo, onCheckedChange = onXtbDemo)
                Text("Demo account")
            }
        }
        item {
            Text("eToro", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = s.etoroToken,
                onValueChange = onEtoroToken,
                label = { Text("Bearer access token") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("From eToro API portal; must match Virtual vs Real below.")
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.etoroDemo, onCheckedChange = onEtoroDemo)
                Text("Virtual (demo) portfolio")
            }
        }
        item {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and refresh")
            }
        }
    }
}
