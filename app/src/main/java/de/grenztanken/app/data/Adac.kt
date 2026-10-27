package de.grenztanken.app.data

import de.grenztanken.app.model.AdacPoint
import org.json.JSONObject
import java.io.IOException

/**
 * Autokosten aus dem ADAC-Autokatalog (adac.de/rund-ums-fahrzeug/autokatalog/marken-modelle/).
 * Eine offizielle Schnittstelle gibt es nicht. Jede Katalogseite enthält ihre Daten aber als JSON
 * (window.__APOLLO_STATE__), das liest die App aus. Abgerufen wird nur auf Nutzeraktion, eine Seite pro Schritt.
 * Neuere Varianten haben Werte für 2–5 Jahre Haltedauer und 5.000–60.000 km pro Jahr, ältere nur
 * für 5 Jahre und 15.000 km.
 */
object Adac {
    private const val BASE = "https://www.adac.de/rund-ums-fahrzeug/autokatalog/marken-modelle/"

    data class Entry(val slug: String, val name: String)

    data class Generation(
        val slug: String,
        val name: String,
        val facelift: Boolean,
        val from: Int?,
        val until: Int?,
    ) {
        val years: String
            get() = when {
                from == null -> ""
                until == null -> "seit $from"
                else -> "$from–$until"
            }
    }

    data class Variant(val id: String, val name: String, val fuel: String, val hp: String)

    /** ADAC-Kostenrechnung einer Variante: alle Kombinationen aus Haltedauer und Fahrleistung. */
    data class Costs(
        val points: List<AdacPoint>,
        /** Verbrauch laut ADAC in l/100 km (bei Elektroautos kWh/100 km). */
        val consumption: Double?,
    )

    suspend fun brands(): List<Entry> = parseBrands(state(BASE))
    suspend fun models(brand: String): List<Entry> = parseModels(state("$BASE$brand/"))
    suspend fun generations(brand: String, model: String): List<Generation> =
        parseGenerations(state("$BASE$brand/$model/"))
    suspend fun variants(brand: String, model: String, generation: String): List<Variant> =
        parseVariants(state("$BASE$brand/$model/$generation/"))

    /** null, wenn der ADAC für diese Variante (noch) keine Kostenrechnung hat. */
    suspend fun costs(brand: String, model: String, generation: String, id: String): Costs? =
        parseCosts(state(pageUrl(brand, model, generation, id)))

    fun pageUrl(brand: String, model: String, generation: String, id: String) = "$BASE$brand/$model/$generation/$id/"

    private suspend fun state(url: String): JSONObject {
        val (code, body) = Http.get(url)
        if (code != 200) throw IOException("ADAC: HTTP $code")
        return extractState(body) ?: throw IOException("ADAC-Seite hat ein unbekanntes Format")
    }

    // ---- Auswertung (ohne Netz, getestet) ----

    fun extractState(html: String): JSONObject? {
        val marker = "window.__APOLLO_STATE__="
        val start = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
        val end = html.indexOf("</script>", start).takeIf { it >= 0 } ?: return null
        return runCatching { JSONObject(html.substring(start, end).trim().removeSuffix(";")) }.getOrNull()
    }

    fun parseBrands(state: JSONObject): List<Entry> = entries(state, "ApilBrandCollectionItem:")

    fun parseModels(state: JSONObject): List<Entry> = entries(state, "ApilRangeCollectionItem:")

    /** In der Reihenfolge der Modellseite, also die neueste Generation zuerst. */
    fun parseGenerations(state: JSONObject): List<Generation> {
        val refs = objects(state, "ApilRange:").firstOrNull()?.optJSONArray("generations")
        val items = if (refs != null) {
            (0 until refs.length()).mapNotNull { i -> resolve(state, refs.optJSONObject(i)) }
        } else {
            objects(state, "ApilGenerationCollectionItem:")
        }
        return items.map {
            Generation(
                slug = it.optString("slug"),
                name = it.optString("name"),
                facelift = it.optInt("facelift", 0) > 0,
                from = it.intOrNull("manufacturedFrom"),
                until = it.intOrNull("manufacturedUntil"),
            )
        }.filter { it.slug.isNotEmpty() }
    }

    fun parseVariants(state: JSONObject): List<Variant> =
        objects(state, "ApilCarCollectionItem:")
            .sortedBy { it.optInt("sorting", Int.MAX_VALUE) }
            .map { Variant(it.optString("id"), it.optString("name"), it.text("fuelType"), it.text("powerInHP")) }
            .filter { it.id.isNotEmpty() }

    fun parseCosts(state: JSONObject): Costs? {
        val root = state.optJSONObject("ROOT_QUERY") ?: return null
        val page = root.keys().asSequence().filter { it.startsWith("carPage") }
            .firstNotNullOfOrNull { root.optJSONObject(it)?.optJSONObject("carPage") } ?: return null
        val list = page.optJSONObject("costs")?.optJSONArray("costs") ?: return null
        val entries = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
        val points = entries.mapNotNull { c ->
            val months = c.optInt("months", 0)
            val km = c.optInt("kilometers", 0)
            val workshop = c.number("workshop") ?: return@mapNotNull null
            if (months <= 0 || km <= 0) return@mapNotNull null
            AdacPoint(months, km, workshop, c.number("deterioration") ?: 0.0)
        }.distinctBy { it.months to it.kmPerYear }
        if (points.isEmpty()) return null
        return Costs(
            points = points,
            consumption = entries.firstNotNullOfOrNull { it.number("energyConsumption") }?.takeIf { it > 0.0 },
        )
    }

    private fun entries(state: JSONObject, prefix: String): List<Entry> =
        objects(state, prefix).map { Entry(it.optString("slug"), it.optString("name")) }
            .filter { it.slug.isNotEmpty() && it.name.isNotEmpty() }
            .distinctBy { it.slug }
            .sortedBy { it.name.lowercase() }

    private fun objects(state: JSONObject, prefix: String): List<JSONObject> =
        state.keys().asSequence().filter { it.startsWith(prefix) }.mapNotNull { state.optJSONObject(it) }.toList()

    private fun resolve(state: JSONObject, ref: JSONObject?): JSONObject? =
        ref?.optString("__ref")?.takeIf { it.isNotEmpty() }?.let { state.optJSONObject(it) } ?: ref

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key, 0).takeIf { it > 0 }

    private fun JSONObject.number(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "").trim()
}
