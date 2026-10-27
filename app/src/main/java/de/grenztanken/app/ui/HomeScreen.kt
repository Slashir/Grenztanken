@file:OptIn(ExperimentalMaterial3Api::class)

package de.grenztanken.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
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
    var showSources by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(market.loading, market.errors.size) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            // Beste Tankstelle zuerst; ohne Ergebnis ans Ende.
            val today = LocalDate.now(BERLIN)
            val entries = settings.foreign
                .map { it to evaluate(it, settings, market, today) }
                .sortedByDescending { (_, e) -> (e as? Evaluation.Ready)?.calc?.netSaving ?: Double.NEGATIVE_INFINITY }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (market.errors.isNotEmpty()) {
                    item { ErrorCard(market.errors) }
                }
                if (settings.apiKey.isBlank() || settings.german == null) {
                    item { SetupCard(onOpenSettings) }
                }
                if (entries.isEmpty()) {
                    item { HintCard() }
                }
                items(entries, key = { it.first.id }) { (station, evaluation) ->
                    ForeignCard(station, evaluation, settings, onEdit = { editing = station })
                }
                item { AddCard(onClick = { creating = true }) }
                settings.german?.let { german ->
                    item { SectionLabel("Deine Tankstelle", Modifier.padding(start = 4.dp, top = 12.dp)) }
                    item { GermanCard(german, settings, market) }
                }
                item { SettingsRow(settings, onOpenSettings) }
                item { Footer(onSources = { showSources = true }) }
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
    if (showSources) SourcesDialog(onDismiss = { showSources = false })
}

// ---------------------------------------------------------------- Ausland

/** Was sich für eine Auslandstankstelle ausrechnen lässt. */
private sealed interface Evaluation {
    data class Ready(
        val calc: Calculation,
        val price: ForeignPrice,
        val germanPrice: Double,
        val rate: RateInfo?,
        val tomorrow: TomorrowHint?,
        val staleDeSince: Long?,
    ) : Evaluation

    data class Missing(val reason: String) : Evaluation
}

private fun evaluate(station: ForeignStation, s: Settings, m: Market, today: LocalDate): Evaluation {
    val price = PriceResolver.resolve(station, s.fuel, m, today)
    val dePrice = m.de?.takeIf { it.stationId == s.german?.id }
    val de = dePrice?.priceFor(s.fuel)
    return when {
        price == null -> Evaluation.Missing(
            "Noch kein Preis für ${station.country.label}: kein gültiger Höchstpreis und noch kein EU-Durchschnitt " +
                "geladen. Zum Aktualisieren herunterziehen oder den Preis von der Säule eintragen.",
        )
        price.perEur == null -> Evaluation.Missing("Wechselkurs fehlt noch. Zum Aktualisieren herunterziehen.")
        de == null && dePrice?.status == "closed" -> Evaluation.Missing(
            "Deine deutsche Tankstelle hat gerade geschlossen, Tankerkönig liefert dann keinen Preis. Sobald sie " +
                "geöffnet hat, aktualisieren; danach bleibt der letzte Preis auch nachts stehen.",
        )
        de == null -> Evaluation.Missing("Deutscher Preis fehlt. Prüfe in den Einstellungen Schlüssel und Tankstelle.")
        else -> Evaluation.Ready(
            calc = Calc.compute(
                de, price.price, price.perEur, s.liters, s.consumption, station.oneWayKm,
                wearEurPerKm = s.wearCentPerKm / 100.0,
            ),
            price = price,
            germanPrice = de,
            rate = m.rate(station.country),
            tomorrow = if (price.source == PriceSource.CAP_TODAY) PriceResolver.tomorrow(station, s.fuel, m, today) else null,
            staleDeSince = dePrice?.takeIf { it.isStale }?.pricedAt,
        )
    }
}

