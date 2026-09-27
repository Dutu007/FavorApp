package com.dutu007.favorapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FavorColors = lightColorScheme(
    primary = Color(0xFFE45192),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCEB),
    onPrimaryContainer = Color(0xFF74183F),
    secondary = Color(0xFF805A9B),
    onSecondary = Color.White,
    background = Color(0xFFFFF8FC),
    surface = Color(0xFFFFF8FC),
    surfaceVariant = Color(0xFFF7E8F3),
    onSurfaceVariant = Color(0xFF6F5A6D),
    outline = Color(0xFFE5C5D8),
)

@Composable
fun FavorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FavorColors,
        content = content,
    )
}
