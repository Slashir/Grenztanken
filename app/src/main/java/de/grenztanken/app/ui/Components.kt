package de.grenztanken.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

/** Karte im App-Stil: flach, große Rundung, heller Hintergrund. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, content = inner)
    }
}

/** Hervorgehobene Karte mit Farbverlauf; Text darin nimmt automatisch die helle Inhaltsfarbe. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val h = LocalHeroColors.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(h.start, h.end))),
    ) {
        CompositionLocalProvider(LocalContentColor provides h.content) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

/** Zweitrangige Textfarbe passend zur Umgebung (Karte oder Hero-Karte). */
@Composable
fun mutedColor(): Color = LocalContentColor.current.copy(alpha = 0.72f)

/** Rundes Symbol mit getöntem Hintergrund, z. B. vor Abschnittsüberschriften. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
    }
}

/** Runder Anfangsbuchstabe, z. B. für Automarken. */
@Composable
fun Monogram(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.take(1).uppercase(Locale.GERMANY),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
    val big = 22.dp
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
        Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { content() }
    }
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

/**
 * Euro-Preis wie an der Zapfsäule: "1,73⁹ €" – die dritte Nachkommastelle klein und hochgestellt.
 */
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
fun DeltaTag(own: Double, reference: Double) {
    val r = LocalResultColors.current
    val ct = (own - reference) * 100.0
    when {
        abs(ct) < 0.05 -> Tag("= Ø", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
        ct < 0 -> Tag(String.format(Locale.GERMANY, "%.1f ct unter Ø", -ct), r.goodContainer, r.onGoodContainer)
        else -> Tag(String.format(Locale.GERMANY, "%.1f ct über Ø", ct), r.badContainer, r.onBadContainer)
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
