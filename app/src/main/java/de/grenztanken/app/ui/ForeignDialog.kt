@file:OptIn(ExperimentalMaterial3Api::class)

package de.grenztanken.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.grenztanken.app.model.Country
import de.grenztanken.app.model.ForeignStation
import java.util.UUID

@Composable
fun ForeignDialog(
    initial: ForeignStation?,
    onDismiss: () -> Unit,
    onSave: (ForeignStation) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var country by remember { mutableStateOf(initial?.country ?: Country.CZ) }
    var km by remember { mutableStateOf(Fmt.input(initial?.oneWayKm)) }
    var petrol by remember { mutableStateOf(Fmt.input(initial?.manualPetrol)) }
    var diesel by remember { mutableStateOf(Fmt.input(initial?.manualDiesel)) }

    val kmValue = Fmt.parse(km)?.takeIf { it >= 0.0 && it < 1000.0 }
    val petrolOk = petrol.isBlank() || Fmt.parse(petrol)?.let { it > 0.0 } == true
    val dieselOk = diesel.isBlank() || Fmt.parse(diesel)?.let { it > 0.0 } == true
    val valid = name.isNotBlank() && kmValue != null && petrolOk && dieselOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tankstelle hinzufügen" else "Tankstelle bearbeiten") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name, z. B. Orlen Brandov") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Country.entries.forEachIndexed { index, c ->
                        SegmentedButton(
                            selected = country == c,
                            onClick = { country = c },
                            shape = SegmentedButtonDefaults.itemShape(index, Country.entries.size),
                        ) { Text("${c.flag} ${c.label}") }
                    }
                }
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it },
                    label = { Text("Strecke einfach in km") },
                    supportingText = { Text("Zusätzlicher Weg gegenüber deiner deutschen Tankstelle") },
                    isError = km.isNotBlank() && kmValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
}
