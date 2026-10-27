@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.grenztanken.app.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    val step = adac.step
    // Filter gilt pro Schritt und wird beim Wechsel geleert.
    var filter by rememberSaveable(step) { mutableStateOf("") }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
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
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
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
                item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            }
            adac.error?.let { error ->
                item { ErrorBox(error, onRetry) }
            }
            if (!adac.loading && adac.error == null) {
                when (step) {
                    AdacStep.BRAND -> {
                        val list = adac.brands.filter { it.name.contains(filter.trim(), ignoreCase = true) }
                        itemsIndexed(list, key = { _, e -> "b-" + e.slug }) { i, e ->
                            GroupedRow(i, list.size, onClick = { onPickBrand(e) }) {
                                EntryRow(e.name, monogram = true)
                            }
                        }
                    }

                    AdacStep.MODEL -> {
                        val list = adac.models.filter { it.name.contains(filter.trim(), ignoreCase = true) }
                        itemsIndexed(list, key = { _, e -> "m-" + e.slug }) { i, e ->
                            GroupedRow(i, list.size, onClick = { onPickModel(e) }) { EntryRow(e.name) }
                        }
                    }

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
                        if (list.isEmpty() && adac.variants.isEmpty()) {
                            item { Hint("Für diese Generation führt der ADAC keine Varianten.") }
                        }
                        itemsIndexed(list, key = { _, v -> "v-" + v.id }) { i, v ->
                            GroupedRow(i, list.size, onClick = { onPickVariant(v) }) {
                                EntryRow(
                                    title = v.name.removePrefix(prefix),
                                    subtitle = listOf(v.fuel, v.hp.takeIf { it.isNotEmpty() }?.let { "$it PS" })
                                        .filter { !it.isNullOrEmpty() }.joinToString(" · "),
                                )
                            }
                        }
                    }

                    AdacStep.COSTS -> item {
                        val costs = adac.costs
                        val variant = adac.variant
                        if (costs == null || variant == null) {
                            Hint(
                                "Für diese Variante hat der ADAC noch keine Kostenrechnung. Bei ganz neuen " +
                                    "Modellen kommt sie meist einige Monate nach Marktstart. Wähle eine ähnliche " +
                                    "Variante oder trage den Wert selbst ein.",
                            )
                        } else {
                            CostsView(
                                carName = variant.name,
                                costs = costs,
                                settings = settings,
                                url = Adac.pageUrl(adac.brand!!.slug, adac.model!!.slug, adac.generation!!.slug, variant.id),
                                onUpdate = onUpdate,
                                onApply = onApply,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumb(adac: AdacState) {
    val parts = listOfNotNull(
        adac.brand?.name,
        adac.model?.name,
        adac.generation?.let { it.name + if (it.facelift) " Facelift" else "" },
    )
    if (parts.isEmpty()) {
        Text(
            "Werkstatt- und Reifenkosten aus dem ADAC-Autokatalog übernehmen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            parts.forEach {
                Tag(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
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
            .padding(bottom = 12.dp),
    )
}

@Composable
private fun EntryRow(title: String, subtitle: String = "", monogram: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (monogram) {
            Monogram(title)
            Spacer(Modifier.size(14.dp))
        }
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
    carName: String,
    costs: Adac.Costs,
    settings: Settings,
    url: String,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onApply: (AdacCost, takeConsumption: Boolean) -> Unit,
) {
    val uri = LocalUriHandler.current
    var months by rememberSaveable { mutableStateOf(settings.adacCost?.months ?: AdacCost.DEFAULT_MONTHS) }
    var withDeterioration by rememberSaveable { mutableStateOf(false) }
    var takeConsumption by rememberSaveable { mutableStateOf(false) }
    var km by rememberSaveable { mutableStateOf(settings.kmPerYear.toString()) }
    val kmValue = km.toIntOrNull()?.takeIf { it in 1_000..200_000 }
    val k = kmValue ?: settings.kmPerYear
    val cost = AdacCost(carName, url, costs.points, months, withDeterioration)
    val steps = cost.hasMileageSteps
    val workshopOnly = cost.copy(withDeterioration = false)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard {
            Text(carName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.dec(cost.centPerKm(k), 1), style = MaterialTheme.typography.displayMedium.merge(Tabular))
                Text("  ct/km", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text(
                (if (steps) "Zusatzkosten pro gefahrenem km" else "Durchschnitt pro km") +
                    " · ${Fmt.int(k)} km im Jahr · ${years(cost.usedMonths)} · " +
                    (if (withDeterioration) "Werkstatt, Reifen, Wertverlust" else "Werkstatt und Reifen"),
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor(),
            )
        }

        AppCard {
            SectionLabel("Deine Nutzung", color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = km,
                onValueChange = { value ->
                    km = value.filter { it.isDigit() }.take(6)
                    km.toIntOrNull()?.takeIf { it in 1_000..200_000 }?.let { v -> onUpdate { it.copy(kmPerYear = v) } }
                },
                label = { Text("km pro Jahr") },
                isError = kmValue == null,
                supportingText = {
                    Text(
                        if (!steps) {
                            "Für diese Variante nennt der ADAC nur ${Fmt.int(cost.minKm)} km im Jahr."
                        } else if (k < cost.minKm || k > cost.maxKm) {
                            "ADAC-Werte gibt es von ${Fmt.int(cost.minKm)} bis ${Fmt.int(cost.maxKm)} km; " +
                                "darüber hinaus rechnet die App mit der äußersten Stufe weiter."
                        } else {
                            "ADAC-Werte von ${Fmt.int(cost.minKm)} bis ${Fmt.int(cost.maxKm)} km im Jahr, " +
                                "dazwischen wird umgerechnet."
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (cost.holdings.size > 1) {
                Text("Haltedauer bzw. Leasinglaufzeit", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    cost.holdings.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = cost.usedMonths == m,
                            onClick = { months = m },
                            shape = SegmentedButtonDefaults.itemShape(i, cost.holdings.size),
                        ) { Text(years(m)) }
                    }
                }
            }
        }

        AppCard {
            SectionLabel("ADAC-Werte", color = MaterialTheme.colorScheme.primary)
            ValueRow("Werkstatt und Reifen", "${Fmt.eur(cost.workshopPerMonth(k))} / Monat")
            if (steps) {
                ValueRow("ein km mehr kostet", "${Fmt.dec(workshopOnly.workshopCentPerKm(k), 1)} ct", bold = true)
                ValueRow(
                    "zum Vergleich: Durchschnitt pro km",
                    "${Fmt.dec(workshopOnly.averageCentPerKm(k), 1)} ct",
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (steps) {
                SwitchRow(
                    title = "Wertverlust mitrechnen",
                    subtitle = "${Fmt.eur(cost.deteriorationPerMonth(k))} im Monat, davon kostet ein km mehr " +
                        "${Fmt.dec(cost.deteriorationCentPerKm(k), 1)} ct. Nur sinnvoll, wenn dir das Auto gehört.",
                    checked = withDeterioration,
                    onChange = { withDeterioration = it },
                )
            }
            costs.consumption?.let { c ->
                SwitchRow(
                    title = "Verbrauch übernehmen",
                    subtitle = "ADAC: ${Fmt.dec(c, 1)} l/100 km statt deiner ${Fmt.dec(settings.consumption, 1)} l/100 km",
                    checked = takeConsumption,
                    onChange = { takeConsumption = it },
                )
            }
            Text(
                if (steps) {
                    "Für den Umweg zählt, was jeder zusätzliche km kostet. Dafür vergleicht die App die ADAC-Kosten " +
                        "bei deiner Fahrleistung mit ${Fmt.int(AdacCost.STEP_KM)} km mehr und weniger im Jahr."
                } else {
                    "Zusatzkosten je km und Wertverlust je km gibt es beim ADAC nur für neuere Varianten. " +
                        "Hier rechnet die App mit dem Durchschnitt der Werkstattkosten."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Leasing mit Wartungsvertrag: nichts übernehmen. Dann fallen nur Mehrkilometer an, " +
                    "die du in den Einstellungen selbst einträgst.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = { onApply(cost, takeConsumption) },
            enabled = kmValue != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Text("  ${Fmt.dec(cost.centPerKm(k), 1)} ct/km übernehmen")
        }
        OutlinedButton(onClick = { uri.openUri(url) }, modifier = Modifier.fillMaxWidth()) {
            Text("Beim ADAC ansehen")
        }
        Text(
            "Quelle: ADAC Autokatalog, Kostenrechnung der Variante (${costs.points.size} " +
                "Kombinationen aus Haltedauer und Fahrleistung).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** 60 → "5 Jahre", 24 → "2 Jahre", sonst Monate. */
fun years(months: Int): String = when {
    months % 12 != 0 -> "$months Monate"
    months == 12 -> "1 Jahr"
    else -> "${months / 12} Jahre"
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
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
