@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.grenztanken.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.grenztanken.app.OsmSearchState
import de.grenztanken.app.logic.Calc
import de.grenztanken.app.logic.Calculation
import de.grenztanken.app.logic.ForeignPrice
import de.grenztanken.app.logic.PriceResolver
import de.grenztanken.app.logic.PriceSource
import de.grenztanken.app.logic.Stats
import de.grenztanken.app.logic.TomorrowHint
import de.grenztanken.app.model.DePrice
import de.grenztanken.app.model.ForeignStation
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
    osmSearch: OsmSearchState,
    onOsmAroundGerman: () -> Unit,
    onOsmAroundPlace: (String) -> Unit,
    onOsmClear: () -> Unit,
    routeKm: suspend (Double, Double) -> Result<Double>,
) {
    var editing by remember { mutableStateOf<ForeignStation?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Grenztanken") },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !market.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Tankstelle") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = { creating = true },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = market.loading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (market.errors.isNotEmpty()) {
                item { ErrorCard(market.errors) }
            }
            if (settings.apiKey.isBlank() || settings.german == null) {
                item { SetupCard(onOpenSettings) }
            }
            settings.german?.let { german ->
                item { GermanCard(german, settings, market) }
            }
            item { SettingsChips(settings, onOpenSettings) }
            if (settings.foreign.isEmpty()) {
                item { HintCard() }
            } else {
                item { SectionLabel("Im Ausland tanken", Modifier.padding(start = 4.dp, top = 8.dp)) }
            }
            items(settings.foreign, key = { it.id }) { station ->
                ForeignCard(station, settings, market, onClick = { editing = station })
            }
            item { SourcesFooter() }
        }
        }
    }

    if (creating || editing != null) {
        ForeignDialog(
            initial = editing,
            hasGerman = settings.german != null,
            osmSearch = osmSearch,
            onOsmAroundGerman = onOsmAroundGerman,
            onOsmAroundPlace = onOsmAroundPlace,
            onOsmClear = onOsmClear,
            routeKm = routeKm,
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

// ---------------------------------------------------------------- Deutsche Tankstelle

@Composable
private fun GermanCard(station: GermanStation, s: Settings, m: Market) {
    val de: DePrice? = m.de?.takeIf { it.stationId == station.id }
    val price = de?.priceFor(s.fuel)
    val r = LocalResultColors.current

    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("🇩🇪  Deine Tankstelle", color = mutedColor(), modifier = Modifier.weight(1f))
            de?.let {
                val open = it.status == "open"
                Tag(
                    Fmt.status(it.status),
                    if (open) r.goodContainer else LocalContentColor.current.copy(alpha = 0.16f),
                    if (open) r.onGoodContainer else LocalContentColor.current,
                )
            }
        }
        Text(station.name, style = MaterialTheme.typography.titleMedium)
        Text(station.address, style = MaterialTheme.typography.bodySmall, color = mutedColor())
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (price != null) {
                PumpPrice(price, MaterialTheme.typography.displayMedium)
            } else {
                Text("–", style = MaterialTheme.typography.displayMedium)
            }
            Spacer(Modifier.weight(1f))
            Tag(
                s.fuel.label,
                LocalContentColor.current.copy(alpha = 0.16f),
                LocalContentColor.current,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        de?.let {
            Text(
                if (it.isStale) "Letzter Preis vom ${Fmt.time(it.pricedAt)} · abgerufen ${Fmt.time(it.fetchedAt)}"
                else "Stand ${Fmt.time(it.fetchedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor(),
            )
        }
        if (price != null) {
            ComparisonBlock(price, station, s, m)
        }
    }
}

@Composable
private fun ComparisonBlock(price: Double, station: GermanStation, s: Settings, m: Market) {
    val area = m.area?.takeIf { it.stationId == station.id }?.let { Stats.area(it, s.fuel) }
    val history = if (m.historyStationId == station.id) m.history else emptyList()
    val now = System.currentTimeMillis()
    val dayStart = LocalDate.now(BERLIN).atStartOfDay(BERLIN).toInstant().toEpochMilli()
    val today = Stats.timeWeighted(history, s.fuel, dayStart, now + 1)
    val week = Stats.timeWeighted(history, s.fuel, now - 7L * 24 * 3_600_000L, now + 1)
    if (area == null && today == null && week == null) return

    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = LocalContentColor.current.copy(alpha = 0.2f))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        area?.let {
            CompareRow(
                title = "Umgebung jetzt",
                detail = "Ø von ${it.count} geöffneten im Umkreis ${Fmt.km(it.radiusKm)}",
                average = it.average,
                own = price,
            )
            Text(
                "Günstigste: ${it.cheapest.name} ${Fmt.eurPerL(it.cheapestPrice)}" +
                    (it.cheapest.distKm?.let { d -> " · ${Fmt.dec(d, 1)} km" } ?: ""),
                style = MaterialTheme.typography.bodySmall.merge(Tabular),
                color = mutedColor(),
            )
        }
        today?.let {
            CompareRow(
                title = "Ø heute",
                detail = "aus ${it.observations} ${if (it.observations == 1) "Abruf" else "Abrufen"} · " +
                    "${Fmt.eurPerL(it.min)} – ${Fmt.eurPerL(it.max)}",
                average = it.average,
                own = price,
            )
        }
        week?.let {
            if (it.observations > (today?.observations ?: 0)) {
                CompareRow(
                    title = "Ø 7 Tage",
                    detail = "aus ${it.observations} Abrufen · ${Fmt.eurPerL(it.min)} – ${Fmt.eurPerL(it.max)}",
                    average = it.average,
                    own = price,
                )
            }
        }
        if (today != null || week != null) {
            Text(
                "Ø heute / 7 Tage stammen aus deinen eigenen Abrufen und sind umso genauer, je öfter du die App öffnest.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor(),
            )
        }
    }
}

