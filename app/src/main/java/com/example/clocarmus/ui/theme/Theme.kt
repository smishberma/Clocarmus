package com.example.clocarmus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PlayerColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xff171717),
    onSurface = Color.White
)

@Composable
fun ClocarmusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PlayerColors, typography = Typography, content = content)
}
