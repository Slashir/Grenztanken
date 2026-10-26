package de.grenztanken.app.data

import de.grenztanken.app.model.Country
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tankstellensuche über OpenStreetMap (Nominatim) und Streckenberechnung über OSRM.
 * Daten © OpenStreetMap-Mitwirkende, ODbL. Beide Dienste sind öffentliche Server mit Nutzungsregeln:
 * Nominatim höchstens 1 Anfrage pro Sekunde, nur auf Nutzeraktion, keine Vorschläge beim Tippen.
 */
object Osm {
    private const val NOMINATIM = "https://nominatim.openstreetmap.org/search"
    private const val OSRM = "https://router.project-osrm.org/route/v1/driving/"

    /** Suchradius um den Mittelpunkt; Nominatim liefert höchstens [LIMIT] Treffer. */
    const val RADIUS_KM = 15.0
    const val LIMIT = 40

    data class FuelStation(
        val osmId: String,
        val name: String,
        val address: String,
        val country: Country,
        val lat: Double,
        val lng: Double,
        /** Luftlinie zum Suchmittelpunkt in km. */
        val distKm: Double,
    )

    /** Tankstellen in Tschechien und Polen im Umkreis von [radiusKm] um (lat, lng), nach Luftlinie sortiert. */
    suspend fun fuelNear(lat: Double, lng: Double, radiusKm: Double = RADIUS_KM): List<FuelStation> {
        val dLat = radiusKm / 111.0
        val dLng = radiusKm / (111.0 * cos(Math.toRadians(lat)))
        val viewbox = listOf(lng - dLng, lat + dLat, lng + dLng, lat - dLat).joinToString(",") { fmt(it) }
        val url = "$NOMINATIM?amenity=fuel&viewbox=$viewbox&bounded=1&countrycodes=cz,pl" +
            "&addressdetails=1&extratags=1&format=jsonv2&limit=$LIMIT"
        return parseFuel(nominatim(url), lat, lng)
    }

    /** Ort in Deutschland, Tschechien oder Polen → (lat, lng); null, wenn nichts gefunden. */
    suspend fun place(query: String): Pair<Double, Double>? {
        val url = "$NOMINATIM?q=${URLEncoder.encode(query.trim(), "UTF-8")}" +
            "&countrycodes=de,cz,pl&format=jsonv2&limit=1"
        return parsePlace(nominatim(url))
    }

    /** Autostrecke in km von A nach B; null, wenn OSRM keine Route findet. */
    suspend fun routeKm(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Double? {
        val url = "$OSRM${fmt(fromLng)},${fmt(fromLat)};${fmt(toLng)},${fmt(toLat)}?overview=false"
        val (code, body) = Http.get(url)
        if (code != 200 && code != 400) throw IOException("Routenplaner: HTTP $code")
        return parseRouteKm(body)
    }

    // Nominatim erlaubt höchstens eine Anfrage pro Sekunde.
    private val lock = Mutex()
    private var lastCall = 0L

    private suspend fun nominatim(url: String): String = lock.withLock {
        val wait = 1_100L - (System.currentTimeMillis() - lastCall)
        if (wait > 0) delay(wait)
        try {
            val (code, body) = Http.get(url)
            if (code != 200) throw IOException("OpenStreetMap-Suche: HTTP $code")
            body
        } finally {
            lastCall = System.currentTimeMillis()
        }
    }

    // ---- Auswertung (ohne Netz, getestet) ----

    fun parseFuel(json: String, centerLat: Double, centerLng: Double): List<FuelStation> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val a = o.optJSONObject("address") ?: JSONObject()
            val country = when (a.optString("country_code")) {
                "cz" -> Country.CZ
                "pl" -> Country.PL
                else -> return@mapNotNull null
            }
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lng = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            // Außerorts steht als Straße oft nur die Straßennummer, z. B. "26510" oder "13;35".
            val roadName = a.text("road").let { if (it.matches(ROAD_NUMBER)) "Straße ${it.replace(";", "/")}" else it }
            val road = listOf(roadName, a.text("house_number")).filter { it.isNotEmpty() }.joinToString(" ")
            val town = listOf("city", "town", "village", "municipality", "hamlet", "suburb")
                .map { a.text(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()
            val place = listOf(a.text("postcode"), town).filter { it.isNotEmpty() }.joinToString(" ")
            FuelStation(
                osmId = o.optString("osm_type") + "/" + o.optString("osm_id"),
                name = displayName(o.text("name"), o.optJSONObject("extratags")?.text("brand").orEmpty(), road),
                address = listOf(road, place).filter { it.isNotEmpty() }.joinToString(", "),
                country = country,
                lat = lat,
                lng = lng,
                distKm = distanceKm(centerLat, centerLng, lat, lng),
            )
        }.distinctBy { it.osmId }.sortedBy { it.distKm }
    }

    fun parsePlace(json: String): Pair<Double, Double>? {
        val o = JSONArray(json).optJSONObject(0) ?: return null
        val lat = o.optString("lat").toDoubleOrNull() ?: return null
        val lng = o.optString("lon").toDoubleOrNull() ?: return null
        return lat to lng
    }

    /** OSRM: {"code":"Ok","routes":[{"distance":2874.3,...}]}; Strecke in Metern. */
    fun parseRouteKm(json: String): Double? {
        val o = JSONObject(json)
        if (o.optString("code") != "Ok") return null
        val meters = o.optJSONArray("routes")?.optJSONObject(0)?.optDouble("distance") ?: return null
        return if (meters.isNaN() || meters < 0) null else meters / 1000.0
    }

    /** Name, sonst Marke, sonst "Tankstelle" mit Straße. */
    fun displayName(name: String, brand: String, road: String): String = when {
        name.isNotEmpty() && (brand.isEmpty() || name.contains(brand, ignoreCase = true)) -> name
        name.isNotEmpty() -> "$brand – $name"
        brand.isNotEmpty() -> brand
        road.isNotEmpty() -> "Tankstelle $road"
        else -> "Tankstelle"
    }

    /** Luftlinie in km (Haversine). */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(h))
    }

    private val ROAD_NUMBER = Regex("[0-9][0-9;/ ]*")

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun JSONObject.text(key: String): String =
        optString(key, "").trim().takeIf { it != "null" }.orEmpty()
}
