package app.kin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object KinColors {
    val Green = Color(0xFF10B981)
    val GreenDark = Color(0xFF0F7B5F)
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
    val Ink = Color(0xFF0B0F0E)
    val Surface = Color(0xFF141A18)
    val SurfaceHigh = Color(0xFF1C2421)
    val Muted = Color(0xFF8CA29A)
}

private val Dark = darkColorScheme(
    primary = KinColors.Green,
    onPrimary = Color.Black,
    background = KinColors.Ink,
    onBackground = Color(0xFFEAF2EF),
    surface = KinColors.Surface,
    onSurface = Color(0xFFEAF2EF),
    surfaceVariant = KinColors.SurfaceHigh,
    onSurfaceVariant = KinColors.Muted,
    error = KinColors.Red,
)

private val Light = lightColorScheme(
    primary = KinColors.GreenDark,
    onPrimary = Color.White,
    background = Color(0xFFF5F8F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFE7EFEC),
    onSurfaceVariant = Color(0xFF52665F),
)

private val KinTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun KinTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = KinTypography, content = content)
}
