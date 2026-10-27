package de.grenztanken.app.model

import java.time.LocalDate
import kotlin.math.abs

enum class Fuel(val apiKey: String, val label: String) {
    E5("e5", "Super E5"),
    E10("e10", "Super E10"),
    DIESEL("diesel", "Diesel");

    val isDiesel: Boolean get() = this == DIESEL
}

enum class Country(val code: String, val label: String, val currencySymbol: String, val flag: String) {
    CZ("CZ", "Tschechien", "Kč", "🇨🇿"),
    PL("PL", "Polen", "zł", "🇵🇱"),
}

data class GermanStation(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null,
)

/**
 * Eine Tankstelle im Ausland, frei angelegt oder aus der OpenStreetMap-Suche übernommen.
 * [oneWayKm] ist der zusätzliche Weg (einfache Strecke) gegenüber der deutschen Tankstelle.
 * Manuelle Preise sind in Landeswährung pro Liter; Benzin gilt für E5 und E10.
 * [lat]/[lng] und [address] gibt es nur bei Tankstellen aus der Suche.
 */
data class ForeignStation(
    val id: String,
    val name: String,
    val country: Country,
    val oneWayKm: Double,
    val manualPetrol: Double? = null,
    val manualDiesel: Double? = null,
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
) {
    fun manualFor(fuel: Fuel): Double? = if (fuel.isDiesel) manualDiesel else manualPetrol
}

/** Ein Wert der ADAC-Kostenrechnung: Haltedauer, Fahrleistung pro Jahr, Kosten in Euro pro Monat. */
data class AdacPoint(val months: Int, val kmPerYear: Int, val workshop: Double, val deterioration: Double)

/**
 * Aus dem ADAC-Autokatalog übernommene Kosten einer Fahrzeugvariante.
 *
 * Für den Umweg zählt, was jeder zusätzliche km kostet, nicht der Durchschnitt aller km (Werkstatt-
 * und vor allem Wertverlust-Kosten fallen zu einem großen Teil auch ohne Fahren an). Hat der ADAC Werte
 * für mehrere Fahrleistungen, vergleicht die App deshalb die Kosten bei der eigenen Fahrleistung mit
 * [STEP_KM] mehr und weniger. Kennt der ADAC nur eine Fahrleistung (ältere Varianten), bleibt nur der
 * Durchschnitt der Werkstattkosten pro km.
 */
