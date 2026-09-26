package app.kin.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import app.kin.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The Kin look: a white drafting-paper canvas, ink type, graphite product panels, and one aqua signal
 * colour reserved for live actions. Amounts and addresses use a monospaced face.
 */
object KinColors {
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF18181B)
    val Graphite = Color(0xFF27272A)
    val Cloud = Color(0xFFF4F4F5)
    val Steel = Color(0xFFD4D4D8)
    val Slate = Color(0xFF71717A)
    val Charcoal = Color(0xFF3F3F46)

    /** Fill for the main call to action and for "live" markers. Never used as a text colour on white. */
    val Aqua = Color(0xFF94FAF0)
    val Lime = Color(0xFFD4F796)
    val Volt = Color(0xFFBFF660)

    /** Readable status colours for text on white and cloud surfaces. */
    val Good = Color(0xFF0B7A6F)
    val Warn = Color(0xFFB45309)
    val Bad = Color(0xFFB91C1C)

    val Sweep = Brush.horizontalGradient(listOf(Color(0xFF07CDDF), Color(0xFF9EED15)))
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight): Font =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Inter for text and JetBrains Mono for amounts, addresses and technical labels. Both are bundled and open licence. */
val KinMono: FontFamily = FontFamily(
    variable(R.font.jetbrains_mono, FontWeight.Normal),
    variable(R.font.jetbrains_mono, FontWeight.Medium),
)

private val Inter: FontFamily = FontFamily(
    variable(R.font.inter, FontWeight.Normal),
    variable(R.font.inter, FontWeight.Medium),
    variable(R.font.inter, FontWeight.SemiBold),
)

private val Scheme: ColorScheme = lightColorScheme(
    primary = KinColors.Ink,
    onPrimary = Color.White,
    secondary = KinColors.Aqua,
    onSecondary = KinColors.Ink,
    background = KinColors.Paper,
    onBackground = KinColors.Ink,
    surface = KinColors.Cloud,
    onSurface = KinColors.Ink,
    surfaceVariant = KinColors.Cloud,
    onSurfaceVariant = KinColors.Slate,
    outline = KinColors.Steel,
    outlineVariant = KinColors.Steel,
    error = KinColors.Bad,
)

private val Sans = Inter

private val KinTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 46.sp, lineHeight = 44.sp, letterSpacing = (-2.2).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1.4).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-1.2).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 29.sp, letterSpacing = (-0.9).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 16.sp, letterSpacing = (-0.1).sp),
    labelMedium = TextStyle(fontFamily = KinMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = KinMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.3.sp),
)

@Composable
fun KinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = KinTypography, content = content)
}
