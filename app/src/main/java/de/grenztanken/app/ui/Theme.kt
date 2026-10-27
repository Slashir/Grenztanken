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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.grenztanken.app.R

/*
 * Eigenes, festes Farbschema (keine dynamischen Systemfarben). Jede Farbe hat genau eine Rolle:
 * Petrol = Marke, Text, Auswahl; Bernstein (secondary) = die eine Hauptaktion eines Bildschirms;
 * Grün/Rot = ausschließlich Ergebnis-Aussagen; alles andere neutral grau.
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
    secondaryContainer = Color(0xFFE2E8E8),
    onSecondaryContainer = Color(0xFF161D1E),
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
    secondaryContainer = Color(0xFF2A3335),
    onSecondaryContainer = Color(0xFFDDE4E4),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFDDE4E4),
    surface = Color(0xFF0F1415),
    onSurface = Color(0xFFDDE4E4),
    surfaceVariant = Color(0xFF2A3335),
    onSurfaceVariant = Color(0xFFA3B0B2),
    surfaceContainerLowest = Color(0xFF0A0F10),
    surfaceContainerLow = Color(0xFF151C1D),
    surfaceContainer = Color(0xFF1C2527),
    surfaceContainerHigh = Color(0xFF242E30),
    surfaceContainerHighest = Color(0xFF2C3739),
    outline = Color(0xFF4D5A5C),
    outlineVariant = Color(0xFF2C3638),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF3B1512),
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
    /**
     * Hintergrund der Ergebniskarten: nur leicht getönt. Text darauf bleibt neutral (onSurface),
     * farbig sind nur Urteil und Betrag – Grün auf Grün ist besonders im Dunkeln schlecht lesbar.
     */
    val goodCard: Color,
    val badCard: Color,
)

private val LightResult = ResultColors(
    good = Color(0xFF1B7A3E),
    goodContainer = Color(0xFFDDF3E3),
    onGoodContainer = Color(0xFF0B3D1D),
    bad = Color(0xFFB3261E),
    badContainer = Color(0xFFFCE4E1),
    onBadContainer = Color(0xFF5C0E09),
    goodCard = Color(0xFFE6F4EA),
    badCard = Color(0xFFFBEAE7),
)

private val DarkResult = ResultColors(
    good = Color(0xFF7DDB98),
    goodContainer = Color(0xFF123A21),
    onGoodContainer = Color(0xFFBFF0CC),
    bad = Color(0xFFFF9C92),
    badContainer = Color(0xFF4A1612),
    onBadContainer = Color(0xFFFFDAD6),
    goodCard = Color(0xFF18271E),
    badCard = Color(0xFF2B1B1A),
)

val LocalResultColors = staticCompositionLocalOf { LightResult }

/** Hervorgehobene Ergebnis-Fläche (ADAC-Ergebnis): eine pro Bildschirm. */
@Immutable
data class HeroColors(val container: Color, val content: Color, val muted: Color)

private val LightHero = HeroColors(Color(0xFF0E5A60), Color.White, Color(0xFFD3E8E9))
private val DarkHero = HeroColors(Color(0xFF145E64), Color(0xFFE6F4F4), Color(0xFFC2DDDF))

val LocalHeroColors = staticCompositionLocalOf { LightHero }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/*
 * Schrift: Inter (SIL Open Font License, Lizenz unter assets/licenses/Inter-OFL.txt) als variable Schrift.
 * Große Größen nutzen die Display-Optik (opsz 32) mit engerer Laufweite.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int, opticalSize: Float) = Font(
    R.font.inter,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

private val InterText = FontFamily(listOf(400, 500, 600, 700).map { inter(it, 14f) })
private val InterDisplay = FontFamily(listOf(400, 500, 600, 700, 800).map { inter(it, 32f) })

private val Base = Typography()

private fun TextStyle.display(weight: FontWeight, tracking: Double) =
    copy(fontFamily = InterDisplay, fontWeight = weight, letterSpacing = tracking.sp)

private fun TextStyle.text(weight: FontWeight? = null) =
    copy(fontFamily = InterText, fontWeight = weight ?: fontWeight)

private val AppTypography = Typography(
    displayLarge = Base.displayLarge.display(FontWeight.Bold, -1.5),
    displayMedium = Base.displayMedium.display(FontWeight.Bold, -1.0),
    displaySmall = Base.displaySmall.display(FontWeight.Bold, -0.8),
    headlineLarge = Base.headlineLarge.display(FontWeight.Bold, -0.8),
    headlineMedium = Base.headlineMedium.display(FontWeight.Bold, -0.6),
    headlineSmall = Base.headlineSmall.display(FontWeight.Bold, -0.4),
    titleLarge = Base.titleLarge.display(FontWeight.SemiBold, -0.3),
    titleMedium = Base.titleMedium.text(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.text(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.text(),
    bodyMedium = Base.bodyMedium.text(),
    bodySmall = Base.bodySmall.text(),
    labelLarge = Base.labelLarge.text(FontWeight.SemiBold),
    labelMedium = Base.labelMedium.text(FontWeight.SemiBold).copy(letterSpacing = 0.6.sp),
    labelSmall = Base.labelSmall.text(FontWeight.Medium),
)

/** Zahlen mit gleich breiten Ziffern (Preise springen beim Aktualisieren nicht). */
val Tabular: TextStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun GrenztankenTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalResultColors provides if (dark) DarkResult else LightResult,
        LocalHeroColors provides if (dark) DarkHero else LightHero,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
