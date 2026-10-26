package de.grenztanken.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Eigenes, festes Farbschema (keine dynamischen Systemfarben): Petrol als Hauptfarbe,
 * Bernstein als Akzent (Zapfsäulen-Anzeige), Grün/Rot ausschließlich für Ergebnis-Aussagen.
 */

private val Petrol = Color(0xFF0E5A60)
private val PetrolLight = Color(0xFF7FCFD3)
private val Amber = Color(0xFFE59B1E)
private val AmberLight = Color(0xFFF5C26B)

private val LightColors = lightColorScheme(
    primary = Petrol,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBEC),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Amber,
    onSecondary = Color(0xFF2A1800),
    secondaryContainer = Color(0xFFFFE3B8),
    onSecondaryContainer = Color(0xFF2A1800),
    background = Color(0xFFF3F5F5),
    onBackground = Color(0xFF161D1E),
    surface = Color(0xFFF3F5F5),
    onSurface = Color(0xFF161D1E),
    surfaceVariant = Color(0xFFE2E8E8),
    onSurfaceVariant = Color(0xFF59656A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF1F1),
    surfaceContainerHighest = Color(0xFFE5EAEA),
    outline = Color(0xFFB8C4C5),
    outlineVariant = Color(0xFFDCE3E3),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE4E1),
    onErrorContainer = Color(0xFF5C0E09),
)

private val DarkColors = darkColorScheme(
    primary = PetrolLight,
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF0B4A4F),
    onPrimaryContainer = Color(0xFFCDEBEC),
    secondary = AmberLight,
    onSecondary = Color(0xFF3A2500),
    secondaryContainer = Color(0xFF5A3C00),
    onSecondaryContainer = Color(0xFFFFE3B8),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFDDE4E4),
    surface = Color(0xFF0F1415),
    onSurface = Color(0xFFDDE4E4),
    surfaceVariant = Color(0xFF2A3335),
    onSurfaceVariant = Color(0xFFA3B0B2),
    surfaceContainerLowest = Color(0xFF0A0F10),
    surfaceContainerLow = Color(0xFF151C1D),
    surfaceContainer = Color(0xFF192123),
    surfaceContainerHigh = Color(0xFF212A2C),
    surfaceContainerHighest = Color(0xFF2A3436),
    outline = Color(0xFF4D5A5C),
    outlineVariant = Color(0xFF2C3638),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Ergebnisfarben, getrennt vom Material-Schema, damit "gut/schlecht" überall gleich aussieht. */
@Immutable
data class ResultColors(
    val good: Color,
    val goodContainer: Color,
    val onGoodContainer: Color,
    val bad: Color,
    val badContainer: Color,
    val onBadContainer: Color,
)

private val LightResult = ResultColors(
    good = Color(0xFF1B7A3E),
    goodContainer = Color(0xFFDDF3E3),
    onGoodContainer = Color(0xFF0B3D1D),
    bad = Color(0xFFB3261E),
    badContainer = Color(0xFFFCE4E1),
    onBadContainer = Color(0xFF5C0E09),
)

private val DarkResult = ResultColors(
    good = Color(0xFF7DDB98),
    goodContainer = Color(0xFF123A21),
    onGoodContainer = Color(0xFFBFF0CC),
    bad = Color(0xFFFF9C92),
    badContainer = Color(0xFF4A1612),
    onBadContainer = Color(0xFFFFDAD6),
)

val LocalResultColors = staticCompositionLocalOf { LightResult }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Base = Typography()

private val AppTypography = Base.copy(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

/** Zahlen mit gleich breiten Ziffern (Preise springen beim Aktualisieren nicht). */
val Tabular: TextStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun GrenztankenTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalResultColors provides if (dark) DarkResult else LightResult) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
