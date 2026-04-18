package com.example.positionaggregator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.positionaggregator.data.ConfigStore
import com.example.positionaggregator.ui.PositionsApp
import com.example.positionaggregator.ui.theme.PositionAggregatorTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PositionsViewModel by viewModels {
        PositionsViewModelFactory(
            repository = defaultRepository(),
            configStore = ConfigStore(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PositionAggregatorTheme {
                PositionsApp(viewModel = viewModel)
            }
        }
    }
}
