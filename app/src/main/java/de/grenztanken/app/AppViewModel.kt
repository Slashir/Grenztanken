package de.grenztanken.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.grenztanken.app.data.Adac
import de.grenztanken.app.data.Geo
import de.grenztanken.app.data.Osm
import de.grenztanken.app.data.Repo
import de.grenztanken.app.data.Sources
import de.grenztanken.app.data.Tankerkoenig
import de.grenztanken.app.data.errorText
import de.grenztanken.app.logic.PriceResolver
import de.grenztanken.app.logic.Stats
import de.grenztanken.app.model.AdacCost
import de.grenztanken.app.model.Area
import de.grenztanken.app.model.AreaStation
import de.grenztanken.app.model.Country
import de.grenztanken.app.model.PriceObservation
import de.grenztanken.app.model.DePrice
import de.grenztanken.app.model.ForeignStation
import de.grenztanken.app.model.GermanStation
import de.grenztanken.app.model.Market
import de.grenztanken.app.model.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class SearchState(
    val loading: Boolean = false,
    val results: List<Tankerkoenig.Station> = emptyList(),
    val error: String? = null,
)

/** Suche nach Tankstellen in Tschechien und Polen über OpenStreetMap. */
data class OsmSearchState(
    val loading: Boolean = false,
    val results: List<Osm.FuelStation> = emptyList(),
    val error: String? = null,
    /** Wurde das Trefferlimit erreicht? Dann gibt es vermutlich weitere Tankstellen im Umkreis. */
    val truncated: Boolean = false,
)

enum class AdacStep { BRAND, MODEL, GENERATION, VARIANT, COSTS }

