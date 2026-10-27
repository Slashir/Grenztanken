@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package de.grenztanken.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.grenztanken.app.AdacState
import de.grenztanken.app.AdacStep
import de.grenztanken.app.data.Adac
import de.grenztanken.app.model.AdacCost
import de.grenztanken.app.model.Settings

@Composable
fun AdacScreen(
    adac: AdacState,
    settings: Settings,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onPickBrand: (Adac.Entry) -> Unit,
    onPickModel: (Adac.Entry) -> Unit,
    onPickGeneration: (Adac.Generation) -> Unit,
    onPickVariant: (Adac.Variant) -> Unit,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onApply: (AdacCost, takeConsumption: Boolean) -> Unit,
) {
    LaunchedEffect(Unit) { onOpen() }
    val uri = LocalUriHandler.current
    val step = adac.step
    // Filter gilt pro Schritt und wird beim Wechsel geleert.
    var filter by rememberSaveable(step) { mutableStateOf("") }

    // Auswahl im Ergebnis; gehört der Variante, damit der feste Knopf unten sie kennt.
    val variantKey = adac.variant?.id ?: ""
    var months by rememberSaveable(variantKey) { mutableStateOf(settings.adacCost?.months ?: AdacCost.DEFAULT_MONTHS) }
    var withDeterioration by rememberSaveable(variantKey) { mutableStateOf(false) }
    var takeConsumption by rememberSaveable(variantKey) { mutableStateOf(false) }

    val variant = adac.variant
    val costs = adac.costs
    val url = if (variant != null && adac.brand != null && adac.model != null && adac.generation != null) {
        Adac.pageUrl(adac.brand.slug, adac.model.slug, adac.generation.slug, variant.id)
    } else {
        null
    }
    val cost = if (step == AdacStep.COSTS && costs != null && variant != null && url != null && !adac.loading) {
        AdacCost(variant.name, url, costs.points, months, withDeterioration)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            AdacStep.BRAND -> "Marke wählen"
                            AdacStep.MODEL -> "Modell wählen"
                            AdacStep.GENERATION -> "Generation"
                            AdacStep.VARIANT -> "Motor & Ausstattung"
                            AdacStep.COSTS -> "Autokosten"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (step == AdacStep.COSTS && url != null) {
                        IconButton(onClick = { uri.openUri(url) }) {
                            Icon(AppIcons.OpenInNew, contentDescription = "Beim ADAC ansehen", modifier = Modifier.size(22.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (cost != null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Button(
                            onClick = { onApply(cost, takeConsumption) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(52.dp),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Text(
                                "  ${Fmt.dec(cost.centPerKm(settings.kmPerYear), 1)} ct/km übernehmen",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Breadcrumb(adac) }
            if (step == AdacStep.BRAND || step == AdacStep.MODEL || step == AdacStep.VARIANT) {
                item {
                    FilterField(
                        value = filter,
                        onChange = { filter = it },
                        label = when (step) {
                            AdacStep.BRAND -> "Marke suchen"
                            AdacStep.MODEL -> "Modell suchen"
                            else -> "z. B. Hybrid, Diesel, 150 PS"
                        },
                    )
                }
            }
            if (adac.loading) {
                item {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
            }
            adac.error?.let { error ->
                item { ErrorBox(error, onRetry) }
            }
            if (!adac.loading && adac.error == null) {
                when (step) {
                    AdacStep.BRAND -> lettered(
                        adac.brands.filter { it.name.contains(filter.trim(), ignoreCase = true) },
                        "b",
                        onPickBrand,
                    )

                    AdacStep.MODEL -> lettered(
                        adac.models.filter { it.name.contains(filter.trim(), ignoreCase = true) },
                        "m",
                        onPickModel,
                    )

                    AdacStep.GENERATION -> {
                        val list = adac.generations
                        itemsIndexed(list, key = { _, g -> "g-" + g.slug }) { i, g ->
                            GroupedRow(i, list.size, onClick = { onPickGeneration(g) }) {
                                EntryRow(
                                    title = g.name + if (g.facelift) " · Facelift" else "",
                                    subtitle = g.years,
                                )
                            }
                        }
                    }

                    AdacStep.VARIANT -> {
                        val prefix = listOfNotNull(adac.brand?.name, adac.model?.name).joinToString(" ") + " "
                        val terms = filter.trim().split(" ").filter { it.isNotEmpty() }
                        val list = adac.variants.filter { v ->
                            val text = "${v.name} ${v.fuel} ${v.hp} PS"
                            terms.all { text.contains(it, ignoreCase = true) }
                        }
                        if (adac.variants.isEmpty()) {
                            item { Hint("Für diese Generation führt der ADAC keine Varianten.") }
                        }
                        itemsIndexed(list, key = { _, v -> "v-" + v.id }) { i, v ->
                            val (title, period) = splitVariant(v.name, prefix)
                            GroupedRow(i, list.size, onClick = { onPickVariant(v) }) {
                                EntryRow(title = title, subtitle = variantDetails(v, period))
                            }
                        }
                    }

                    AdacStep.COSTS -> item {
                        if (cost == null || variant == null || costs == null) {
                            Hint(
                                "Für diese Variante hat der ADAC noch keine Kostenrechnung. Bei ganz neuen " +
                                    "Modellen kommt sie meist einige Monate nach Marktstart. Wähle eine ähnliche " +
                                    "Variante oder trage den Wert selbst ein.",
                            )
                        } else {
                            val prefix = listOfNotNull(adac.brand?.name).joinToString(" ") + " "
                            val (title, period) = splitVariant(variant.name, prefix)
                            CostsView(
                                title = title,
                                subtitle = variantDetails(variant, period),
                                cost = cost,
                                consumption = costs.consumption,
                                pointCount = costs.points.size,
                                settings = settings,
                                onMonths = { months = it },
                                onDeterioration = { withDeterioration = it },
                                takeConsumption = takeConsumption,
                                onTakeConsumption = { takeConsumption = it },
                                onUpdate = onUpdate,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Liste mit Buchstaben-Überschriften (A, B, C …), je Buchstabe eine gruppierte Liste. */
private fun LazyListScope.lettered(list: List<Adac.Entry>, keyPrefix: String, onPick: (Adac.Entry) -> Unit) {
    list.groupBy { e -> e.name.first().uppercaseChar().takeIf { it.isLetter() }?.toString() ?: "#" }
        .forEach { (letter, group) ->
            stickyHeader(key = "$keyPrefix-h-$letter") {
                Text(
                    letter,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = 6.dp, top = 14.dp, bottom = 8.dp),
                )
            }
            itemsIndexed(group, key = { _, e -> "$keyPrefix-${e.slug}" }) { i, e ->
                GroupedRow(i, group.size, onClick = { onPick(e) }) { EntryRow(e.name) }
            }
        }
}

/** "Hyundai Tucson 1.6 T-GDI Select (ab 09/25)" → ("1.6 T-GDI Select", "ab 09/25") */
private fun splitVariant(name: String, prefix: String): Pair<String, String> {
    val n = name.removePrefix(prefix)
    val m = Regex("""\s*\(([^)]*)\)\s*$""").find(n) ?: return n to ""
    return n.substring(0, m.range.first) to m.groupValues[1].replace(" - ", "–")
}

private fun variantDetails(v: Adac.Variant, period: String): String =
    listOf(v.fuel, v.hp.takeIf { it.isNotEmpty() }?.let { "$it PS" }, period)
        .filter { !it.isNullOrEmpty() }
        .joinToString(" · ")

@Composable
private fun Breadcrumb(adac: AdacState) {
    val parts = listOfNotNull(
        adac.brand?.name,
        adac.model?.name,
        adac.generation?.let { it.name + if (it.facelift) " Facelift" else "" },
    )
    Text(
        if (parts.isEmpty()) "Werkstatt- und Reifenkosten aus dem ADAC-Autokatalog" else parts.joinToString(" › "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
    )
}

@Composable
private fun FilterField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Leeren") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

@Composable
private fun EntryRow(title: String, subtitle: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CostsView(
    title: String,
    subtitle: String,
    cost: AdacCost,
    consumption: Double?,
    pointCount: Int,
    settings: Settings,
    onMonths: (Int) -> Unit,
    onDeterioration: (Boolean) -> Unit,
    takeConsumption: Boolean,
    onTakeConsumption: (Boolean) -> Unit,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    var km by rememberSaveable { mutableStateOf(settings.kmPerYear.toString()) }
    var explain by rememberSaveable { mutableStateOf(false) }
    val k = settings.kmPerYear
    val steps = cost.hasMileageSteps
    val workshopOnly = cost.copy(withDeterioration = false)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalHeroColors.current.muted)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                Text(Fmt.dec(cost.centPerKm(k), 1), style = MaterialTheme.typography.displayMedium.merge(Tabular))
                Text("  ct/km", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                if (steps) "Zusatzkosten pro gefahrenem km" else "Durchschnitt pro km",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalHeroColors.current.muted,
            )
        }

        AppCard {
            if (cost.holdings.size > 1) {
                Text("Haltedauer bzw. Leasinglaufzeit", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    cost.holdings.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = cost.usedMonths == m,
                            onClick = { onMonths(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, cost.holdings.size),
                        ) { Text(if (m % 12 == 0) "${m / 12} J." else "$m M.") }
                    }
                }
            }
            OutlinedTextField(
                value = km,
                onValueChange = { value ->
                    km = value.filter { it.isDigit() }.take(6)
                    km.toIntOrNull()?.takeIf { it in 1_000..200_000 }?.let { v -> onUpdate { it.copy(kmPerYear = v) } }
                },
                label = { Text("km pro Jahr") },
                isError = km.toIntOrNull()?.let { it in 1_000..200_000 } != true,
                supportingText = {
                    Text(
                        when {
                            !steps -> "Der ADAC nennt für diese Variante nur ${Fmt.int(cost.minKm)} km im Jahr."
                            k < cost.minKm || k > cost.maxKm ->
                                "ADAC-Werte von ${Fmt.int(cost.minKm)} bis ${Fmt.int(cost.maxKm)} km; darüber hinaus " +
                                    "wird mit der äußersten Stufe weitergerechnet."
                            else -> "ADAC-Werte von ${Fmt.int(cost.minKm)} bis ${Fmt.int(cost.maxKm)} km im Jahr"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ValueRow(
                "Werkstatt und Reifen",
                "${Fmt.eur(cost.workshopPerMonth(k))} / Monat",
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (steps) {
                ValueRow(
                    "Durchschnitt pro km",
                    "${Fmt.dec(workshopOnly.averageCentPerKm(k), 1)} ct",
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    title = "Wertverlust mitrechnen",
                    subtitle = "ein km mehr: ${Fmt.dec(cost.deteriorationCentPerKm(k), 1)} ct · nur bei eigenem Auto",
                    checked = cost.withDeterioration,
                    onChange = onDeterioration,
                )
            }
            consumption?.let { c ->
                SwitchRow(
                    title = "ADAC-Verbrauch übernehmen",
                    subtitle = "${Fmt.dec(c, 1)} l/100 km statt deiner ${Fmt.dec(settings.consumption, 1)}",
                    checked = takeConsumption,
                    onChange = onTakeConsumption,
                )
            }
            ExpandButton(
                expanded = explain,
                closedLabel = "Wie wird gerechnet?",
                openLabel = "Wie wird gerechnet?",
                onToggle = { explain = !explain },
                color = MaterialTheme.colorScheme.primary,
            )
            AnimatedVisibility(visible = explain) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muted(
                        if (steps) {
                            "Für den Umweg zählt, was jeder zusätzliche km kostet, nicht der Durchschnitt. Die App " +
                                "vergleicht dazu die ADAC-Monatskosten bei deiner Fahrleistung mit " +
                                "${Fmt.int(AdacCost.STEP_KM)} km mehr und weniger im Jahr."
                        } else {
                            "Zusatzkosten je km und Wertverlust je km gibt es beim ADAC nur für neuere Varianten. " +
                                "Hier rechnet die App mit dem Durchschnitt der Werkstattkosten."
                        },
                    )
                    Muted(
                        "Leasing mit Wartungsvertrag: nichts übernehmen. Dann fallen nur Mehrkilometer an, " +
                            "die du in den Einstellungen selbst einträgst.",
                    )
                    Muted("Quelle: ADAC Autokatalog, $pointCount Kombinationen aus Haltedauer und Fahrleistung.")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Muted(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    AppCard {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBox(error: String, onRetry: () -> Unit) {
    AppCard {
        Text("ADAC-Katalog nicht erreichbar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text("Erneut versuchen") }
    }
}

/** 60 → "5 Jahre", 24 → "2 Jahre", sonst Monate. */
fun years(months: Int): String = when {
    months % 12 != 0 -> "$months Monate"
    months == 12 -> "1 Jahr"
    else -> "${months / 12} Jahre"
}
