package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YouTubeDarkColorScheme = darkColorScheme(
    primary = YouTubeRed,
    onPrimary = Color.White,
    primaryContainer = DarkRed,
    onPrimaryContainer = Color.White,
    secondary = CrimsonAccent,
    onSecondary = Color.White,
    tertiary = GoldStar,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceHighlight
)

private val YouTubeLightColorScheme = lightColorScheme(
    primary = YouTubeRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = DarkRed,
    onSecondary = Color.White,
    tertiary = GoldStar,
    onTertiary = Color.Black,
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF0F0F0F),
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    outline = Color(0xFFCCCCCC)
)

@Composable
fun YouTubePlayerTheme(
    darkTheme: Boolean = true, // Default to sleek cinema dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) YouTubeDarkColorScheme else YouTubeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
