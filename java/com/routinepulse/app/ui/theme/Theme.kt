package com.routinepulse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---- Dark Mode Palette: Black, Cobalt Blue & Acid Lime ----
val BlackBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF0D0F17)
val DarkSurfaceVariant = Color(0xFF181C2B)
val CobaltBlue = Color(0xFF1E40AF)
val CobaltBlueLight = Color(0xFF3B82F6)
val AcidLime = Color(0xFFCCFF00)
val DarkText = Color(0xFFF4F4F5)
val DarkTextMuted = Color(0xFFA1A1AA)

// ---- Light Mode Palette: White, Pink & Baby Blue ----
val PureWhite = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F9FF)
val VibrantPink = Color(0xFFDB2777)
val SoftPink = Color(0xFFFCE7F3)
val BabyBlue = Color(0xFF0284C7)
val BabyBlueLight = Color(0xFF38BDF8)
val SoftBabyBlue = Color(0xFFE0F2FE)
val LightText = Color(0xFF0F172A)
val LightTextMuted = Color(0xFF475569)

// Priority indicator colors
val PriorityLow = Color(0xFF64748B)
val PriorityMedium = Color(0xFFF59E0B)
val PriorityHigh = Color(0xFFEF4444)

private val LightColors = lightColorScheme(
    primary = VibrantPink,
    onPrimary = Color.White,
    primaryContainer = SoftPink,
    onPrimaryContainer = Color(0xFF831843),
    secondary = BabyBlue,
    onSecondary = Color.White,
    secondaryContainer = SoftBabyBlue,
    onSecondaryContainer = Color(0xFF0369A1),
    background = PureWhite,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextMuted,
    outline = Color(0xFFBAE6FD),
    outlineVariant = Color(0xFFE0F2FE),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

private val DarkColors = darkColorScheme(
    primary = AcidLime,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF283600),
    onPrimaryContainer = AcidLime,
    secondary = CobaltBlueLight,
    onSecondary = Color.White,
    secondaryContainer = CobaltBlue,
    onSecondaryContainer = AcidLime,
    background = BlackBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextMuted,
    outline = Color(0xFF27272A),
    outlineVariant = Color(0xFF3F3F46),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun RoutinePulseTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