@Composable
private fun ForeignCard(station: ForeignStation, evaluation: Evaluation, s: Settings, onEdit: () -> Unit) {
    val r = LocalResultColors.current
    val ready = evaluation as? Evaluation.Ready
    val good = ready?.calc?.worthIt == true
    var expanded by rememberSaveable(station.id) { mutableStateOf(false) }

    // Karte nur leicht getönt, Text neutral; farbig sind nur Urteil und Betrag.
    AppCard(
        containerColor = when {
            ready == null -> MaterialTheme.colorScheme.surfaceContainer
            good -> r.goodCard
            else -> r.badCard
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CountryBadge(station.country)
            Spacer(Modifier.size(10.dp))
            Text(station.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(20.dp))
            }
        }
        Text(
            listOf(station.address, "${Fmt.km(station.oneWayKm)} einfach").filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = mutedColor(),
        )

        when (evaluation) {
            is Evaluation.Missing -> Text(
                evaluation.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = mutedColor(),
                modifier = Modifier.padding(top = 6.dp),
            )

            is Evaluation.Ready -> {
                val c = evaluation.calc
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        if (good) "Lohnt sich" else "Lohnt sich nicht",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (good) r.good else r.bad,
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 4.dp),
                    )
                    Text(
                        Fmt.eurSigned(c.netSaving),
                        style = MaterialTheme.typography.displaySmall.merge(Tabular),
                        color = if (good) r.good else r.bad,
                    )
                }
                Text(summary(c), style = MaterialTheme.typography.bodyMedium.merge(Tabular))
                Text(
                    buildAnnotatedString {
                        append(pumpText(c.foreignEurPerL))
                        append(" dort · ")
                        append(pumpText(evaluation.germanPrice))
                        append(" hier · ")
                        append(shortSource(evaluation.price))
                    },
                    style = MaterialTheme.typography.bodyMedium.merge(Tabular),
                    color = mutedColor(),
                )
                evaluation.staleDeSince?.let {
                    Text(
                        "Deutscher Preis vom ${Fmt.time(it)}, die Tankstelle hat gerade geschlossen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor(),
                    )
                }
                evaluation.tomorrow?.let { TomorrowLine(it, station) }
                AnimatedVisibility(visible = expanded) {
                    CalcDetails(evaluation, station, s)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            if (ready != null) {
                ExpandButton(
                    expanded = expanded,
                    closedLabel = "Rechnung anzeigen",
                    openLabel = "Rechnung ausblenden",
                    onToggle = { expanded = !expanded },
                )
            }
            Spacer(Modifier.weight(1f))
            if (station.lat != null && station.lng != null) {
                val uri = LocalUriHandler.current
                val content = LocalContentColor.current
                FilledTonalButton(
                    onClick = { uri.openUri(mapsRouteUrl(s.german, station.lat, station.lng)) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = content.copy(alpha = 0.09f),
                        contentColor = content,
                    ),
                ) {
                    Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Route")
                }
            }
        }
    }
}

/** "Ersparnis 19,82 € − Fahrt 6,35 € · lohnt bis ca. 69 km einfach" */
private fun summary(c: Calculation): String {
    if (c.diffPerL <= 0.0) {
        return "Dort ${Fmt.dec(-c.diffPerL * 100.0, 1)} ct/l teurer als hier · Fahrt ${Fmt.eur(c.tripCost)}"
    }
    val base = "Ersparnis ${Fmt.eur(c.grossSaving)} − Fahrt ${Fmt.eur(c.tripCost)}"
    return c.breakEvenOneWayKm?.let { "$base · lohnt bis ca. ${Fmt.km(it)} einfach" } ?: base
}

private fun shortSource(p: ForeignPrice): String = when (p.source) {
    PriceSource.MANUAL -> "dein Preis"
    PriceSource.CAP_TODAY -> "Höchstpreis heute"
    PriceSource.CAP_TOMORROW -> "Höchstpreis ab morgen"
    PriceSource.EU_WEEKLY -> "Landesdurchschnitt"
}

private fun sourceLabel(p: ForeignPrice): String = when (p.source) {
    PriceSource.MANUAL -> "Preis von dir eingetragen."
    PriceSource.CAP_TODAY -> "Amtlicher Höchstpreis, gültig ${p.period?.let { Fmt.period(it) }.orEmpty()}."
    PriceSource.CAP_TOMORROW ->
        "Amtlicher Höchstpreis ab morgen (${p.period?.let { Fmt.period(it) }.orEmpty()}); " +
            "der heutige Wert wurde nicht abgerufen."
    PriceSource.EU_WEEKLY ->
        "Richtwert: EU-Landesdurchschnitt, Woche vom ${p.euWeek?.let { Fmt.date(it) }.orEmpty()}. " +
            "Grenztankstellen können abweichen."
}

