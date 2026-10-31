package com.frostlock.remote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.frostlock.remote.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF147DAB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF2FA),
    onPrimaryContainer = Color(0xFF073B4D),
    secondary = Color(0xFF147DAB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF2FA),
    onSecondaryContainer = Color(0xFF073B4D),
    tertiary = Color(0xFF2F7C55),
    onTertiary = Color.White,
    background = Color(0xFFF3F6F8),
    onBackground = Color(0xFF101E2E),
    surface = Color.White,
    onSurface = Color(0xFF101E2E),
    surfaceVariant = Color(0xFFEDF1F4),
    onSurfaceVariant = Color(0xFF607181),
    outline = Color(0xFFC9D5DE),
    outlineVariant = Color(0xFFDCE4EA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CB8DE),
    onPrimary = Color(0xFF032733),
    primaryContainer = Color(0xFF12384A),
    onPrimaryContainer = Color(0xFFCDEAF7),
    secondary = Color(0xFF5CB8DE),
    onSecondary = Color(0xFF032733),
    secondaryContainer = Color(0xFF17313F),
    onSecondaryContainer = Color(0xFFCDEAF7),
    tertiary = Color(0xFF7BD3A8),
    onTertiary = Color(0xFF06271A),
    background = Color(0xFF0B141B),
    onBackground = Color(0xFFE4EDF4),
    surface = Color(0xFF131F29),
    onSurface = Color(0xFFE4EDF4),
    surfaceVariant = Color(0xFF1B2A36),
    onSurfaceVariant = Color(0xFF9FB0BE),
    outline = Color(0xFF3A4C5B),
    outlineVariant = Color(0xFF25333F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun FrostTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
