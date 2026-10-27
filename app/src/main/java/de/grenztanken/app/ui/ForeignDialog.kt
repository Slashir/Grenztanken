@file:OptIn(ExperimentalMaterial3Api::class)

package de.grenztanken.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.grenztanken.app.OsmSearchState
import de.grenztanken.app.data.Osm
import de.grenztanken.app.model.Country
import de.grenztanken.app.model.ForeignStation
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun ForeignDialog(
    initial: ForeignStation?,
    hasGerman: Boolean,
    osmSearch: OsmSearchState,
    onOsmAroundGerman: () -> Unit,
    onOsmAroundPlace: (String) -> Unit,
    onOsmClear: () -> Unit,
    routeKm: suspend (Double, Double) -> Result<Double>,
    onDismiss: () -> Unit,
    onSave: (ForeignStation) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var country by remember { mutableStateOf(initial?.country ?: Country.CZ) }
    var km by remember { mutableStateOf(Fmt.input(initial?.oneWayKm)) }
    var petrol by remember { mutableStateOf(Fmt.input(initial?.manualPetrol)) }
    var diesel by remember { mutableStateOf(Fmt.input(initial?.manualDiesel)) }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var lat by remember { mutableStateOf(initial?.lat) }
    var lng by remember { mutableStateOf(initial?.lng) }
    var searching by remember { mutableStateOf(false) }
    var routing by remember { mutableStateOf(false) }
    var routeNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun calcRoute(toLat: Double, toLng: Double) {
        routing = true
        routeNote = null
        scope.launch {
            routeKm(toLat, toLng)
                .onSuccess {
                    km = Fmt.input((it * 10).roundToInt() / 10.0)
                    routeNote = "Autostrecke ab deiner deutschen Tankstelle (OSRM)"
                }
                .onFailure { routeNote = "Strecke nicht berechnet: ${it.message}. Bitte von Hand eintragen." }
            routing = false
        }
    }

    val kmValue = Fmt.parse(km)?.takeIf { it >= 0.0 && it < 1000.0 }
    val petrolOk = petrol.isBlank() || Fmt.parse(petrol)?.let { it > 0.0 } == true
    val dieselOk = diesel.isBlank() || Fmt.parse(diesel)?.let { it > 0.0 } == true
    val valid = name.isNotBlank() && kmValue != null && petrolOk && dieselOk && !routing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tankstelle hinzufügen" else "Tankstelle bearbeiten") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { searching = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text("  In OpenStreetMap suchen")
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name, z. B. Orlen Brandov") },
                    supportingText = address.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Country.entries.forEachIndexed { index, c ->
                        SegmentedButton(
                            selected = country == c,
                            onClick = { country = c },
                            shape = SegmentedButtonDefaults.itemShape(index, Country.entries.size),
                        ) { Text(c.label) }
                    }
                }
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it; routeNote = null },
                    label = { Text("Strecke einfach in km") },
                    supportingText = {
                        Text(
                            when {
                                routing -> "Strecke wird berechnet …"
                                routeNote != null -> routeNote!!
                                else -> "Zusätzlicher Weg gegenüber deiner deutschen Tankstelle"
                            },
                        )
                    },
                    isError = km.isNotBlank() && kmValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (routing) LinearProgressIndicator(Modifier.fillMaxWidth())
                val la = lat
                val ln = lng
                if (la != null && ln != null && hasGerman && !routing) {
                    TextButton(onClick = { calcRoute(la, ln) }) { Text("Strecke neu berechnen") }
                }
                Text(
                    "Optional: Preis an der Säule. Leer lassen, dann gilt der amtliche Höchstpreis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = petrol,
                    onValueChange = { petrol = it },
                    label = { Text("Benzin in ${country.currencySymbol}/l") },
                    isError = !petrolOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = diesel,
                    onValueChange = { diesel = it },
                    label = { Text("Diesel in ${country.currencySymbol}/l") },
                    isError = !dieselOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        ForeignStation(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            country = country,
                            oneWayKm = kmValue ?: 0.0,
                            manualPetrol = Fmt.parse(petrol),
                            manualDiesel = Fmt.parse(diesel),
                            address = address,
                            lat = lat,
                            lng = lng,
                        ),
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial.id) }) {
                        Text("Löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )

    if (searching) {
        OsmSearchDialog(
            state = osmSearch,
            hasGerman = hasGerman,
            onAroundGerman = onOsmAroundGerman,
            onAroundPlace = onOsmAroundPlace,
            onDismiss = { searching = false; onOsmClear() },
            onPick = { st ->
                name = st.name
                country = st.country
                address = st.address
                lat = st.lat
                lng = st.lng
                searching = false
                onOsmClear()
                if (hasGerman) {
                    calcRoute(st.lat, st.lng)
                } else {
                    routeNote = "Ohne deutsche Tankstelle keine Streckenberechnung. Bitte von Hand eintragen."
                }
            },
        )
    }
}

@Composable
private fun OsmSearchDialog(
    state: OsmSearchState,
    hasGerman: Boolean,
    onAroundGerman: () -> Unit,
    onAroundPlace: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Osm.FuelStation) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    // Beim Öffnen gleich rund um die deutsche Tankstelle suchen.
    LaunchedEffect(Unit) { if (hasGerman) onAroundGerman() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tankstelle in CZ / PL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Ort, z. B. Hrádek nad Nisou") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onAroundPlace(query) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onAroundPlace(query) },
                    enabled = query.isNotBlank() && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text("  Rund um diesen Ort")
                }
                OutlinedButton(
                    onClick = onAroundGerman,
                    enabled = hasGerman && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Text("  Rund um meine deutsche Tankstelle")
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.results.isNotEmpty()) {
                    Text(
                        "${state.results.size} Tankstellen im Umkreis von ${Osm.RADIUS_KM.toInt()} km" +
                            if (state.truncated) " (Liste gekürzt – Ort genauer wählen)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.results, key = { it.osmId }) { st ->
                        Card(onClick = { onPick(st) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${st.country.code} · ${st.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${Fmt.dec(st.distKm, 1)} km", style = MaterialTheme.typography.bodySmall)
                                }
                                if (st.address.isNotBlank()) {
                                    Text(
                                        st.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "Entfernung = Luftlinie. Daten © OpenStreetMap-Mitwirkende (ODbL).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