@Composable
private fun CalcDetails(e: Evaluation.Ready, station: ForeignStation, s: Settings) {
    val c = e.calc
    val r = LocalResultColors.current
    val muted = mutedColor()
    val isEu = e.price.source == PriceSource.EU_WEEKLY
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(LocalContentColor.current.copy(alpha = 0.06f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ValueRow(
            "Preis dort",
            Fmt.eurPerL(c.foreignEurPerL) + if (isEu) "" else " = ${Fmt.local(e.price.price, station.country)}",
            labelColor = muted,
        )
        ValueRow("Preis hier", Fmt.eurPerL(e.germanPrice), labelColor = muted)
        ValueRow("Ersparnis bei ${Fmt.dec(s.liters, 0)} l", Fmt.eur(c.grossSaving), labelColor = muted)
        ValueRow("Sprit hin und zurück, ${Fmt.km(2 * station.oneWayKm)}", Fmt.eur(-c.tripFuelCost), labelColor = muted)
        if (c.wearPerKm > 0.0) {
            ValueRow(
                "Verschleiß, ${Fmt.km(2 * station.oneWayKm)} × ${Fmt.dec(c.wearPerKm * 100.0, 1)} ct",
                Fmt.eur(-c.tripWearCost),
                labelColor = muted,
            )
        }
        HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.15f))
        ValueRow("Unterm Strich", Fmt.eurSigned(c.netSaving), bold = true, color = if (c.worthIt) r.good else r.bad)
        Text(
            sourceLabel(e.price) + if (!isEu && e.rate != null) {
                " Kurs 1 € = ${Fmt.dec(e.rate.perEur, 3)} ${station.country.currencySymbol} (${e.rate.date})."
            } else {
                ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

@Composable
private fun TomorrowLine(t: TomorrowHint, station: ForeignStation) {
    val text = when {
        t.diff < -0.005 ->
            "Morgen günstiger: ${Fmt.local(t.price, station.country)} (${Fmt.localDiff(t.diff, station.country)}). Wenn es nicht eilt, warten."
        t.diff > 0.005 ->
            "Morgen teurer: ${Fmt.local(t.price, station.country)} (${Fmt.localDiff(t.diff, station.country)}). Besser heute tanken."
        else -> "Morgen gilt derselbe Höchstpreis."
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = LocalContentColor.current.copy(alpha = 0.08f),
        contentColor = LocalContentColor.current,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

/** Google-Maps-Routen-Link (ohne API); ohne Koordinaten der deutschen Tankstelle ab aktuellem Standort. */
private fun mapsRouteUrl(german: GermanStation?, lat: Double, lng: Double): String {
    val origin = if (german?.lat != null && german.lng != null) "&origin=${german.lat},${german.lng}" else ""
    return "https://www.google.com/maps/dir/?api=1$origin&destination=$lat,$lng&travelmode=driving"
}

@Composable
private fun AddCard(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(MaterialTheme.shapes.large)
            .dashedBorder(MaterialTheme.colorScheme.outline, 24.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "  Tankstelle im Ausland hinzufügen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---------------------------------------------------------------- Deutsche Tankstelle

@Composable
private fun GermanCard(station: GermanStation, s: Settings, m: Market) {
    val de: DePrice? = m.de?.takeIf { it.stationId == station.id }
    val price = de?.priceFor(s.fuel)
    val r = LocalResultColors.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    val area = m.area?.takeIf { it.stationId == station.id }?.let { Stats.area(it, s.fuel) }
    val history = if (m.historyStationId == station.id) m.history else emptyList()
    val now = System.currentTimeMillis()
    val dayStart = LocalDate.now(BERLIN).atStartOfDay(BERLIN).toInstant().toEpochMilli()
    val today = Stats.timeWeighted(history, s.fuel, dayStart, now + 1)
    val week = Stats.timeWeighted(history, s.fuel, now - 7L * 24 * 3_600_000L, now + 1)
        ?.takeIf { it.observations > (today?.observations ?: 0) }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(station.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            de?.let {
                val color = if (it.status == "open") r.good else MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text("  " + Fmt.status(it.status), style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (price != null) {
                PumpPrice(price, MaterialTheme.typography.headlineLarge)
            } else {
                Text("–", style = MaterialTheme.typography.headlineLarge)
            }
            Text(
                "  " + s.fuel.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val reference = area?.average ?: today?.average
            if (price != null && reference != null) DeltaTag(price, reference)
        }
        val canExpand = price != null && (area != null || today != null || week != null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                de?.let {
                    buildString {
                        append(if (it.isStale) "Letzter Preis vom ${Fmt.time(it.pricedAt)}" else "Stand ${Fmt.time(it.fetchedAt)}")
                        area?.let { a -> append(" · Ø ${Fmt.km(a.radiusKm)}: ${Fmt.eurPerL(a.average)}") }
                    }
                }.orEmpty(),
                style = MaterialTheme.typography.bodySmall.merge(Tabular),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (canExpand) {
                ExpandButton(
                    expanded = expanded,
                    closedLabel = "Mehr",
                    openLabel = "Weniger",
                    onToggle = { expanded = !expanded },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (price != null && canExpand) {
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    area?.let {
                        CompareRow(
                            "Umgebung jetzt · ${Fmt.eurPerL(it.average)}",
                            "Ø von ${it.count} geöffneten im Umkreis ${Fmt.km(it.radiusKm)}",
                            it.average,
                            price,
                        )
                    }
                    today?.let {
                        CompareRow(
                            "Ø heute · ${Fmt.eurPerL(it.average)}",
                            "aus ${it.observations} ${if (it.observations == 1) "Abruf" else "Abrufen"} · " +
                                "${Fmt.eurPerL(it.min)} – ${Fmt.eurPerL(it.max)}",
                            it.average,
                            price,
                        )
                    }
                    week?.let {
                        CompareRow(
                            "Ø 7 Tage · ${Fmt.eurPerL(it.average)}",
                            "aus ${it.observations} Abrufen · ${Fmt.eurPerL(it.min)} – ${Fmt.eurPerL(it.max)}",
                            it.average,
                            price,
                        )
                    }
                    area?.let {
                        Text(
                            "Günstigste in der Nähe: ${it.cheapest.name} ${Fmt.eurPerL(it.cheapestPrice)}" +
                                (it.cheapest.distKm?.let { d -> " · ${Fmt.dec(d, 1)} km" } ?: ""),
                            style = MaterialTheme.typography.bodySmall.merge(Tabular),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (today != null || week != null) {
                        Text(
                            "Ø heute und Ø 7 Tage stammen aus deinen eigenen Abrufen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareRow(title: String, detail: String, average: Double, own: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.merge(Tabular))
            Text(detail, style = MaterialTheme.typography.bodySmall.merge(Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DeltaTag(own, average, short = true)
    }
}

// ---------------------------------------------------------------- Sonstiges

@Composable
private fun SettingsRow(s: Settings, onOpenSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenSettings)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp),
    ) {
        Text(
            listOf(
                s.fuel.label,
                "${Fmt.dec(s.consumption, 1)} l/100 km",
                "${Fmt.dec(s.liters, 0)} l",
                if (s.wearCentPerKm > 0.0) "Verschleiß ${Fmt.dec(s.wearCentPerKm, 1)} ct/km" else "ohne Verschleiß",
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Einstellungen öffnen",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Footer(onSources: () -> Unit) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            "Daten: Tankerkönig (CC BY 4.0) · OpenStreetMap · ohne Gewähr",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onSources, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Quellen & Lizenzen")
        }
        CopyrightRow()
    }
}

@Composable
private fun ErrorCard(errors: List<String>) {
    // Neutral statt rosa, damit sie nicht wie ein "Lohnt sich nicht"-Ergebnis aussieht.
    AppCard(contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Text("Nicht alles konnte geladen werden", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
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
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("Zu den Einstellungen") }
    }
}

@Composable
private fun HintCard() {
    AppCard {
        Text("Noch keine Tankstelle im Ausland", style = MaterialTheme.typography.titleSmall)
        Text(
            "Such unten eine Tankstelle in Tschechien oder Polen. Die Strecke rechnet die App selbst aus.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
