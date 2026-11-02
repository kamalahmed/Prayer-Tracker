package com.kamalahmed.prayerapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AccentGreen = Color(0xFF5BC16B)
val AccentGreenDim = Color(0xFF2E5E36)
val Gold = Color(0xFFE6A23C)
val Bg = Color(0xFF000000)
val Card = Color(0xFF1C1C1E)
val CardHigh = Color(0xFF2A2A2D)
val TextSecondary = Color(0xFF9A9A9F)
val Danger = Color(0xFFFF5A5F)

private val Colors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color.Black,
    primaryContainer = AccentGreenDim,
    onPrimaryContainer = Color.White,
    secondary = Gold,
    onSecondary = Color.Black,
    background = Bg,
    onBackground = Color.White,
    surface = Bg,
    onSurface = Color.White,
    surfaceVariant = Card,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Card,
    surfaceContainerHigh = CardHigh,
    surfaceContainerHighest = CardHigh,
    outline = Color(0xFF3A3A3C),
    error = Danger,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun PrayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
