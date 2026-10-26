package de.grenztanken.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
