package de.grenztanken.app.logic

import de.grenztanken.app.model.Area
import de.grenztanken.app.model.AreaStation
import de.grenztanken.app.model.Fuel
import de.grenztanken.app.model.PriceObservation

/** Vergleich mit den Tankstellen im Umkreis: nur geöffnete mit Preis für die gewählte Sorte. */
data class AreaSummary(
    val count: Int,
    val radiusKm: Double,
    val average: Double,
    val cheapest: AreaStation,
    val cheapestPrice: Double,
)

/** Zeitgewichteter Durchschnitt aus mitgeschriebenen Preisen. */
data class HistoryAverage(val average: Double, val observations: Int, val min: Double, val max: Double)

object Stats {
    const val HISTORY_KEEP_MS = 14L * 24 * 3_600_000L

    /** Höchstens so lange gilt ein mitgeschriebener Preis, wenn danach keiner mehr abgerufen wurde. */
    const val MAX_GAP_MS = 2L * 3_600_000L

    fun area(area: Area, fuel: Fuel): AreaSummary? {
        val priced = area.stations.mapNotNull { st ->
            st.priceFor(fuel)?.takeIf { st.isOpen }?.let { st to it }
        }
        if (priced.isEmpty()) return null
        val (cheapest, price) = priced.minBy { it.second }
        return AreaSummary(
            count = priced.size,
            radiusKm = area.radiusKm,
            average = priced.sumOf { it.second } / priced.size,
            cheapest = cheapest,
            cheapestPrice = price,
        )
    }

    /**
     * Neuen Abruf anhängen, Einträge älter als [HISTORY_KEEP_MS] verwerfen.
     * Abrufe ohne Preis (z. B. geschlossen) werden nicht mitgeschrieben.
     */
    fun record(history: List<PriceObservation>, obs: PriceObservation): List<PriceObservation> {
        if (obs.e5 == null && obs.e10 == null && obs.diesel == null) return history
        val kept = history.filter { it.at > obs.at - HISTORY_KEEP_MS && it.at < obs.at }
        return kept + obs
    }

    /**
     * Durchschnitt über [from, to): Jeder Preis gilt bis zum nächsten Abruf, höchstens [MAX_GAP_MS].
     * Ein einzelner Abruf ohne Dauer zählt mit seinem Wert. null, wenn es im Zeitraum keinen Preis gibt.
     */
    fun timeWeighted(
        history: List<PriceObservation>,
        fuel: Fuel,
        from: Long,
        to: Long,
        maxGapMs: Long = MAX_GAP_MS,
    ): HistoryAverage? {
        val points = history
            .filter { it.at in from until to }
            .mapNotNull { o -> o.priceFor(fuel)?.let { o.at to it } }
            .sortedBy { it.first }
        if (points.isEmpty()) return null
        var sum = 0.0
        var weight = 0.0
        points.forEachIndexed { i, (at, price) ->
            val next = points.getOrNull(i + 1)?.first ?: to
            val w = (minOf(next, at + maxGapMs, to) - at).coerceAtLeast(0L).toDouble()
            sum += price * w
            weight += w
        }
        val prices = points.map { it.second }
        val avg = if (weight > 0.0) sum / weight else prices.average()
        return HistoryAverage(avg, points.size, prices.min(), prices.max())
    }
}
