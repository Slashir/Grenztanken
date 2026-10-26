@file:OptIn(ExperimentalMaterial3Api::class)

package de.grenztanken.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
) {
    val uri = LocalUriHandler.current
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var consumption by remember { mutableStateOf(Fmt.input(settings.consumption)) }
    var liters by remember { mutableStateOf(Fmt.input(settings.liters)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Tankerkönig-Schlüssel") {
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
                Text(
                    "Kostenlos und persönlich. Er bleibt nur auf deinem Gerät und wird ausschließlich " +
                        "an Tankerkönig gesendet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { uri.openUri("https://onboarding.tankerkoenig.de/") }) {
                    Text("Schlüssel beantragen")
                }
            }

            Section("Kraftstoff") {
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

            Section("Dein Auto") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = consumption,
                        onValueChange = { value ->
                            consumption = value
                            Fmt.parse(value)?.takeIf { it > 0.0 && it < 50.0 }?.let { v ->
                                onUpdate { it.copy(consumption = v) }
                            }
                        },
                        label = { Text("Verbrauch l/100 km") },
                        isError = Fmt.parse(consumption)?.let { it > 0.0 && it < 50.0 } != true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = liters,
                        onValueChange = { value ->
                            liters = value
                            Fmt.parse(value)?.takeIf { it > 0.0 && it < 500.0 }?.let { v ->
                                onUpdate { it.copy(liters = v) }
                            }
                        },
                        label = { Text("Tankmenge in l") },
                        isError = Fmt.parse(liters)?.let { it > 0.0 && it < 500.0 } != true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Section("Deine deutsche Tankstelle") {
                val german = settings.german
                if (german == null) {
                    Text("Noch keine gewählt.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(german.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        german.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onPickStation) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text("  Tankstelle suchen")
                }
            }

            Section("Datenquellen") {
                Text(
                    "Deutschland: Tankerkönig (Daten der Markttransparenzstelle für Kraftstoffe, CC BY 4.0). " +
                        "Tschechien: tägliche Höchstpreise des Finanzministeriums. " +
                        "Polen: tägliche Höchstpreise des Energieministeriums. " +
                        "Wechselkurse: Tschechische Nationalbank (ČNB) und Polnische Nationalbank (NBP). " +
                        "Gibt es keinen gültigen Höchstpreis, rechnet die App mit dem wöchentlichen " +
                        "EU-Landesdurchschnitt (EU Weekly Oil Bulletin über fuel-prices.eu, CC BY 4.0) – " +
                        "oder mit deinem Preis von der Säule, wenn du einen einträgst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
