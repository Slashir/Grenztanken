@file:OptIn(ExperimentalMaterial3Api::class)

package de.grenztanken.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.grenztanken.app.model.Fuel
import de.grenztanken.app.model.Settings

@Composable
fun SettingsScreen(
    settings: Settings,
    onBack: () -> Unit,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onPickStation: () -> Unit,
    onOpenAdac: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var consumption by remember { mutableStateOf(Fmt.input(settings.consumption)) }
    var liters by remember { mutableStateOf(Fmt.input(settings.liters)) }
    var wear by remember { mutableStateOf(Fmt.input(settings.manualWearCentPerKm.takeIf { it > 0.0 })) }
    var km by remember { mutableStateOf(settings.kmPerYear.toString()) }
    // Neu befüllen, wenn sich der Wert von außen ändert (z. B. im ADAC-Bildschirm), nicht beim eigenen Tippen.
    LaunchedEffect(settings.consumption) {
        if (Fmt.parse(consumption) != settings.consumption) consumption = Fmt.input(settings.consumption)
    }
    LaunchedEffect(settings.kmPerYear) {
        if (km.toIntOrNull() != settings.kmPerYear) km = settings.kmPerYear.toString()
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(Icons.Filled.Lock, "Tankerkönig-Schlüssel", "für die Preise in Deutschland") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { value ->
                        apiKey = value.trim()
                        onUpdate { it.copy(apiKey = value.trim()) }
                    },
                    label = { Text("API-Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Muted(
                    "Kostenlos und persönlich. Er bleibt nur auf deinem Gerät und wird ausschließlich " +
                        "an Tankerkönig gesendet.",
                )
                TextButton(onClick = { uri.openUri("https://onboarding.tankerkoenig.de/") }) {
                    Text("Schlüssel beantragen")
                }
            }

            Section(AppIcons.GasStation, "Kraftstoff") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Fuel.entries.forEachIndexed { index, fuel ->
                        SegmentedButton(
                            selected = settings.fuel == fuel,
                            onClick = { onUpdate { it.copy(fuel = fuel) } },
                            shape = SegmentedButtonDefaults.itemShape(index, Fuel.entries.size),
                        ) { Text(fuel.label) }
                    }
                }
            }

            Section(AppIcons.Car, "Dein Auto") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = consumption,
                        onChange = { value ->
                            consumption = value
                            Fmt.parse(value)?.takeIf { it > 0.0 && it < 50.0 }?.let { v -> onUpdate { it.copy(consumption = v) } }
                        },
                        label = "Verbrauch l/100 km",
                        valid = Fmt.parse(consumption)?.let { it > 0.0 && it < 50.0 } == true,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = liters,
                        onChange = { value ->
                            liters = value
                            Fmt.parse(value)?.takeIf { it > 0.0 && it < 500.0 }?.let { v -> onUpdate { it.copy(liters = v) } }
                        },
                        label = "Tankmenge in l",
                        valid = Fmt.parse(liters)?.let { it > 0.0 && it < 500.0 } == true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Section(Icons.Filled.Build, "Verschleiß & Wartung", "Kosten pro km außer Sprit") {
                val adac = settings.adacCost
                if (adac != null) {
                    Text(adac.car, style = MaterialTheme.typography.titleSmall)
                    Muted(
                        "ADAC Autokatalog · ${years(adac.usedMonths)} Haltedauer · " +
                            "Werkstatt und Reifen ${Fmt.eur(adac.workshopPerMonth(settings.kmPerYear))} im Monat",
                    )
                    NumberField(
                        value = km,
                        onChange = { value ->
                            km = value.filter { it.isDigit() }.take(6)
                            km.toIntOrNull()?.takeIf { it in 1_000..200_000 }?.let { v -> onUpdate { it.copy(kmPerYear = v) } }
                        },
                        label = "Deine km pro Jahr",
                        valid = km.toIntOrNull()?.let { it in 1_000..200_000 } == true,
                        integer = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (adac.hasMileageSteps) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Wertverlust mitrechnen", style = MaterialTheme.typography.bodyLarge)
                                Muted("ein km mehr: ${Fmt.dec(adac.deteriorationCentPerKm(settings.kmPerYear), 1)} ct")
                            }
                            Switch(
                                checked = adac.withDeterioration,
                                onCheckedChange = { on ->
                                    onUpdate { s -> s.copy(adacCost = s.adacCost?.copy(withDeterioration = on)) }
                                },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ValueRow(
                        if (adac.hasMileageSteps) "Zusatzkosten pro km" else "Durchschnitt pro km",
                        "${Fmt.dec(settings.wearCentPerKm, 1)} ct/km",
                        bold = true,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onOpenAdac) { Text("Ändern") }
                        TextButton(onClick = { onUpdate { it.copy(adacCost = null) } }) { Text("Selbst eintragen") }
                    }
                } else {
                    NumberField(
                        value = wear,
                        onChange = { value ->
                            wear = value
                            val v = if (value.isBlank()) 0.0 else Fmt.parse(value)?.takeIf { it >= 0.0 && it < 200.0 }
                            v?.let { c -> onUpdate { it.copy(manualWearCentPerKm = c) } }
                        },
                        label = "Verschleiß in ct/km",
                        placeholder = "leer = nur Sprit",
                        valid = wear.isBlank() || Fmt.parse(wear)?.let { it >= 0.0 && it < 200.0 } == true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Muted(
                        "Leasing mit Wartungsvertrag: leer lassen. Fährst du über deine Freikilometer, " +
                            "trag den Mehrkilometer-Satz aus deinem Vertrag ein.",
                    )
                    FilledTonalButton(onClick = onOpenAdac) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Aus dem ADAC-Autokatalog")
                    }
                }
            }

            Section(Icons.Filled.Place, "Deine deutsche Tankstelle") {
                val german = settings.german
                if (german == null) {
                    Text("Noch keine gewählt.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(german.name, style = MaterialTheme.typography.titleSmall)
                    Muted(german.address)
                }
                Button(onClick = onPickStation) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Tankstelle suchen")
                }
            }

            Section(Icons.Filled.Info, "Datenquellen") {
                Muted(
                    "Deutschland: Tankerkönig (Daten der Markttransparenzstelle für Kraftstoffe, CC BY 4.0). " +
                        "Tschechien: tägliche Höchstpreise des Finanzministeriums. " +
                        "Polen: tägliche Höchstpreise des Energieministeriums. " +
                        "Wechselkurse: Tschechische Nationalbank (ČNB) und Polnische Nationalbank (NBP). " +
                        "Gibt es keinen gültigen Höchstpreis, rechnet die App mit dem wöchentlichen " +
                        "EU-Landesdurchschnitt (EU Weekly Oil Bulletin über fuel-prices.eu, CC BY 4.0) – " +
                        "oder mit deinem Preis von der Säule, wenn du einen einträgst. " +
                        "Autokosten: ADAC Autokatalog. Schrift: Inter (SIL Open Font License).",
                )
            }
        }
    }
}

@Composable
private fun Section(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            IconBadge(icon)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Muted(it) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    valid: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    integer: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = !valid,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
