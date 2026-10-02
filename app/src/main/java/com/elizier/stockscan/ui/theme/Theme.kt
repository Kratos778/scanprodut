package com.elizier.stockscan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF0F766E)
private val Orange = Color(0xFFC2410C)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = Orange,
    tertiary = Green
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    secondary = Color(0xFFFB923C),
    tertiary = Color(0xFF2DD4BF)
)

@Composable
fun StockScanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
