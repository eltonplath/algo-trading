package com.example.positionaggregator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.positionaggregator.PositionsUiState
import com.example.positionaggregator.PositionsViewModel
import com.example.positionaggregator.data.model.BrokerCredentials
import com.example.positionaggregator.data.model.BrokerFetchResult
import com.example.positionaggregator.data.model.Position
import com.example.positionaggregator.data.model.PositionDirection
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun PositionsApp(viewModel: PositionsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    PositionsScreen(
        uiState = uiState,
        onCredentialsChange = viewModel::updateCredentials,
        onSave = viewModel::saveConfig,
        onRefresh = viewModel::refresh,
        snackbarHostState = snackbarHostState
    )
}

@Composable
private fun PositionsScreen(
    uiState: PositionsUiState,
    onCredentialsChange: (BrokerCredentials) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var localCredentials by remember(uiState.credentials) { mutableStateOf(uiState.credentials) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Portfolio Position Hub",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track open positions from eToro, Interactive Brokers, XTB, and Kraken.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                CredentialsCard(
                    credentials = localCredentials,
                    onCredentialsChange = {
                        localCredentials = it
                        onCredentialsChange(it)
                    },
                    onSave = onSave,
                    onRefresh = onRefresh,
                    isLoading = uiState.isLoading
                )
            }
            item {
                SummaryCard(uiState.brokerResults)
            }
            if (uiState.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            items(uiState.brokerResults, key = { it.broker.name }) { brokerResult ->
                BrokerCard(brokerResult)
            }
        }
    }
}

@Composable
private fun CredentialsCard(
    credentials: BrokerCredentials,
    onCredentialsChange: (BrokerCredentials) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Connection Settings", fontWeight = FontWeight.SemiBold)
            Divider()

            Text("eToro")
            CredentialField("API Base URL", credentials.etoroApiBaseUrl) {
                onCredentialsChange(credentials.copy(etoroApiBaseUrl = it))
            }
            CredentialField("API Key", credentials.etoroApiKey) {
                onCredentialsChange(credentials.copy(etoroApiKey = it))
            }
            CredentialField("User Key", credentials.etoroUserKey, isPassword = true) {
                onCredentialsChange(credentials.copy(etoroUserKey = it))
            }
            CredentialField("Account ID (optional)", credentials.etoroAccountId) {
                onCredentialsChange(credentials.copy(etoroAccountId = it))
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Interactive Brokers")
            CredentialField("Base URL", credentials.ibBaseUrl) {
                onCredentialsChange(credentials.copy(ibBaseUrl = it))
            }
            CredentialField("Account ID", credentials.ibAccountId) {
                onCredentialsChange(credentials.copy(ibAccountId = it))
            }
            CredentialField(
                "Session Token / Cookie (optional)",
                credentials.ibSessionToken,
                isPassword = true
            ) {
                onCredentialsChange(credentials.copy(ibSessionToken = it))
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("XTB")
            CredentialField("WebSocket URL", credentials.xtbWsUrl) {
                onCredentialsChange(credentials.copy(xtbWsUrl = it))
            }
            CredentialField("Login ID", credentials.xtbLogin, KeyboardType.Number) {
                onCredentialsChange(credentials.copy(xtbLogin = it))
            }
            CredentialField("Password", credentials.xtbPassword, isPassword = true) {
                onCredentialsChange(credentials.copy(xtbPassword = it))
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Kraken")
            CredentialField("API Key", credentials.krakenApiKey) {
                onCredentialsChange(credentials.copy(krakenApiKey = it))
            }
            CredentialField("API Secret", credentials.krakenApiSecret, isPassword = true) {
                onCredentialsChange(credentials.copy(krakenApiSecret = it))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSave, enabled = !isLoading) {
                    Text("Save")
                }
                Spacer(modifier = Modifier.padding(4.dp))
                Button(onClick = onRefresh, enabled = !isLoading) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun CredentialField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        }
    )
}

@Composable
private fun SummaryCard(results: List<BrokerFetchResult>) {
    val allPositions = results.flatMap { it.positions }
    val totalPnl = allPositions.mapNotNull { it.unrealizedPnl }.sum()
    val totalAbsoluteExposure = allPositions.sumOf { pos ->
        val mark = pos.markPrice ?: pos.avgOpenPrice ?: 0.0
        pos.quantity.absoluteValue * mark
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Summary", fontWeight = FontWeight.SemiBold)
            Text("Open positions: ${allPositions.size}")
            Text("Total unrealized PnL: ${formatNumber(totalPnl)}")
            Text("Approx. notional exposure: ${formatNumber(totalAbsoluteExposure)}")
        }
    }
}

@Composable
private fun BrokerCard(result: BrokerFetchResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(result.broker.displayName, fontWeight = FontWeight.SemiBold)
            result.error?.let {
                Text(
                    text = "Error: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (result.positions.isEmpty()) {
                Text(
                    text = if (result.error == null) "No open positions." else "No positions returned.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                result.positions.forEach { position ->
                    PositionRow(position)
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun PositionRow(position: Position) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(position.symbol, fontWeight = FontWeight.Medium)
            Text(
                "${position.direction.name.lowercase().replaceFirstChar(Char::uppercase)} ${formatNumber(position.quantity)}",
                style = MaterialTheme.typography.bodySmall
            )
            val avg = position.avgOpenPrice?.let { formatNumber(it) } ?: "-"
            val mark = position.markPrice?.let { formatNumber(it) } ?: "-"
            Text("Avg: $avg | Mark: $mark", style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            val pnlText = position.unrealizedPnl?.let { formatNumber(it) } ?: "-"
            Text("PnL: $pnlText")
            Text(position.currency ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatNumber(value: Double): String = String.format(Locale.US, "%,.2f", value)
