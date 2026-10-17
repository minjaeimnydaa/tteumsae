package com.tteumsae.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tteumsae.app.R

// The original vivid red is retained in tone, but deepened enough that small
// labels remain readable on both white and the tinted red surface.
val TteumRed = Color(0xFFD80E31)
val TteumRedSoft = TteumRed.copy(alpha = 0.10f)
val TteumInk = Color(0xFF171719)
val TteumMuted = Color(0xFF6B7079)
val TteumSurface = Color(0xFFF5F6F8)
val TteumBackground = TteumSurface
val TteumOutline = Color(0xFFDDE0E5)
val TteumHandle = Color(0xFF8A8F98)

private val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private fun typeStyle(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = Pretendard,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
    )

private val TteumsaeTypography = Typography(
    displayLarge = typeStyle(32, 40, FontWeight.Bold),
    displayMedium = typeStyle(30, 38, FontWeight.Bold),
    displaySmall = typeStyle(28, 36, FontWeight.Bold),
    headlineLarge = typeStyle(28, 36, FontWeight.Bold),
    headlineMedium = typeStyle(26, 34, FontWeight.Bold),
    headlineSmall = typeStyle(24, 32, FontWeight.Bold),
    titleLarge = typeStyle(20, 28, FontWeight.SemiBold),
    titleMedium = typeStyle(18, 26, FontWeight.SemiBold),
    titleSmall = typeStyle(16, 24, FontWeight.SemiBold),
    bodyLarge = typeStyle(16, 24),
    bodyMedium = typeStyle(14, 22),
    bodySmall = typeStyle(13, 20),
    labelLarge = typeStyle(16, 24, FontWeight.SemiBold),
    labelMedium = typeStyle(14, 20, FontWeight.Medium),
    labelSmall = typeStyle(13, 18, FontWeight.Medium),
)

private val TteumsaeColors = lightColorScheme(
    primary = TteumRed,
    onPrimary = Color.White,
    secondary = TteumInk,
    background = TteumBackground,
    surface = Color.White,
    onSurface = TteumInk,
    surfaceVariant = TteumSurface,
    onSurfaceVariant = TteumMuted,
    outline = TteumOutline,
)

@Composable
fun TteumsaeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TteumsaeColors,
        typography = TteumsaeTypography,
        shapes = TteumShapes.material,
        content = content,
    )
}