data class AdacCost(
    val car: String,
    val url: String,
    val points: List<AdacPoint>,
    /** Haltedauer bzw. Leasinglaufzeit in Monaten. */
    val months: Int = DEFAULT_MONTHS,
    val withDeterioration: Boolean = false,
) {
    val holdings: List<Int> get() = points.map { it.months }.distinct().sorted()

    /** Haltedauer, mit der gerechnet wird: die gewählte oder die nächstliegende vorhandene. */
    val usedMonths: Int get() = holdings.minByOrNull { abs(it - months) } ?: months

    private val row: List<AdacPoint>
        get() = points.filter { it.months == usedMonths }.sortedBy { it.kmPerYear }

    /** Gibt es Werte für mehrere Fahrleistungen? Nur dann lassen sich Zusatzkosten pro km bestimmen. */
    val hasMileageSteps: Boolean get() = row.size >= 2

    val minKm: Int get() = row.firstOrNull()?.kmPerYear ?: 0
    val maxKm: Int get() = row.lastOrNull()?.kmPerYear ?: 0

    fun workshopPerMonth(kmPerYear: Int): Double = monthly(kmPerYear) { it.workshop }
    fun deteriorationPerMonth(kmPerYear: Int): Double = monthly(kmPerYear) { it.deterioration }

    /** Was ein zusätzlicher km an Werkstatt und Reifen kostet, in Cent. */
    fun workshopCentPerKm(kmPerYear: Int): Double =
        if (hasMileageSteps) marginal(kmPerYear) { it.workshop } else average(kmPerYear) { it.workshop }

    /** Was ein zusätzlicher km an Wertverlust kostet, in Cent; 0 ohne Werte für mehrere Fahrleistungen. */
    fun deteriorationCentPerKm(kmPerYear: Int): Double =
        if (hasMileageSteps) marginal(kmPerYear) { it.deterioration } else 0.0

    /** Zusatzkosten pro km, wie sie in die Rechnung eingehen. */
    fun centPerKm(kmPerYear: Int): Double =
        workshopCentPerKm(kmPerYear) + if (withDeterioration) deteriorationCentPerKm(kmPerYear) else 0.0

    /** Durchschnitt pro km (Monatskosten × 12 ÷ km pro Jahr), nur zur Einordnung. */
    fun averageCentPerKm(kmPerYear: Int): Double =
        average(kmPerYear) { it.workshop } + if (withDeterioration) average(kmPerYear) { it.deterioration } else 0.0

    private fun average(km: Int, f: (AdacPoint) -> Double): Double =
        if (km <= 0) 0.0 else monthly(km, f) * 12.0 * 100.0 / km

    /** Monatskosten bei [km]: linear zwischen den ADAC-Stufen, außerhalb mit der Steigung der Randstufe. */
    private fun monthly(km: Int, f: (AdacPoint) -> Double): Double {
        val r = row
        if (r.isEmpty()) return 0.0
        if (r.size == 1) return f(r[0]) * km / r[0].kmPerYear
        val i = (1 until r.size).firstOrNull { km <= r[it].kmPerYear } ?: (r.size - 1)
        val a = r[i - 1]
        val b = r[i]
        val v = f(a) + (f(b) - f(a)) * (km - a.kmPerYear) / (b.kmPerYear - a.kmPerYear).toDouble()
        return v.coerceAtLeast(0.0)
    }

    /** Steigung der Monatskosten zwischen km − [STEP_KM] und km + [STEP_KM], innerhalb der ADAC-Werte. */
    private fun marginal(km: Int, f: (AdacPoint) -> Double): Double {
        val lo0 = minKm
        val hi0 = maxKm
        val center = if (hi0 - lo0 >= 2 * STEP_KM) km.coerceIn(lo0 + STEP_KM, hi0 - STEP_KM) else (lo0 + hi0) / 2
        val lo = (center - STEP_KM).coerceAtLeast(lo0)
        val hi = (center + STEP_KM).coerceAtMost(hi0)
        if (hi <= lo) return 0.0
        val slope = (monthly(hi, f) - monthly(lo, f)) / (hi - lo)
        return (slope * 12.0 * 100.0).coerceAtLeast(0.0)
    }

    companion object {
        /** ADAC-Standard: 5 Jahre. */
        const val DEFAULT_MONTHS = 60
        const val STEP_KM = 5_000
    }
}

data class Settings(
    val apiKey: String = "",
    val fuel: Fuel = Fuel.E10,
    val consumption: Double = 7.0,
    val liters: Double = 50.0,
    /** Selbst eingetragener Verschleiß in Cent pro km; gilt, solange keine ADAC-Kosten übernommen sind. 0 = nur Sprit. */
    val manualWearCentPerKm: Double = 0.0,
    /** Eigene Jahresfahrleistung, auf die ADAC-Kosten umgerechnet werden. */
    val kmPerYear: Int = 15_000,
    val adacCost: AdacCost? = null,
    val german: GermanStation? = null,
    val foreign: List<ForeignStation> = emptyList(),
) {
    /** Verschleiß, Wartung, Reifen … in Cent pro gefahrenem km, wie er in die Rechnung eingeht. */
    val wearCentPerKm: Double get() = adacCost?.centPerKm(kmPerYear) ?: manualWearCentPerKm
}

data class DePrice(
    val stationId: String,
    val e5: Double?,
    val e10: Double?,
    val diesel: Double?,
    val status: String,
    val fetchedAt: Long,
    /**
     * Zeitpunkt, zu dem die Preise abgerufen wurden. Geschlossene Tankstellen liefern bei Tankerkönig
     * keine Preise; dann bleiben die letzten bekannten Preise stehen und [pricedAt] liegt vor [fetchedAt].
     */
    val pricedAt: Long = fetchedAt,
) {
    val hasPrices: Boolean get() = e5 != null || e10 != null || diesel != null

    /** Preise stammen aus einer früheren Abfrage (Tankstelle inzwischen geschlossen). */
    val isStale: Boolean get() = hasPrices && pricedAt < fetchedAt

    fun priceFor(fuel: Fuel): Double? = when (fuel) {
        Fuel.E5 -> e5
        Fuel.E10 -> e10
        Fuel.DIESEL -> diesel
    }

    companion object {
        /** Neue Abfrage ohne Preise (z. B. geschlossen): letzte bekannte Preise derselben Tankstelle behalten. */
        fun merge(previous: DePrice?, fresh: DePrice): DePrice =
            if (!fresh.hasPrices && previous != null && previous.stationId == fresh.stationId && previous.hasPrices) {
                fresh.copy(e5 = previous.e5, e10 = previous.e10, diesel = previous.diesel, pricedAt = previous.pricedAt)
            } else {
                fresh
            }
    }
}

