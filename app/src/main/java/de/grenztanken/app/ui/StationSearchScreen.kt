@file:OptIn(ExperimentalMaterial3Api::class)

package de.grenztanken.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.grenztanken.app.SearchState
import de.grenztanken.app.data.Tankerkoenig

@Composable
fun StationSearchScreen(
    search: SearchState,
    onBack: () -> Unit,
    onSearchPlace: (String) -> Unit,
    onSearchDevice: () -> Unit,
    onPermissionDenied: () -> Unit,
    onPick: (Tankerkoenig.Station) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onSearchDevice() else onPermissionDenied() }

    fun searchByLocation() {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onSearchDevice() else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tankstelle suchen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Ort oder PLZ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearchPlace(query) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onSearchPlace(query) },
                    enabled = query.isNotBlank() && !search.loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text("  Suchen")
                }
                OutlinedButton(
                    onClick = { searchByLocation() },
                    enabled = !search.loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Text("  In der Nähe")
                }
            }
            if (search.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            search.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                items(search.results, key = { it.id }) { station ->
                    StationRow(station, onClick = { onPick(station) })
                }
            }
        }
    }
}

@Composable
private fun StationRow(station: Tankerkoenig.Station, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(station.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                station.dist?.let {
                    Text("${Fmt.dec(it, 1)} km", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                station.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val prices = listOf("E5" to station.e5, "E10" to station.e10, "Diesel" to station.diesel)
                .joinToString("  ·  ") { (label, price) -> "$label ${price?.let { Fmt.dec(it, 3) } ?: "–"}" }
            Text(
                if (station.isOpen) prices else "$prices  ·  geschlossen",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
