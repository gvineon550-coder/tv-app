package com.tv.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7AB8FF),
    onPrimary = Color(0xFF00172E),
    primaryContainer = Color(0xFF00456F),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF1A1A1D),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFC0C0C0),
    outline = Color(0xFF3A3A3E),
    error = Color(0xFFFF6B6B)
)

@Composable
fun TVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