/** Eine Tankstelle im Umkreis der deutschen Tankstelle (aus der Tankerkönig-Umkreissuche). */
data class AreaStation(
    val id: String,
    val name: String,
    val distKm: Double?,
    val e5: Double?,
    val e10: Double?,
    val diesel: Double?,
    val isOpen: Boolean,
) {
    fun priceFor(fuel: Fuel): Double? = when (fuel) {
        Fuel.E5 -> e5
        Fuel.E10 -> e10
        Fuel.DIESEL -> diesel
    }
}

/** Preise aller Tankstellen im Umkreis [radiusKm] um die deutsche Tankstelle (ohne sie selbst). */
data class Area(
    val stationId: String,
    val radiusKm: Double,
    val fetchedAt: Long,
    val stations: List<AreaStation>,
)

/** Ein abgerufener Preis der deutschen Tankstelle (lokal mitgeschrieben). */
data class PriceObservation(
    val at: Long,
    val e5: Double?,
    val e10: Double?,
    val diesel: Double?,
) {
    fun priceFor(fuel: Fuel): Double? = when (fuel) {
        Fuel.E5 -> e5
        Fuel.E10 -> e10
        Fuel.DIESEL -> diesel
    }
}

/** Amtlicher Höchstpreis in Landeswährung pro Liter, gültig von [from] bis [to] (jeweils ganztägig). */
data class CapPeriod(
    val from: LocalDate,
    val to: LocalDate,
    val petrol: Double,
    val diesel: Double,
) {
    fun covers(day: LocalDate): Boolean = !day.isBefore(from) && !day.isAfter(to)
    fun priceFor(fuel: Fuel): Double = if (fuel.isDiesel) diesel else petrol
}

/** Wechselkurs: Einheiten Landeswährung pro 1 Euro. */
data class RateInfo(val perEur: Double, val date: String)

/** EU-Wochendurchschnitt in EUR/l inkl. Steuern (EU Weekly Oil Bulletin). */
data class EuWeekly(val e95: Double, val diesel: Double, val week: LocalDate) {
    fun priceFor(fuel: Fuel): Double = if (fuel.isDiesel) diesel else e95
}

data class Market(
    val de: DePrice? = null,
    val area: Area? = null,
    /** Mitgeschriebene Preise der deutschen Tankstelle [historyStationId], älteste zuerst. */
    val history: List<PriceObservation> = emptyList(),
    val historyStationId: String = "",
    val czCaps: List<CapPeriod> = emptyList(),
    val plCaps: List<CapPeriod> = emptyList(),
    val czk: RateInfo? = null,
    val pln: RateInfo? = null,
    val euCz: EuWeekly? = null,
    val euPl: EuWeekly? = null,
    val capsDay: String = "",
    val capsFetchedAt: Long = 0L,
    val euFetchedAt: Long = 0L,
    /** Tag, an dem für Polen heute keine Bekanntmachung gefunden wurde (nicht erneut suchen). */
    val plMissDay: String = "",
    val loading: Boolean = false,
    val errors: List<String> = emptyList(),
) {
    fun caps(country: Country): List<CapPeriod> = when (country) {
        Country.CZ -> czCaps
        Country.PL -> plCaps
    }

    fun capFor(country: Country, day: LocalDate): CapPeriod? =
        caps(country).filter { it.covers(day) }.maxByOrNull { it.from }

    fun rate(country: Country): RateInfo? = when (country) {
        Country.CZ -> czk
        Country.PL -> pln
    }

    fun eu(country: Country): EuWeekly? = when (country) {
        Country.CZ -> euCz
        Country.PL -> euPl
    }
}
