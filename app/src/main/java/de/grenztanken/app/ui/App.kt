package de.grenztanken.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.grenztanken.app.AppViewModel

enum class Screen { Home, Settings, Search }

@Composable
fun GrenztankenApp(vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val market by vm.market.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val osmSearch by vm.osmSearch.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    // Beim Öffnen und bei jeder Rückkehr in die App aktualisieren (mit Mindestabständen).
    LifecycleResumeEffect(Unit) {
        vm.refresh(force = false)
        onPauseOrDispose { }
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen == Screen.Search) Screen.Settings else Screen.Home
    }

    GrenztankenTheme {
        when (screen) {
            Screen.Home -> HomeScreen(
                settings = settings,
                market = market,
                onRefresh = { vm.refresh(force = true) },
                onOpenSettings = { screen = Screen.Settings },
                onSaveForeign = vm::saveForeign,
                onDeleteForeign = vm::deleteForeign,
                osmSearch = osmSearch,
                onOsmAroundGerman = vm::osmSearchAroundGerman,
                onOsmAroundPlace = vm::osmSearchAroundPlace,
                onOsmClear = vm::clearOsmSearch,
                routeKm = vm::routeKmFromGerman,
            )

            Screen.Settings -> SettingsScreen(
                settings = settings,
                onBack = {
                    screen = Screen.Home
                    vm.refresh(force = false)
                },
                onUpdate = vm::updateSettings,
                onPickStation = { screen = Screen.Search },
            )

            Screen.Search -> StationSearchScreen(
                search = search,
                onBack = { screen = Screen.Settings },
                onSearchPlace = vm::searchPlace,
                onSearchDevice = vm::searchDeviceLocation,
                onPermissionDenied = { vm.searchError("Ohne Standort-Berechtigung bitte Ort oder PLZ eingeben.") },
                onPick = { station ->
                    vm.selectGerman(station)
                    screen = Screen.Settings
                },
            )
        }
    }
}
