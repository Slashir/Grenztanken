package de.grenztanken.app.logic

import de.grenztanken.app.model.CapPeriod
import de.grenztanken.app.model.ForeignStation
import de.grenztanken.app.model.Fuel
import de.grenztanken.app.model.Market
import java.time.LocalDate

enum class PriceSource { MANUAL, CAP_TODAY, CAP_TOMORROW, EU_WEEKLY }

/**
 * Preis, mit dem gerechnet wird.
 * [price] in Landeswährung (bzw. EUR bei [PriceSource.EU_WEEKLY]),
 * [perEur] Landeswährung pro Euro (1.0 bei EU-Werten), null wenn der Kurs fehlt.
 */
data class ForeignPrice(
    val price: Double,
    val perEur: Double?,
    val source: PriceSource,
    val period: CapPeriod? = null,
    val euWeek: LocalDate? = null,
)

/** Hinweis auf den bereits verkündeten Höchstpreis für morgen, in Landeswährung. */
data class TomorrowHint(val price: Double, val diff: Double)

object PriceResolver {

    /** Reihenfolge: Preis an der Säule > Höchstpreis heute > Höchstpreis ab morgen > EU-Wochendurchschnitt. */
    fun resolve(station: ForeignStation, fuel: Fuel, market: Market, today: LocalDate): ForeignPrice? {
        val rate = market.rate(station.country)?.perEur
        station.manualFor(fuel)?.let { return ForeignPrice(it, rate, PriceSource.MANUAL) }
        market.capFor(station.country, today)?.let {
            return ForeignPrice(it.priceFor(fuel), rate, PriceSource.CAP_TODAY, period = it)
        }
        market.capFor(station.country, today.plusDays(1))?.let {
            return ForeignPrice(it.priceFor(fuel), rate, PriceSource.CAP_TOMORROW, period = it)
        }
        market.eu(station.country)?.let {
            return ForeignPrice(it.priceFor(fuel), 1.0, PriceSource.EU_WEEKLY, euWeek = it.week)
        }
        return null
    }

    /**
     * Ist der Höchstpreis für morgen schon bekannt und anders als heute, wird er gemeldet.
     * Nur sinnvoll, wenn heute ebenfalls mit dem Höchstpreis gerechnet wird.
     */
    fun tomorrow(station: ForeignStation, fuel: Fuel, market: Market, today: LocalDate): TomorrowHint? {
        val todayCap = market.capFor(station.country, today) ?: return null
        val nextCap = market.capFor(station.country, today.plusDays(1)) ?: return null
        if (nextCap == todayCap) return null
        val diff = nextCap.priceFor(fuel) - todayCap.priceFor(fuel)
        return TomorrowHint(nextCap.priceFor(fuel), diff)
    }

    /** Neuen Zeitraum in den Verlauf übernehmen; Altes und Doppeltes fliegt raus. */
    fun merge(list: List<CapPeriod>, period: CapPeriod, today: LocalDate): List<CapPeriod> =
        (list.filterNot { it.from == period.from && it.to == period.to } + period)
            .filter { !it.to.isBefore(today.minusDays(1)) }
            .sortedBy { it.from }
            .takeLast(6)
}
