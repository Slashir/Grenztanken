@file:OptIn(ExperimentalMaterial3Api::class)

package de.grenztanken.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.grenztanken.app.logic.Calc
import de.grenztanken.app.logic.ForeignPrice
import de.grenztanken.app.logic.PriceResolver
import de.grenztanken.app.logic.PriceSource
import de.grenztanken.app.logic.TomorrowHint
import de.grenztanken.app.logic.Calculation
import de.grenztanken.app.model.DePrice
import de.grenztanken.app.model.ForeignStation
import de.grenztanken.app.model.Fuel
import de.grenztanken.app.model.GermanStation
import de.grenztanken.app.model.Market
import de.grenztanken.app.model.RateInfo
import de.grenztanken.app.model.Settings
import java.time.LocalDate
import java.time.ZoneId

private val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

@Composable
fun HomeScreen(
    settings: Settings,
    market: Market,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveForeign: (ForeignStation) -> Unit,
    onDeleteForeign: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<ForeignStation?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grenztanken") },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !market.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Tankstelle") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { creating = true },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (market.loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (market.errors.isNotEmpty()) {
                item { ErrorCard(market.errors) }
            }
            if (settings.apiKey.isBlank() || settings.german == null) {
                item { SetupCard(onOpenSettings) }
            }
            settings.german?.let { german ->
                item {
                    GermanCard(german, settings.fuel, market.de?.takeIf { it.stationId == german.id })
                }
            }
            item {
                Text(
                    "${settings.fuel.label} · ${Fmt.dec(settings.consumption, 1)} l/100 km · " +
                        "${Fmt.dec(settings.liters, 0)} l tanken",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (settings.foreign.isEmpty()) {
                item { HintCard() }
            }
            items(settings.foreign, key = { it.id }) { station ->
                ForeignCard(station, settings, market, onClick = { editing = station })
            }
            item { SourcesFooter() }
        }
    }

    if (creating || editing != null) {
        ForeignDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = {
                onSaveForeign(it)
                creating = false
                editing = null
            },
            onDelete = {
                onDeleteForeign(it)
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun GermanCard(station: GermanStation, fuel: Fuel, de: DePrice?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "🇩🇪 Deine Tankstelle",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(station.name, style = MaterialTheme.typography.titleMedium)
            Text(
                station.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val price = de?.priceFor(fuel)
            Text(
                price?.let { Fmt.eurPerL(it) } ?: "–",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                buildString {
                    append(fuel.label)
                    if (de != null) {
                        append(" · ")
                        append(Fmt.status(de.status))
                        append(" · Stand ")
                        append(Fmt.time(de.fetchedAt))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ForeignCard(station: ForeignStation, s: Settings, m: Market, onClick: () -> Unit) {
    val today = LocalDate.now(BERLIN)
    val price = PriceResolver.resolve(station, s.fuel, m, today)
    val tomorrow = if (price?.source == PriceSource.CAP_TODAY) {
        PriceResolver.tomorrow(station, s.fuel, m, today)
    } else {
        null
    }
    val de = m.de?.takeIf { it.stationId == s.german?.id }?.priceFor(s.fuel)

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${station.country.flag} ${station.name}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Bearbeiten",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${Fmt.km(station.oneWayKm)} einfache Strecke · ${station.country.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                price == null -> Notice(
                    "Noch kein Preis für ${station.country.label}: kein gültiger Höchstpreis und noch kein " +
                        "EU-Durchschnitt geladen. Bitte oben aktualisieren oder die Karte antippen und den " +
                        "Preis an der Säule eintragen.",
                )

                price.perEur == null -> Notice("Wechselkurs fehlt noch. Bitte oben aktualisieren.")

                de == null -> Notice(
                    "Deutscher Preis fehlt. Prüfe in den Einstellungen Schlüssel und Tankstelle.",
                )

                else -> ResultBlock(
                    calc = Calc.compute(de, price.price, price.perEur, s.liters, s.consumption, station.oneWayKm),
                    price = price,
                    station = station,
                    s = s,
                    rate = m.rate(station.country),
                    tomorrow = tomorrow,
                )
            }
        }
    }
}

private fun sourceLabel(p: ForeignPrice): String = when (p.source) {
    PriceSource.MANUAL -> "Preis von dir eingetragen"
    PriceSource.CAP_TODAY -> "Amtlicher Höchstpreis, gültig ${p.period?.let { Fmt.period(it) }.orEmpty()}"
    PriceSource.CAP_TOMORROW ->
        "Amtlicher Höchstpreis ab morgen (${p.period?.let { Fmt.period(it) }.orEmpty()}); " +
            "der heutige Wert wurde nicht abgerufen"
    PriceSource.EU_WEEKLY ->
        "Richtwert: EU-Landesdurchschnitt, Woche vom ${p.euWeek?.let { Fmt.date(it) }.orEmpty()}. " +
            "Grenztankstellen können abweichen."
}

@Composable
private fun ResultBlock(
    calc: Calculation,
    price: ForeignPrice,
    station: ForeignStation,
    s: Settings,
    rate: RateInfo?,
    tomorrow: TomorrowHint?,
) {
    val good = MaterialTheme.colorScheme.primary
    val bad = MaterialTheme.colorScheme.error
    val isEu = price.source == PriceSource.EU_WEEKLY

    Spacer(Modifier.height(4.dp))
    Text(
        if (isEu) {
            "Ø ${Fmt.eurPerL(price.price)}"
        } else {
            "${Fmt.local(price.price, station.country)} = ${Fmt.eurPerL(calc.foreignEurPerL)}"
        },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        sourceLabel(price),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!isEu && rate != null) {
        Text(
            "Kurs: 1 € = ${Fmt.dec(rate.perEur, 3)} ${station.country.currencySymbol} (${rate.date})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    tomorrow?.let { TomorrowLine(it, station) }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    ValueRow("Preisunterschied", Fmt.ctPerL(calc.diffPerL))
    ValueRow("Ersparnis bei ${Fmt.dec(s.liters, 0)} l", Fmt.eur(calc.grossSaving))
    ValueRow("Fahrt hin und zurück (${Fmt.km(2 * station.oneWayKm)})", "− " + Fmt.eur(calc.tripCost))
    ValueRow(
        "Unterm Strich",
        Fmt.eurSigned(calc.netSaving),
        bold = true,
        color = if (calc.worthIt) good else bad,
    )
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (calc.worthIt) good.copy(alpha = 0.12f) else bad.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (calc.worthIt) "Lohnt sich" else "Lohnt sich nicht",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (calc.worthIt) good else bad,
            )
            Text(
                calc.breakEvenOneWayKm?.let {
                    "Rechnet sich bis ca. ${Fmt.km(it)} einfache Strecke."
                } ?: "Im Ausland ist es gerade nicht günstiger.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TomorrowLine(t: TomorrowHint, station: ForeignStation) {
    val text = when {
        t.diff < -0.005 -> "Morgen günstiger: ${Fmt.local(t.price, station.country)} " +
            "(${Fmt.localDiff(t.diff, station.country)}). Wenn es nicht eilt, lohnt sich Warten."
        t.diff > 0.005 -> "Morgen teurer: ${Fmt.local(t.price, station.country)} " +
            "(${Fmt.localDiff(t.diff, station.country)}). Besser heute tanken."
        else -> "Morgen gilt derselbe Höchstpreis."
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ValueRow(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ErrorCard(errors: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Nicht alles konnte geladen werden",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            errors.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Einrichten", style = MaterialTheme.typography.titleMedium)
            Text(
                "Trage deinen kostenlosen Tankerkönig-Schlüssel ein und wähle deine deutsche Tankstelle.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenSettings) { Text("Zu den Einstellungen") }
        }
    }
}

@Composable
private fun HintCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Füge über „Tankstelle“ eine Tankstelle in Tschechien oder Polen hinzu, " +
                "mit der Strecke dorthin.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun SourcesFooter() {
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            "Preise Deutschland: Tankerkönig, Daten der Markttransparenzstelle für Kraftstoffe (MTS-K), " +
                "Lizenz CC BY 4.0. Höchstpreise: Finanzministerium Tschechien, Energieministerium Polen. " +
                "Kurse: ČNB, NBP. Richtwerte ohne Höchstpreis: EU Weekly Oil Bulletin über fuel-prices.eu, " +
                "CC BY 4.0. Höchstpreise sind Obergrenzen, an der Säule kann es günstiger sein. " +
                "Fahrtkosten nur Sprit, ohne Verschleiß. Alle Angaben ohne Gewähr.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { uri.openUri("https://creativecommons.tankerkoenig.de") }) {
            Text("tankerkoenig.de")
        }
    }
}
