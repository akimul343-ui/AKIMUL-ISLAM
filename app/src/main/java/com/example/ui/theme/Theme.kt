package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepObsidian,
    primaryContainer = CyanMuted,
    onPrimaryContainer = TextPrimaryDark,
    secondary = EmeraldShield,
    onSecondary = DeepObsidian,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = WarningAmber,
    onTertiary = DeepObsidian,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = CrimsonContainerDark,
    onErrorContainer = Color(0xFFFECACA),
    background = DeepObsidian,
    onBackground = TextPrimaryDark,
    surface = MidnightNavy,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = MidnightNavy,
    surfaceContainer = SlateSurface,
    surfaceContainerHigh = SlateElevated,
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = DeepTealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF083344),
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = CrimsonLight,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = CoolMist,
    onBackground = TextPrimaryLight,
    surface = PureCard,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftSlateLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = PureCard,
    surfaceContainer = SoftSlateLight,
    surfaceContainerHigh = Color(0xFFDDE5F0),
    outline = OutlineLight
)

@Composable
fun SentinelGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