/** Schrittweise Auswahl im ADAC-Autokatalog: Marke → Modell → Generation → Variante → Kosten. */
data class AdacState(
    val loading: Boolean = false,
    val error: String? = null,
    val brands: List<Adac.Entry> = emptyList(),
    val brand: Adac.Entry? = null,
    val models: List<Adac.Entry> = emptyList(),
    val model: Adac.Entry? = null,
    val generations: List<Adac.Generation> = emptyList(),
    val generation: Adac.Generation? = null,
    val variants: List<Adac.Variant> = emptyList(),
    val variant: Adac.Variant? = null,
    /** null bei gewählter Variante: Der ADAC hat (noch) keine Kostenrechnung dafür. */
    val costs: Adac.Costs? = null,
) {
    val step: AdacStep
        get() = when {
            variant != null -> AdacStep.COSTS
            generation != null -> AdacStep.VARIANT
            model != null -> AdacStep.GENERATION
            brand != null -> AdacStep.MODEL
            else -> AdacStep.BRAND
        }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)

    /** null, bis die gespeicherten Einstellungen geladen sind (verhindert kurzes Aufblitzen von "Einrichten"). */
    val settings: StateFlow<Settings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _market = MutableStateFlow(Market())
    val market: StateFlow<Market> = _market.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _osmSearch = MutableStateFlow(OsmSearchState())
    val osmSearch: StateFlow<OsmSearchState> = _osmSearch.asStateFlow()
    private var osmJob: Job? = null

    private val _adac = MutableStateFlow(AdacState())
    val adac: StateFlow<AdacState> = _adac.asStateFlow()
    private var adacJob: Job? = null

    private var refreshJob: Job? = null
    private val loadLock = Mutex()
    private var loaded = false

    init {
        viewModelScope.launch { ensureLoaded() }
    }

    private suspend fun ensureLoaded() = loadLock.withLock {
        if (!loaded) {
            _market.value = repo.loadMarket()
            loaded = true
        }
    }

    /**
     * Holt neue Daten, mit Mindestabständen:
     * - Deutschland frühestens nach 5 Minuten (manuell: 1 Minute), wie von Tankerkönig erbeten.
     * - Höchstpreise und Kurse höchstens stündlich oder bei neuem Tag.
     * - EU-Wochendurchschnitt nur ohne Höchstpreis, höchstens täglich (manuell: stündlich).
     */
    fun refresh(force: Boolean) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            ensureLoaded()
            val s = repo.settings.first()
            val errors = mutableListOf<String>()
            var m = _market.value.copy(loading = true, errors = emptyList())
            _market.value = m

            val now = System.currentTimeMillis()
            val today = LocalDate.now(BERLIN)

            // Deutschland
            val station = s.german
            if (station != null && s.apiKey.isNotBlank()) {
                val age = now - (m.de?.fetchedAt ?: 0L)
                val minAge = if (force) 60_000L else 5 * 60_000L
                if (m.de?.stationId != station.id || age > minAge) {
                    try {
                        // Eine Umkreisabfrage liefert den eigenen Preis und die Umgebung zugleich.
                        val coords = germanCoords()
                        val list = coords?.let { (lat, lng) -> Tankerkoenig.list(s.apiKey, lat, lng, AREA_RADIUS_KM) }
                        val own = list?.firstOrNull { it.id == station.id }
                        val fresh = if (own != null) {
                            DePrice(station.id, own.e5, own.e10, own.diesel, if (own.isOpen) "open" else "closed", now)
                        } else {
                            val p = Tankerkoenig.prices(s.apiKey, listOf(station.id))[station.id]
                            DePrice(station.id, p?.e5, p?.e10, p?.diesel, p?.status ?: "no prices", now)
                        }
                        val history = if (m.historyStationId == station.id) m.history else emptyList()
                        m = m.copy(
                            de = DePrice.merge(m.de, fresh),
                            area = list?.let { all ->
                                Area(
                                    stationId = station.id,
                                    radiusKm = AREA_RADIUS_KM,
                                    fetchedAt = now,
                                    stations = all.filter { it.id != station.id }.map {
                                        AreaStation(it.id, it.name, it.dist, it.e5, it.e10, it.diesel, it.isOpen)
                                    },
                                )
                            } ?: m.area?.takeIf { it.stationId == station.id },
                            history = Stats.record(history, PriceObservation(now, fresh.e5, fresh.e10, fresh.diesel)),
                            historyStationId = station.id,
                        )
                    } catch (e: Exception) {
                        errors += "Deutschland: ${errorText(e)}"
                    }
                }
            }

            // Tschechien / Polen: Höchstpreise (heute und morgen) und Kurse
            val tomorrow = today.plusDays(1)
            val hour = LocalTime.now(BERLIN).hour
            val needCz = s.foreign.any { it.country == Country.CZ }
            val needPl = s.foreign.any { it.country == Country.PL }
            val due = force ||
                m.capsDay != today.toString() ||
                now - m.capsFetchedAt > 3_600_000L ||
                (needCz && m.czk == null) ||
                (needPl && m.pln == null)
            if ((needCz || needPl) && due) {
                var allOk = true
                if (needCz) {
                    try {
                        // Die Seite zeigt immer die zuletzt verkündeten Preise (werktags ab 14 Uhr die für morgen).
                        Sources.cz(today)?.let { m = m.copy(czCaps = PriceResolver.merge(m.czCaps, it, today)) }
                    } catch (e: Exception) {
                        allOk = false; errors += "Tschechien, Höchstpreis: ${errorText(e)}"
                    }
                    try {
                        m = m.copy(czk = Sources.czk() ?: m.czk)
                    } catch (e: Exception) {
                        allOk = false; errors += "Kurs CZK: ${errorText(e)}"
                    }
                }
                if (needPl) {
                    try {
                        if (m.capFor(Country.PL, today) == null && (force || m.plMissDay != today.toString())) {
                            val p = Sources.pl(today)
                            m = if (p != null) {
                                m.copy(plCaps = PriceResolver.merge(m.plCaps, p, today))
                            } else {
                                m.copy(plMissDay = today.toString())
                            }
                        }
                        // Die Bekanntmachung für morgen erscheint im Laufe des Tages.
                        if (m.capFor(Country.PL, tomorrow) == null && hour >= 12) {
                            Sources.pl(tomorrow)?.let { m = m.copy(plCaps = PriceResolver.merge(m.plCaps, it, today)) }
                        }
                    } catch (e: Exception) {
                        allOk = false; errors += "Polen, Höchstpreis: ${errorText(e)}"
                    }
                    try {
                        m = m.copy(pln = Sources.pln() ?: m.pln)
                    } catch (e: Exception) {
                        allOk = false; errors += "Kurs PLN: ${errorText(e)}"
                    }
                }
                if (allOk) m = m.copy(capsDay = today.toString(), capsFetchedAt = now)
            }

            // EU-Wochendurchschnitt als Ausweichwert, nur wenn kein Höchstpreis da ist.
            // Die Werte ändern sich wöchentlich: höchstens einmal pro Tag (manuell: pro Stunde).
            val euMaxAge = if (force) 3_600_000L else 24 * 3_600_000L
            var euFetched = false
            var euOk = true
            for (c in Country.entries) {
                val needed = s.foreign.any { it.country == c } &&
                    m.capFor(c, today) == null && m.capFor(c, tomorrow) == null
                if (!needed || (m.eu(c) != null && now - m.euFetchedAt <= euMaxAge)) continue
                euFetched = true
                try {
                    val week = Sources.eu(c)
                    m = when (c) {
                        Country.CZ -> m.copy(euCz = week ?: m.euCz)
                        Country.PL -> m.copy(euPl = week ?: m.euPl)
                    }
                } catch (e: Exception) {
                    euOk = false; errors += "EU-Durchschnitt ${c.label}: ${errorText(e)}"
                }
            }
            if (euFetched && euOk) m = m.copy(euFetchedAt = now)

            m = m.copy(loading = false, errors = errors)
            _market.value = m
            repo.saveMarket(m.copy(errors = emptyList()))
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }

    fun saveForeign(station: ForeignStation) {
        viewModelScope.launch {
            repo.updateSettings { s ->
                val exists = s.foreign.any { it.id == station.id }
                s.copy(
                    foreign = if (exists) {
                        s.foreign.map { if (it.id == station.id) station else it }
                    } else {
                        s.foreign + station
                    },
                )
            }
            refresh(false)
        }
    }

    fun deleteForeign(id: String) {
        viewModelScope.launch {
            repo.updateSettings { s -> s.copy(foreign = s.foreign.filterNot { it.id == id }) }
        }
    }

    fun selectGerman(st: Tankerkoenig.Station) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(german = GermanStation(st.id, st.name, st.address, st.lat, st.lng)) }
            // Preise aus der Suche direkt übernehmen, spart eine Abfrage.
            val fresh = DePrice(
                stationId = st.id,
                e5 = st.e5,
                e10 = st.e10,
                diesel = st.diesel,
                status = if (st.isOpen) "open" else "closed",
                fetchedAt = System.currentTimeMillis(),
            )
            _market.value = _market.value.copy(de = DePrice.merge(_market.value.de, fresh))
            repo.saveMarket(_market.value.copy(loading = false, errors = emptyList()))
        }
    }

    fun searchPlace(query: String) = runSearch {
        Geo.geocode(getApplication<Application>(), query)
            ?: throw IllegalStateException("Ort nicht gefunden. Versuche es mit PLZ oder Ortsnamen.")
    }

    fun searchDeviceLocation() = runSearch {
        Geo.currentLocation(getApplication<Application>())?.let { it.latitude to it.longitude }
            ?: throw IllegalStateException("Standort nicht verfügbar. Ist die Standortermittlung eingeschaltet?")
    }

    fun searchError(message: String) {
        _search.value = SearchState(error = message)
    }

    private fun runSearch(locate: suspend () -> Pair<Double, Double>) {
        viewModelScope.launch {
            val key = repo.settings.first().apiKey
            if (key.isBlank()) {
                _search.value = SearchState(error = "Bitte zuerst den Tankerkönig-Schlüssel eintragen.")
                return@launch
            }
            _search.value = SearchState(loading = true)
            _search.value = try {
                val (lat, lng) = locate()
                val list = Tankerkoenig.list(key, lat, lng, SEARCH_RADIUS_KM)
                SearchState(
                    results = list,
                    error = if (list.isEmpty()) "Keine Tankstellen im Umkreis von 10 km gefunden." else null,
                )
            } catch (e: Exception) {
                SearchState(error = errorText(e))
            }
        }
    }

    // ---- Auslandstankstellen über OpenStreetMap ----

    fun osmSearchAroundGerman() = runOsmSearch {
        germanCoords() ?: throw IllegalStateException(
            "Bitte zuerst in den Einstellungen deine deutsche Tankstelle wählen.",
        )
    }

    fun osmSearchAroundPlace(query: String) = runOsmSearch {
        Osm.place(query) ?: throw IllegalStateException("Ort nicht gefunden.")
    }

    fun clearOsmSearch() {
        osmJob?.cancel()
        _osmSearch.value = OsmSearchState()
    }

    private fun runOsmSearch(center: suspend () -> Pair<Double, Double>) {
        osmJob?.cancel()
        osmJob = viewModelScope.launch {
            _osmSearch.value = OsmSearchState(loading = true)
            _osmSearch.value = try {
                val (lat, lng) = center()
                val list = Osm.fuelNear(lat, lng)
                OsmSearchState(
                    results = list,
                    error = if (list.isEmpty()) {
                        "Keine Tankstellen in Tschechien oder Polen im Umkreis von " +
                            "${Osm.RADIUS_KM.toInt()} km gefunden."
                    } else {
                        null
                    },
                    truncated = list.size >= Osm.LIMIT,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OsmSearchState(error = errorText(e))
            }
        }
    }

    // ---- Autokosten aus dem ADAC-Autokatalog ----

    fun adacOpen() {
        val a = _adac.value
        if (a.step == AdacStep.BRAND && a.brands.isEmpty() && !a.loading) adacRetry()
    }

    fun adacPickBrand(brand: Adac.Entry) = adacLoad(AdacState(brands = _adac.value.brands, brand = brand)) {
        it.copy(models = Adac.models(brand.slug))
    }

    fun adacPickModel(model: Adac.Entry) = adacLoad(_adac.value.copy(model = model, generation = null, variant = null)) {
        val gens = Adac.generations(it.brand!!.slug, model.slug)
        // Nur eine Generation: direkt die Varianten zeigen.
        if (gens.size == 1) {
            it.copy(generations = gens, generation = gens[0], variants = Adac.variants(it.brand.slug, model.slug, gens[0].slug))
        } else {
            it.copy(generations = gens)
        }
    }

    fun adacPickGeneration(gen: Adac.Generation) = adacLoad(_adac.value.copy(generation = gen, variant = null)) {
        it.copy(variants = Adac.variants(it.brand!!.slug, it.model!!.slug, gen.slug))
    }

    fun adacPickVariant(v: Adac.Variant) = adacLoad(_adac.value.copy(variant = v, costs = null)) {
        it.copy(costs = Adac.costs(it.brand!!.slug, it.model!!.slug, it.generation!!.slug, v.id))
    }

    /** Einen Schritt zurück; false, wenn schon bei der Markenauswahl. */
    fun adacBack(): Boolean {
        adacJob?.cancel()
        val a = _adac.value.copy(loading = false, error = null)
        _adac.value = when (a.step) {
            AdacStep.BRAND -> return false
            AdacStep.MODEL -> a.copy(brand = null, models = emptyList())
            AdacStep.GENERATION -> a.copy(model = null, generations = emptyList())
            // Gab es nur eine Generation, geht es direkt zurück zur Modellauswahl.
            AdacStep.VARIANT -> if (a.generations.size <= 1) {
                a.copy(model = null, generation = null, generations = emptyList(), variants = emptyList())
            } else {
                a.copy(generation = null, variants = emptyList())
            }
            AdacStep.COSTS -> a.copy(variant = null, costs = null)
        }
        return true
    }

    fun adacRetry() {
        val a = _adac.value
        when (a.step) {
            AdacStep.BRAND -> adacLoad(a) { it.copy(brands = Adac.brands()) }
            AdacStep.MODEL -> adacPickBrand(a.brand!!)
            AdacStep.GENERATION -> adacPickModel(a.model!!)
            AdacStep.VARIANT -> adacPickGeneration(a.generation!!)
            AdacStep.COSTS -> adacPickVariant(a.variant!!)
        }
    }

    /** Übernimmt die ADAC-Kosten der gewählten Variante; optional auch den ADAC-Verbrauch. */
    fun adacApply(cost: AdacCost, takeConsumption: Boolean) {
        val consumption = _adac.value.costs?.consumption
        updateSettings { s ->
            s.copy(
                adacCost = cost,
                consumption = consumption?.takeIf { takeConsumption && it < 50.0 } ?: s.consumption,
            )
        }
    }

    private fun adacLoad(start: AdacState, load: suspend (AdacState) -> AdacState) {
        adacJob?.cancel()
        _adac.value = start.copy(loading = true, error = null)
        adacJob = viewModelScope.launch {
            _adac.value = try {
                load(_adac.value).copy(loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _adac.value.copy(loading = false, error = errorText(e))
            }
        }
    }

    /**
     * Autostrecke (einfach) von der deutschen Tankstelle zu (lat, lng).
     * Liefert eine Fehlermeldung statt einer Zahl, wenn das nicht geht.
     */
    suspend fun routeKmFromGerman(lat: Double, lng: Double): Result<Double> = try {
        val (fromLat, fromLng) = germanCoords()
            ?: throw IllegalStateException("Keine deutsche Tankstelle gewählt.")
        val km = Osm.routeKm(fromLat, fromLng, lat, lng)
            ?: throw IllegalStateException("Keine Autoroute gefunden.")
        Result.success(km)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(IllegalStateException(errorText(e)))
    }

    /** Koordinaten der deutschen Tankstelle; ältere Einträge ohne Koordinaten werden einmal nachgeladen. */
    private suspend fun germanCoords(): Pair<Double, Double>? {
        val s = repo.settings.first()
        val g = s.german ?: return null
        if (g.lat != null && g.lng != null) return g.lat to g.lng
        if (s.apiKey.isBlank()) return null
        val c = Tankerkoenig.coords(s.apiKey, g.id) ?: return null
        repo.updateSettings { cur ->
            if (cur.german?.id == g.id) cur.copy(german = cur.german.copy(lat = c.first, lng = c.second)) else cur
        }
        return c
    }

    companion object {
        private val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
        private const val SEARCH_RADIUS_KM = 10.0
        private const val AREA_RADIUS_KM = 5.0
    }
}
