package de.grenztanken.app.logic

data class Calculation(
    /** Auslandspreis in Euro pro Liter. */
    val foreignEurPerL: Double,
    /** Deutscher Preis minus Auslandspreis, Euro pro Liter. */
    val diffPerL: Double,
    /** Ersparnis beim Tanken ohne Fahrtkosten. */
    val grossSaving: Double,
    /** Spritkosten pro gefahrenem Kilometer. */
    val costPerKm: Double,
    /** Verschleiß, Wartung usw. pro gefahrenem Kilometer (Eingabe des Nutzers). */
    val wearPerKm: Double,
    /** Spritkosten für Hin- und Rückweg. */
    val tripFuelCost: Double,
    /** Verschleißkosten für Hin- und Rückweg. */
    val tripWearCost: Double,
    /** Ersparnis abzüglich Fahrtkosten (Sprit und Verschleiß). */
    val netSaving: Double,
    /** Einfache Strecke, bis zu der es sich rechnet; null, wenn das Ausland nicht günstiger ist. */
    val breakEvenOneWayKm: Double?,
) {
    val tripCost: Double get() = tripFuelCost + tripWearCost
    val worthIt: Boolean get() = netSaving > 0.0
}

object Calc {
    /**
     * Annahme: Der Sprit für Hin- und Rückweg wird an der Auslandstankstelle nachgetankt,
     * kostet also den Auslandspreis. Verschleiß, Wartung usw. gibt der Nutzer pro km vor.
     * Zeit wird bewusst nicht eingerechnet.
     *
     * Netto = Liter × (Preis DE − Preis Ausland)
     *       − 2 × Strecke × (Verbrauch/100 × Preis Ausland + Verschleiß pro km)
     */
    fun compute(
        germanEurPerL: Double,
        foreignLocalPerL: Double,
        localPerEur: Double,
        liters: Double,
        consumptionL100: Double,
        oneWayKm: Double,
        wearEurPerKm: Double = 0.0,
    ): Calculation {
        require(localPerEur > 0.0) { "Wechselkurs muss positiv sein" }
        require(wearEurPerKm >= 0.0) { "Verschleiß darf nicht negativ sein" }
        val foreignEur = foreignLocalPerL / localPerEur
        val diff = germanEurPerL - foreignEur
        val gross = liters * diff
        val costPerKm = consumptionL100 / 100.0 * foreignEur
        val allPerKm = costPerKm + wearEurPerKm
        val fuelTrip = 2.0 * oneWayKm * costPerKm
        val wearTrip = 2.0 * oneWayKm * wearEurPerKm
        val breakEven = if (diff > 0.0 && allPerKm > 0.0) gross / allPerKm / 2.0 else null
        return Calculation(
            foreignEurPerL = foreignEur,
            diffPerL = diff,
            grossSaving = gross,
            costPerKm = costPerKm,
            wearPerKm = wearEurPerKm,
            tripFuelCost = fuelTrip,
            tripWearCost = wearTrip,
            netSaving = gross - fuelTrip - wearTrip,
            breakEvenOneWayKm = breakEven,
        )
    }
}