@Composable
private fun CompareRow(title: String, detail: String, average: Double, own: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(8.dp))
                Text(Fmt.eurPerL(average), style = MaterialTheme.typography.bodyMedium.merge(Tabular))
            }
            Text(detail, style = MaterialTheme.typography.bodySmall.merge(Tabular), color = mutedColor())
        }
        DeltaTag(own, average)
    }
}

@Composable
private fun SettingsChips(s: Settings, onOpenSettings: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        val c = MaterialTheme.colorScheme.secondaryContainer
        val on = MaterialTheme.colorScheme.onSecondaryContainer
        listOf(
            s.fuel.label,
            "${Fmt.dec(s.consumption, 1)} l/100 km",
            "${Fmt.dec(s.liters, 0)} l tanken",
            if (s.wearCentPerKm > 0.0) "Verschleiß ${Fmt.dec(s.wearCentPerKm, 1)} ct/km" else "ohne Verschleiß",
        ).forEach { Tag(it, c, on) }
        // Ohne onClick-Parameter, sonst erzwingt Material 48 dp Mindesthöhe und die Zeile verrutscht.
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onOpenSettings),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                "Ändern",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Ausland

@Composable
private fun ForeignCard(station: ForeignStation, s: Settings, m: Market, onClick: () -> Unit) {
    val today = LocalDate.now(BERLIN)
    val price = PriceResolver.resolve(station, s.fuel, m, today)
    val tomorrow = if (price?.source == PriceSource.CAP_TODAY) PriceResolver.tomorrow(station, s.fuel, m, today) else null
    val dePrice = m.de?.takeIf { it.stationId == s.german?.id }
    val de = dePrice?.priceFor(s.fuel)

    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${station.country.flag}  ${station.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Text(
            listOf(station.address, "${Fmt.km(station.oneWayKm)} einfach", station.country.label)
                .filter { it.isNotBlank() }.joinToString(" · "),
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

            de == null && dePrice?.status == "closed" -> Notice(
                "Deine deutsche Tankstelle hat gerade geschlossen, und Tankerkönig liefert dann keinen " +
                    "Preis. Sobald sie geöffnet hat, oben aktualisieren – danach bleibt der letzte Preis " +
                    "auch nachts stehen.",
            )

            de == null -> Notice("Deutscher Preis fehlt. Prüfe in den Einstellungen Schlüssel und Tankstelle.")

            else -> ResultBlock(
                calc = Calc.compute(
                    de, price.price, price.perEur, s.liters, s.consumption, station.oneWayKm,
                    wearEurPerKm = s.wearCentPerKm / 100.0,
                ),
                price = price,
                station = station,
                s = s,
                rate = m.rate(station.country),
                tomorrow = tomorrow,
                staleDeSince = dePrice?.takeIf { it.isStale }?.pricedAt,
            )
        }
        if (station.lat != null && station.lng != null) {
            val uri = LocalUriHandler.current
            FilledTonalButton(
                onClick = { uri.openUri(mapsRouteUrl(s.german, station.lat, station.lng)) },
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Route in Google Maps")
            }
        }
    }
}

