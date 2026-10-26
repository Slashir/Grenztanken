package de.grenztanken.app.model

import java.time.LocalDate

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

data class Settings(
    val apiKey: String = "",
    val fuel: Fuel = Fuel.E10,
    val consumption: Double = 7.0,
    val liters: Double = 50.0,
    /** Verschleiß, Wartung, Reifen, Wertverlust … in Cent pro gefahrenem km; 0 = nur Sprit rechnen. */
    val wearCentPerKm: Double = 0.0,
    val german: GermanStation? = null,
    val foreign: List<ForeignStation> = emptyList(),
)

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
