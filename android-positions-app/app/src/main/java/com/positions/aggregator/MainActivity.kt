package com.positions.aggregator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.positions.aggregator.ui.PositionsApp
import com.positions.aggregator.ui.PositionsViewModel
import com.positions.aggregator.ui.PositionsViewModelFactory
import com.positions.aggregator.ui.theme.PositionsAggregatorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PositionsViewModel by viewModels {
        PositionsViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PositionsAggregatorTheme {
                PositionsApp(viewModel = viewModel)
            }
        }
    }
}
