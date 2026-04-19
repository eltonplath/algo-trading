package com.example.positionaggregator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = Teal80,
    tertiary = Green80
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = Teal40,
    tertiary = Green40
)

@Composable
fun PositionAggregatorTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
