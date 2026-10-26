package de.grenztanken.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.grenztanken.app.model.CapPeriod
import de.grenztanken.app.model.Country
import de.grenztanken.app.model.DePrice
import de.grenztanken.app.model.EuWeekly
import de.grenztanken.app.model.ForeignStation
import de.grenztanken.app.model.Fuel
import de.grenztanken.app.model.GermanStation
import de.grenztanken.app.model.Market
import de.grenztanken.app.model.RateInfo
import de.grenztanken.app.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "grenztanken")

/** Speichert Einstellungen und den letzten Datenstand lokal auf dem Gerät. */
class Repo(private val context: Context) {
    private val keySettings = stringPreferencesKey("settings")
    private val keyMarket = stringPreferencesKey("market")

    private val prefs: Flow<Preferences> = context.dataStore.data.catch { emit(emptyPreferences()) }

    val settings: Flow<Settings> = prefs.map { p -> readSettings(p) }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            p[keySettings] = JsonCodec.settingsToJson(transform(readSettings(p)))
        }
    }

    suspend fun loadMarket(): Market =
        prefs.first()[keyMarket]?.let { runCatching { JsonCodec.marketFromJson(it) }.getOrNull() } ?: Market()

    suspend fun saveMarket(market: Market) {
        context.dataStore.edit { it[keyMarket] = JsonCodec.marketToJson(market) }
    }

    private fun readSettings(p: Preferences): Settings =
        p[keySettings]?.let { runCatching { JsonCodec.settingsFromJson(it) }.getOrNull() } ?: Settings()
}

object JsonCodec {

    fun settingsToJson(s: Settings): String = JSONObject().apply {
        put("apiKey", s.apiKey)
        put("fuel", s.fuel.name)
        put("consumption", s.consumption)
        put("liters", s.liters)
        s.german?.let {
            put("german", JSONObject().put("id", it.id).put("name", it.name).put("address", it.address))
        }
        val foreign = JSONArray()
        s.foreign.forEach { f ->
            foreign.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("country", f.country.name)
                    .put("oneWayKm", f.oneWayKm)
                    .putOpt("manualPetrol", f.manualPetrol)
                    .putOpt("manualDiesel", f.manualDiesel),
            )
        }
        put("foreign", foreign)
    }.toString()

    fun settingsFromJson(text: String): Settings {
        val o = JSONObject(text)
        val foreign = o.optJSONArray("foreign")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                runCatching {
                    val f = a.getJSONObject(i)
                    ForeignStation(
                        id = f.getString("id"),
                        name = f.optString("name"),
                        country = Country.valueOf(f.getString("country")),
                        oneWayKm = f.optDouble("oneWayKm", 0.0),
                        manualPetrol = f.dbl("manualPetrol"),
                        manualDiesel = f.dbl("manualDiesel"),
                    )
                }.getOrNull()
            }
        } ?: emptyList()
        return Settings(
            apiKey = o.optString("apiKey", ""),
            fuel = runCatching { Fuel.valueOf(o.optString("fuel")) }.getOrDefault(Fuel.E10),
            consumption = o.optDouble("consumption", 7.0),
            liters = o.optDouble("liters", 50.0),
            german = o.optJSONObject("german")?.let {
                GermanStation(it.getString("id"), it.optString("name"), it.optString("address"))
            },
            foreign = foreign,
        )
    }

    fun marketToJson(m: Market): String = JSONObject().apply {
        m.de?.let {
            put(
                "de",
                JSONObject()
                    .put("stationId", it.stationId)
                    .putOpt("e5", it.e5)
                    .putOpt("e10", it.e10)
                    .putOpt("diesel", it.diesel)
                    .put("status", it.status)
                    .put("fetchedAt", it.fetchedAt),
            )
        }
        put("czCaps", capsToJson(m.czCaps))
        put("plCaps", capsToJson(m.plCaps))
        m.czk?.let { put("czk", rateToJson(it)) }
        m.pln?.let { put("pln", rateToJson(it)) }
        m.euCz?.let { put("euCz", euToJson(it)) }
        m.euPl?.let { put("euPl", euToJson(it)) }
        put("capsDay", m.capsDay)
        put("capsFetchedAt", m.capsFetchedAt)
        put("euFetchedAt", m.euFetchedAt)
        put("plMissDay", m.plMissDay)
    }.toString()

    fun marketFromJson(text: String): Market {
        val o = JSONObject(text)
        return Market(
            de = o.optJSONObject("de")?.let {
                DePrice(
                    stationId = it.getString("stationId"),
                    e5 = it.dbl("e5"),
                    e10 = it.dbl("e10"),
                    diesel = it.dbl("diesel"),
                    status = it.optString("status"),
                    fetchedAt = it.optLong("fetchedAt"),
                )
            },
            czCaps = capsFromJson(o.optJSONArray("czCaps")),
            plCaps = capsFromJson(o.optJSONArray("plCaps")),
            czk = o.optJSONObject("czk")?.let { rateFromJson(it) },
            pln = o.optJSONObject("pln")?.let { rateFromJson(it) },
            euCz = o.optJSONObject("euCz")?.let { euFromJson(it) },
            euPl = o.optJSONObject("euPl")?.let { euFromJson(it) },
            capsDay = o.optString("capsDay"),
            capsFetchedAt = o.optLong("capsFetchedAt"),
            euFetchedAt = o.optLong("euFetchedAt"),
            plMissDay = o.optString("plMissDay"),
        )
    }

    private fun capsToJson(list: List<CapPeriod>) = JSONArray().apply {
        list.forEach {
            put(
                JSONObject()
                    .put("from", it.from.toString())
                    .put("to", it.to.toString())
                    .put("petrol", it.petrol)
                    .put("diesel", it.diesel),
            )
        }
    }

    private fun capsFromJson(a: JSONArray?): List<CapPeriod> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                CapPeriod(
                    from = LocalDate.parse(o.getString("from")),
                    to = LocalDate.parse(o.getString("to")),
                    petrol = o.getDouble("petrol"),
                    diesel = o.getDouble("diesel"),
                )
            }.getOrNull()
        }
    }

    private fun euToJson(e: EuWeekly) = JSONObject()
        .put("e95", e.e95)
        .put("diesel", e.diesel)
        .put("week", e.week.toString())

    private fun euFromJson(o: JSONObject): EuWeekly? = runCatching {
        EuWeekly(o.getDouble("e95"), o.getDouble("diesel"), LocalDate.parse(o.getString("week")))
    }.getOrNull()

    private fun rateToJson(r: RateInfo) = JSONObject().put("perEur", r.perEur).put("date", r.date)

    private fun rateFromJson(o: JSONObject) = RateInfo(o.getDouble("perEur"), o.optString("date"))

    private fun JSONObject.dbl(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
}
