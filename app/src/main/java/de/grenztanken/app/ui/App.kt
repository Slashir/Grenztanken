package de.grenztanken.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import de.grenztanken.app.AppViewModel

/** [depth] bestimmt die Richtung der Übergangsanimation (tiefer = von rechts herein). */
enum class Screen(val depth: Int) { Home(0), Settings(1), Search(2), Adac(2) }

@Composable
fun GrenztankenApp(vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val market by vm.market.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val osmSearch by vm.osmSearch.collectAsStateWithLifecycle()
    val adac by vm.adac.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    // Beim Öffnen und bei jeder Rückkehr in die App aktualisieren (mit Mindestabständen).
    LifecycleResumeEffect(Unit) {
        vm.refresh(force = false)
        onPauseOrDispose { }
    }

    fun back() {
        screen = when (screen) {
            Screen.Home -> Screen.Home
            Screen.Settings -> {
                vm.refresh(force = false)
                Screen.Home
            }
            Screen.Search -> Screen.Settings
            Screen.Adac -> if (vm.adacBack()) Screen.Adac else Screen.Settings
        }
    }

    BackHandler(enabled = screen != Screen.Home) { back() }

    GrenztankenTheme {
        val settings = settings ?: run {
            // Einstellungen werden noch geladen: nur der Hintergrund, kein leerer Zwischenstand.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
            return@GrenztankenTheme
        }
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState.depth >= initialState.depth
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(320)) { w -> dir * w / 5 } + fadeIn(tween(220, delayMillis = 60)))
                    .togetherWith(slideOutHorizontally(tween(320)) { w -> -dir * w / 5 } + fadeOut(tween(160)))
            },
            label = "screen",
        ) { current ->
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (current) {
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
                        onBack = ::back,
                        onUpdate = vm::updateSettings,
                        onPickStation = { screen = Screen.Search },
                        onOpenAdac = { screen = Screen.Adac },
                    )

                    Screen.Search -> StationSearchScreen(
                        search = search,
                        onBack = ::back,
                        onSearchPlace = vm::searchPlace,
                        onSearchDevice = vm::searchDeviceLocation,
                        onPermissionDenied = { vm.searchError("Ohne Standort-Berechtigung bitte Ort oder PLZ eingeben.") },
                        onPick = { station ->
                            vm.selectGerman(station)
                            screen = Screen.Settings
                        },
                    )

                    Screen.Adac -> AdacScreen(
                        adac = adac,
                        settings = settings,
                        onBack = ::back,
                        onOpen = vm::adacOpen,
                        onRetry = vm::adacRetry,
                        onPickBrand = vm::adacPickBrand,
                        onPickModel = vm::adacPickModel,
                        onPickGeneration = vm::adacPickGeneration,
                        onPickVariant = vm::adacPickVariant,
                        onUpdate = vm::updateSettings,
                        onApply = { cost, takeConsumption ->
                            vm.adacApply(cost, takeConsumption)
                            screen = Screen.Settings
                        },
                    )
                }
            }
        }
    }
}
