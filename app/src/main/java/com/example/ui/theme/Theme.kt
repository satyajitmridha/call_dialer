package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    secondary = CyberCyan,
    tertiary = SecurityGold,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color(0xFF003311),
    onSecondary = Color(0xFF002533),
    onBackground = Color(0xFFEDF2F7),
    onSurface = Color(0xFFEDF2F7),
    onSurfaceVariant = Color(0xFFA0AEC0)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    secondary = CyberCyan,
    tertiary = SecurityGold,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A202C),
    onSurface = Color(0xFF1A202C),
    onSurfaceVariant = Color(0xFF4A5568)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to gorgeous secure dark mode
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
