package dev.mowin.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0B0F14)
private val Surface = Color(0xFF131A22)
private val Accent = Color(0xFF4CD6A7)
private val Warn = Color(0xFFF2B84B)
private val Danger = Color(0xFFEF6A6A)

private val MoWinColors = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    secondary = Warn,
    background = Ink,
    onBackground = Color(0xFFE6EDF3),
    surface = Surface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C2530),
    onSurfaceVariant = Color(0xFF9FB0C0),
    error = Danger,
    onError = Ink,
)

@Composable
fun MoWinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MoWinColors, content = content)
}
