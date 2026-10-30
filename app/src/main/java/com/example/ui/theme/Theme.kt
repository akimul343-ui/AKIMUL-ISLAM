package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberColorScheme = darkColorScheme(
    primary = CyberPink,
    onPrimary = Color.White,
    primaryContainer = CyberSurfaceVariant,
    onPrimaryContainer = CyberNeonPink,
    secondary = CyberPurple,
    onSecondary = Color.White,
    background = CyberDarkBg,
    onBackground = CyberOnSurface,
    surface = CyberSurface,
    onSurface = CyberOnSurface,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextDim,
    error = CyberDanger,
    onError = Color.White
)

@Composable
fun MahiWifeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = Typography,
        content = content
    )
}
