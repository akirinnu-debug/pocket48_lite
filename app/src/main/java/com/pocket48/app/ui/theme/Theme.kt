package com.pocket48.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Pocket48 品牌色 (与 Web 版一致 #F43F5E)
private val Pocket48Primary = Color(0xFFF43F5E)
private val Pocket48PrimaryContainer = Color(0xFFFFD9DD)
private val Pocket48Secondary = Color(0xFF7C7C80)
private val Pocket48Background = Color(0xFFFAFAFA)
private val Pocket48Surface = Color(0xFFFFFFFF)
private val Pocket48SurfaceVariant = Color(0xFFF3F3F3)

private val DarkColors = darkColorScheme(
    primary = Pocket48Primary,
    primaryContainer = Pocket48PrimaryContainer,
    secondary = Pocket48Secondary,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
)

private val LightColors = lightColorScheme(
    primary = Pocket48Primary,
    primaryContainer = Pocket48PrimaryContainer,
    secondary = Pocket48Secondary,
    background = Pocket48Background,
    surface = Pocket48Surface,
    surfaceVariant = Pocket48SurfaceVariant,
)

@Composable
fun Pocket48Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
