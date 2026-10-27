package de.grenztanken.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import de.grenztanken.app.model.Country
import java.time.Year
import java.util.Locale
import kotlin.math.abs

/** Urheber, wie er in der App steht. */
const val COPYRIGHT_HOLDER = "Slashir"

fun copyrightLine(): String = "© ${Year.now().value} $COPYRIGHT_HOLDER"

/** Freiwillige Unterstützung des Entwicklers. */
const val KOFI_URL = "https://ko-fi.com/slashir"

/** "© 2026 Slashir" mit dezentem Ko-fi-Link daneben. */
@Composable
fun CopyrightRow(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            copyrightLine(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { uri.openUri(KOFI_URL) }) { Text("Auf Ko-fi unterstützen") }
    }
}

/** Karte im App-Stil: flach, große Rundung. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** Die eine hervorgehobene Ergebnis-Fläche eines Bildschirms; Text darin nimmt die helle Inhaltsfarbe. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val h = LocalHeroColors.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(h.container),
    ) {
        CompositionLocalProvider(LocalContentColor provides h.content) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

/** Zweitrangige Textfarbe passend zur Umgebung (normale, farbige oder Hero-Karte). */
@Composable
fun mutedColor(): Color = LocalContentColor.current.copy(alpha = 0.74f)

/** Rundes Symbol mit neutralem Hintergrund vor Abschnittsüberschriften. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** Länderkürzel als kleine Plakette ("CZ", "PL") statt Flaggen-Emojis, die je nach Gerät anders aussehen. */
@Composable
fun CountryBadge(country: Country) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = LocalContentColor.current.copy(alpha = 0.1f),
        contentColor = LocalContentColor.current,
    ) {
        Text(
            country.code,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Zeile einer gruppierten Liste: Einträge stehen mit kleinem Abstand untereinander,
 * nur der erste und letzte haben große Rundungen.
 */
@Composable
fun GroupedRow(
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val big = 20.dp
    val small = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    }
}

/** Textknopf "Rechnung anzeigen ▾" / "… ausblenden ▴" in der Farbe der Umgebung. */
@Composable
fun ExpandButton(
    expanded: Boolean,
    closedLabel: String,
    openLabel: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    TextButton(
        onClick = onToggle,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(if (expanded) openLabel else closedLabel, style = MaterialTheme.typography.labelLarge)
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Gestrichelter Rahmen, z. B. für "Hinzufügen"-Flächen. */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
}

/** Zusätzliche Symbole im Stil der Material-Icons (Pfade aus Material Icons, Apache 2.0). */
object AppIcons {
    val Car: ImageVector by lazy {
        icon(
            "M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 " +
                "1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 " +
                "13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 " +
                "1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z",
        )
    }

    val GasStation: ImageVector by lazy {
        icon(
            "M19.77 7.23l.01-.01-3.72-3.72L15 4.56l2.11 2.11c-.94.36-1.61 1.26-1.61 2.33 0 1.38 1.12 2.5 2.5 2.5.36 " +
                "0 .69-.08 1-.21v7.21c0 .55-.45 1-1 1s-1-.45-1-1V14c0-1.1-.9-2-2-2h-1V5c0-1.1-.9-2-2-2H6c-1.1 0-2 .9-2 " +
                "2v16h10v-7.5h1.5v5c0 1.38 1.12 2.5 2.5 2.5s2.5-1.12 2.5-2.5V9c0-.69-.28-1.32-.73-1.77zM12 10H6V5h6v5zm6 " +
                "0c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z",
        )
    }

    val OpenInNew: ImageVector by lazy {
        icon(
            "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 " +
                "9.83 1.41 1.41L19 6.41V10h2V3h-7z",
        )
    }

    private fun icon(path: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}

/** Kleine Überschrift in Großbuchstaben. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(Locale.GERMANY), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** Etikett mit farbigem Hintergrund. */
@Composable
fun Tag(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = container, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.merge(Tabular),
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** "1,72⁹ €" für Fließtext: die dritte Nachkommastelle klein und hochgestellt, wie an der Zapfsäule. */
fun pumpText(eurPerL: Double): AnnotatedString {
    val s = String.format(Locale.GERMANY, "%.3f", eurPerL)
    return buildAnnotatedString {
        append(s.dropLast(1))
        withStyle(SpanStyle(fontSize = 0.72.em, baselineShift = BaselineShift(0.4f))) { append(s.last()) }
        append(" €")
    }
}

/** Euro-Preis wie an der Zapfsäule als eigene Zeile. */
@Composable
fun PumpPrice(eurPerL: Double, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val s = String.format(Locale.GERMANY, "%.3f", eurPerL)
    val text = buildAnnotatedString {
        append(s.dropLast(1))
        withStyle(SpanStyle(fontSize = style.fontSize * 0.55f, baselineShift = BaselineShift(0.5f))) {
            append(s.last())
        }
        withStyle(SpanStyle(fontSize = style.fontSize * 0.6f, fontWeight = FontWeight.Medium)) {
            append(" €")
        }
    }
    Text(text, style = style.merge(Tabular), color = color, modifier = modifier)
}

/** Vergleich eigener Preis gegen einen Durchschnitt: grün darunter, rot darüber. */
@Composable
fun DeltaTag(own: Double, reference: Double, short: Boolean = false) {
    val r = LocalResultColors.current
    val ct = (own - reference) * 100.0
    when {
        abs(ct) < 0.05 -> Tag("= Ø", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
        ct < 0 -> Tag(
            if (short) Fmt.ctDelta(ct) else String.format(Locale.GERMANY, "%.1f ct unter Ø", -ct),
            r.goodContainer,
            r.onGoodContainer,
        )
        else -> Tag(
            if (short) Fmt.ctDelta(ct) else String.format(Locale.GERMANY, "%.1f ct über Ø", ct),
            r.badContainer,
            r.onBadContainer,
        )
    }
}

/** Zeile "Bezeichnung ...... Wert". */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    labelColor: Color = Color.Unspecified,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.merge(Tabular),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

/** Datenquellen, Lizenzen und Urheber an einer Stelle. */
@Composable
fun SourcesDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = { Text("Quellen & Lizenzen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "Preise Deutschland" to "Tankerkönig, Daten der Markttransparenzstelle für Kraftstoffe (MTS-K), CC BY 4.0",
                    "Höchstpreise" to "Finanzministerium Tschechien, Energieministerium Polen",
                    "Wechselkurse" to "Tschechische Nationalbank (ČNB), Polnische Nationalbank (NBP)",
                    "Richtwerte ohne Höchstpreis" to "EU Weekly Oil Bulletin über fuel-prices.eu, CC BY 4.0",
                    "Tankstellensuche" to "© OpenStreetMap-Mitwirkende (ODbL) über Nominatim; Strecken: OSRM",
                    "Autokosten" to "ADAC Autokatalog",
                    "Schrift" to "Inter, SIL Open Font License 1.1",
                ).forEach { (title, text) ->
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Höchstpreise sind Obergrenzen, an der Säule kann es günstiger sein. Alle Angaben ohne Gewähr.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { uri.openUri("https://creativecommons.tankerkoenig.de") }) { Text("Tankerkönig") }
                    TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/copyright") }) { Text("OpenStreetMap") }
                }
                CopyrightRow()
            }
        },
    )
}
