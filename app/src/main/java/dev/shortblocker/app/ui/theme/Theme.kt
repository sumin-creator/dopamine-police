package dev.shortblocker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A00),
    onPrimary = Color(0xFF2A1600),
    secondary = Color(0xFFFFB54D),
    background = Color(0xFF17120C),
    surface = Color(0xFF231A12),
    surfaceVariant = Color(0xFF32251A),
    onBackground = Color(0xFFFFF6EE),
    onSurface = Color(0xFFFFF6EE),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFF57C00),
    onPrimary = Color.White,
    secondary = Color(0xFFFFA726),
    background = Color(0xFFFFF8F0),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFFFE9D2),
    onBackground = Color(0xFF2E2014),
    onSurface = Color(0xFF2E2014),
)

@Composable
fun ShortblockerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
