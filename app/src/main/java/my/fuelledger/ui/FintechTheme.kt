package my.fuelledger.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun LedgerTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) LedgerDarkColors else LedgerLightColors,
        typography = LedgerTypography, shapes = LedgerShapes) {
        PremiumInteractions(content)
    }
}

val LedgerDarkColors = darkColorScheme(
    primary = Color(0xFF2ABB91), onPrimary = Color(0xFF062D25),
    primaryContainer = Color(0xFF123C36), onPrimaryContainer = Color(0xFFB6F7E1),
    secondary = Color(0xFF87C9E8), onSecondary = Color(0xFF062C3B),
    secondaryContainer = Color(0xFF1B354A), onSecondaryContainer = Color(0xFFD0ECFA),
    tertiary = Color(0xFFC5B7FF), onTertiary = Color(0xFF2A2050),
    background = Color(0xFF0B1220), onBackground = Color(0xFFF3F6FA),
    surface = Color(0xFF0B1220), onSurface = Color(0xFFF3F6FA),
    surfaceVariant = Color(0xFF223249), onSurfaceVariant = Color(0xFFADBED0),
    surfaceContainerLowest = Color(0xFF080F1C), surfaceContainerLow = Color(0xFF131F32),
    surfaceContainer = Color(0xFF18263B), surfaceContainerHigh = Color(0xFF203047),
    surfaceContainerHighest = Color(0xFF293C54),
    outline = Color(0xFF6C7D95), outlineVariant = Color(0xFF35495B),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF55262C), onErrorContainer = Color(0xFFFFDAD6)
)

val LedgerLightColors = lightColorScheme(
    primary = Color(0xFF006C53), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F6E9), onPrimaryContainer = Color(0xFF004B3A),
    secondary = Color(0xFF285F7A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEEF8), onSecondaryContainer = Color(0xFF12384C),
    tertiary = Color(0xFF675698), onTertiary = Color.White,
    background = Color(0xFFF3F6FA), onBackground = Color(0xFF0B1220),
    surface = Color(0xFFF3F6FA), onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE5EBF2), onSurfaceVariant = Color(0xFF53647A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFEDF2F7),
    surfaceContainerHighest = Color(0xFFE3EAF2),
    outline = Color(0xFF6C7D90), outlineVariant = Color(0xFFD9E2EC),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val ledgerFont = FontFamily.SansSerif
val LedgerTypography = Typography(
    displaySmall = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-1.2).sp),
    headlineLarge = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-.9).sp),
    headlineMedium = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-.6).sp),
    headlineSmall = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-.4).sp),
    titleLarge = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-.3).sp),
    titleMedium = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = .2.sp),
    labelSmall = TextStyle(fontFamily = ledgerFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = .25.sp)
)

val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
