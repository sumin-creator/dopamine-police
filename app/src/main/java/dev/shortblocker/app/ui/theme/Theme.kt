package dev.shortblocker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF855D),
    onPrimary = Color(0xFF23130E),
    secondary = Color(0xFF58D7C5),
    background = Color(0xFF0C1013),
    surface = Color(0xFF142029),
    surfaceVariant = Color(0xFF1B2A35),
    onBackground = Color(0xFFF0F6F7),
    onSurface = Color(0xFFF0F6F7),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFD75A34),
    onPrimary = Color.White,
    secondary = Color(0xFF157B6E),
    background = Color(0xFFF7F4EF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9E3DB),
    onBackground = Color(0xFF101417),
    onSurface = Color(0xFF101417),
)

@Composable
fun ShortblockerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