/** Google-Maps-Routen-Link (ohne API); ohne Koordinaten der deutschen Tankstelle ab aktuellem Standort. */
private fun mapsRouteUrl(german: GermanStation?, lat: Double, lng: Double): String {
    val origin = if (german?.lat != null && german.lng != null) "&origin=${german.lat},${german.lng}" else ""
    return "https://www.google.com/maps/dir/?api=1$origin&destination=$lat,$lng&travelmode=driving"
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
    staleDeSince: Long?,
) {
    val r = LocalResultColors.current
    val isEu = price.source == PriceSource.EU_WEEKLY

    // Ergebnis zuerst
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (calc.worthIt) r.goodContainer else r.badContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (calc.worthIt) "Lohnt sich" else "Lohnt sich nicht",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (calc.worthIt) r.onGoodContainer else r.onBadContainer,
                )
                Text(
                    calc.breakEvenOneWayKm?.let { "bis ca. ${Fmt.km(it)} einfache Strecke" }
                        ?: "Im Ausland gerade nicht günstiger",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (calc.worthIt) r.onGoodContainer else r.onBadContainer,
                )
            }
            Text(
                Fmt.eurSigned(calc.netSaving),
                style = MaterialTheme.typography.headlineSmall.merge(Tabular),
                fontWeight = FontWeight.Bold,
                color = if (calc.worthIt) r.good else r.bad,
            )
        }
    }

    // Preis im Ausland
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
        PumpPrice(calc.foreignEurPerL, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        if (!isEu) {
            Text(
                "  = ${Fmt.local(price.price, station.country)}",
                style = MaterialTheme.typography.bodyMedium.merge(Tabular),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        } else {
            Text("  Ø Land", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
    Text(sourceLabel(price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!isEu && rate != null) {
        Text(
            "Kurs: 1 € = ${Fmt.dec(rate.perEur, 3)} ${station.country.currencySymbol} (${rate.date})",
            style = MaterialTheme.typography.bodySmall.merge(Tabular),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    tomorrow?.let { TomorrowLine(it, station) }
    staleDeSince?.let {
        Text(
            "Verglichen mit dem letzten deutschen Preis vom ${Fmt.time(it)} (Tankstelle gerade geschlossen).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Rechnung
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ValueRow("Preisunterschied", Fmt.ctPerL(calc.diffPerL), labelColor = muted)
    ValueRow("Ersparnis bei ${Fmt.dec(s.liters, 0)} l", Fmt.eur(calc.grossSaving), labelColor = muted)
    ValueRow("Sprit hin und zurück (${Fmt.km(2 * station.oneWayKm)})", Fmt.eur(-calc.tripFuelCost), labelColor = muted)
    if (calc.wearPerKm > 0.0) {
        ValueRow(
            "Verschleiß (${Fmt.km(2 * station.oneWayKm)} × ${Fmt.dec(calc.wearPerKm * 100.0, 1)} ct)",
            Fmt.eur(-calc.tripWearCost),
            labelColor = muted,
        )
    }
    ValueRow(
        "Unterm Strich",
        Fmt.eurSigned(calc.netSaving),
        bold = true,
        color = if (calc.worthIt) r.good else r.bad,
    )
}

@Composable
private fun TomorrowLine(t: TomorrowHint, station: ForeignStation) {
    val r = LocalResultColors.current
    val (text, container, content) = when {
        t.diff < -0.005 -> Triple(
            "Morgen günstiger: ${Fmt.local(t.price, station.country)} (${Fmt.localDiff(t.diff, station.country)}) – wenn es nicht eilt, warten.",
            r.goodContainer, r.onGoodContainer,
        )
        t.diff > 0.005 -> Triple(
            "Morgen teurer: ${Fmt.local(t.price, station.country)} (${Fmt.localDiff(t.diff, station.country)}) – besser heute tanken.",
            r.badContainer, r.onBadContainer,
        )
        else -> Triple(
            "Morgen gilt derselbe Höchstpreis.",
            MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface,
        )
    }
    Surface(shape = MaterialTheme.shapes.small, color = container, modifier = Modifier.padding(top = 6.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = content, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

// ---------------------------------------------------------------- Sonstiges

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
    androidx.compose.material3.Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Nicht alles konnte geladen werden", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            errors.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    AppCard {
        Text("Einrichten", style = MaterialTheme.typography.titleMedium)
        Text(
            "Trage deinen kostenlosen Tankerkönig-Schlüssel ein und wähle deine deutsche Tankstelle.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 4.dp)) { Text("Zu den Einstellungen") }
    }
}

@Composable
private fun HintCard() {
    AppCard {
        Text("Noch keine Tankstelle im Ausland", style = MaterialTheme.typography.titleSmall)
        Text(
            "Tippe unten auf „Tankstelle“ und suche in OpenStreetMap nach einer Tankstelle in Tschechien " +
                "oder Polen – die Strecke rechnet die App selbst aus.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourcesFooter() {
    val uri = LocalUriHandler.current
    Column(Modifier.padding(start = 4.dp, top = 12.dp, end = 4.dp)) {
        Text(
            "Preise Deutschland: Tankerkönig, Daten der Markttransparenzstelle für Kraftstoffe (MTS-K), " +
                "Lizenz CC BY 4.0. Höchstpreise: Finanzministerium Tschechien, Energieministerium Polen. " +
                "Kurse: ČNB, NBP. Richtwerte ohne Höchstpreis: EU Weekly Oil Bulletin über fuel-prices.eu, " +
                "CC BY 4.0. Tankstellensuche: © OpenStreetMap-Mitwirkende (ODbL) über Nominatim, " +
                "Strecken: OSRM. Höchstpreise sind Obergrenzen, an der Säule kann es günstiger sein. " +
                "Fahrtkosten: Sprit zum Auslandspreis plus dein Verschleißwert aus den Einstellungen, " +
                "ohne Zeit. Alle Angaben ohne Gewähr.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = { uri.openUri("https://creativecommons.tankerkoenig.de") }) { Text("tankerkoenig.de") }
            TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/copyright") }) { Text("OpenStreetMap") }
        }
    }
}
