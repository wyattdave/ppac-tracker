package com.releaseplanner.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.releaseplanner.tracker.data.ReleaseThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    secondary = Color(0xFF0F766E),
    tertiary = Color(0xFF9333EA),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93C5FD),
    secondary = Color(0xFF5EEAD4),
    tertiary = Color(0xFFD8B4FE),
)

@Composable
fun ReleasePlannerTheme(themeMode: ReleaseThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when (themeMode) {
            ReleaseThemeMode.Light -> LightColors
            ReleaseThemeMode.Dark -> DarkColors
        },
        typography = MaterialTheme.typography,
        content = content,
    )
}
