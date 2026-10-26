package de.grenztanken.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Freie Spritpreis-API von Tankerkönig (Daten der MTS-K, Lizenz CC BY 4.0).
 * Jeder Nutzer braucht einen eigenen, persönlichen API-Key.
 * Doku: https://creativecommons.tankerkoenig.de
 */
object Tankerkoenig {
    private const val BASE = "https://creativecommons.tankerkoenig.de/json"

    data class Station(
        val id: String,
        val name: String,
        val address: String,
        val dist: Double?,
        val e5: Double?,
        val e10: Double?,
        val diesel: Double?,
        val isOpen: Boolean,
        val lat: Double?,
        val lng: Double?,
    )

    data class PriceEntry(
        val status: String,
        val e5: Double?,
        val e10: Double?,
        val diesel: Double?,
    )

    /** Umkreissuche, Radius max. 25 km. Sortiert nach Entfernung, ohne Filter. */
    suspend fun list(apiKey: String, lat: Double, lng: Double, radKm: Double): List<Station> {
        val url = "$BASE/list.php?lat=$lat&lng=$lng&rad=${radKm.coerceIn(1.0, 25.0)}" +
            "&sort=dist&type=all&apikey=${enc(apiKey)}"
        val o = request(url)
        val arr = o.optJSONArray("stations") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            Station(
                id = s.getString("id"),
                name = displayName(s),
                address = address(s),
                dist = (s.opt("dist") as? Number)?.toDouble(),
                e5 = s.price("e5"),
                e10 = s.price("e10"),
                diesel = s.price("diesel"),
                isOpen = s.optBoolean("isOpen", false),
                lat = s.coord("lat"),
                lng = s.coord("lng"),
            )
        }
    }

    /** Koordinaten einer Tankstelle (für Tankstellen, die vor der Streckenberechnung gespeichert wurden). */
    suspend fun coords(apiKey: String, id: String): Pair<Double, Double>? {
        val o = request("$BASE/detail.php?id=${enc(id)}&apikey=${enc(apiKey)}")
        val s = o.optJSONObject("station") ?: return null
        val lat = s.coord("lat") ?: return null
        val lng = s.coord("lng") ?: return null
        return lat to lng
    }

    /** Preisabfrage für bis zu 10 Tankstellen. */
    suspend fun prices(apiKey: String, ids: List<String>): Map<String, PriceEntry> {
        require(ids.size in 1..10) { "1 bis 10 Tankstellen pro Abfrage" }
        val url = "$BASE/prices.php?ids=${ids.joinToString(",") { enc(it) }}&apikey=${enc(apiKey)}"
        val o = request(url)
        val prices = o.optJSONObject("prices") ?: JSONObject()
        return ids.associateWith { id ->
            val p = prices.optJSONObject(id)
            PriceEntry(
                status = p?.optString("status", "no prices") ?: "no prices",
                e5 = p?.price("e5"),
                e10 = p?.price("e10"),
                diesel = p?.price("diesel"),
            )
        }
    }

    private suspend fun request(url: String): JSONObject {
        val (code, body) = Http.get(url)
        val o = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw IOException("Tankerkönig: unerwartete Antwort (HTTP $code)")
        }
        if (!o.optBoolean("ok", false)) {
            throw IOException("Tankerkönig: " + o.optString("message", "Fehler (HTTP $code)"))
        }
        return o
    }

    /** Preisfelder sind Zahlen oder `false`, wenn die Sorte nicht geführt wird. */
    private fun JSONObject.price(key: String): Double? =
        (opt(key) as? Number)?.toDouble()?.takeIf { it > 0.0 }

    private fun JSONObject.coord(key: String): Double? =
        (opt(key) as? Number)?.toDouble()?.takeIf { it != 0.0 }

    private fun JSONObject.text(key: String): String =
        optString(key, "").trim().takeIf { it != "null" }.orEmpty()

    private fun displayName(s: JSONObject): String {
        val name = s.text("name")
        val brand = s.text("brand")
        return when {
            name.isEmpty() -> brand.ifEmpty { "Tankstelle" }
            brand.isEmpty() || name.contains(brand, ignoreCase = true) -> name
            else -> "$brand – $name"
        }
    }

    private fun address(s: JSONObject): String {
        val street = listOf(s.text("street"), s.text("houseNumber"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        var postCode = s.opt("postCode")?.toString()?.trim().orEmpty()
        if (postCode == "null") postCode = ""
        if (postCode.length in 1..4 && postCode.all { it.isDigit() }) postCode = postCode.padStart(5, '0')
        val place = listOf(postCode, s.text("place")).filter { it.isNotEmpty() }.joinToString(" ")
        return listOf(street, place).filter { it.isNotEmpty() }.joinToString(", ")
    }

    private fun enc(s: String): String = URLEncoder.encode(s.trim(), "UTF-8")
}
