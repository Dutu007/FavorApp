package com.dutu007.favorapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FavorColors = lightColorScheme(
    primary = Color(0xFFB33C66),
    onPrimary = Color.White,
    secondary = Color(0xFF7A5262),
    background = Color(0xFFFFF8F8),
    surface = Color(0xFFFFF8F8),
    surfaceVariant = Color(0xFFF7E5EA),
)

@Composable
fun FavorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FavorColors,
        content = content,
    )
}
