package com.tteumsae.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TteumRed = Color(0xFFE60F33)
val TteumRedSoft = TteumRed.copy(alpha = 0.10f)
val TteumInk = Color(0xFF171719)
val TteumMuted = Color(0xFF8B8F98)
val TteumSurface = Color(0xFFF5F6F8)

private val TteumsaeColors = lightColorScheme(
    primary = TteumRed,
    onPrimary = Color.White,
    secondary = TteumInk,
    background = Color(0xFFF7F7F4),
    surface = Color.White,
    onSurface = TteumInk,
    surfaceVariant = TteumSurface,
    onSurfaceVariant = TteumMuted,
    outline = Color(0xFFE1E3E8),
)

@Composable
fun TteumsaeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TteumsaeColors,
        content = content,
    )
}
