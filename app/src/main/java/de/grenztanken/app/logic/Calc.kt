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
    /** Spritkosten für Hin- und Rückweg. */
    val tripCost: Double,
    /** Ersparnis abzüglich Fahrtkosten. */
    val netSaving: Double,
    /** Einfache Strecke, bis zu der es sich rechnet; null, wenn das Ausland nicht günstiger ist. */
    val breakEvenOneWayKm: Double?,
) {
    val worthIt: Boolean get() = netSaving > 0.0
}

object Calc {
    /**
     * Annahme: Der Sprit für Hin- und Rückweg wird an der Auslandstankstelle nachgetankt,
     * kostet also den Auslandspreis. Verschleiß und Zeit werden bewusst nicht eingerechnet.
     *
     * Netto = Liter × (Preis DE − Preis Ausland) − 2 × Strecke × Verbrauch/100 × Preis Ausland
     */
    fun compute(
        germanEurPerL: Double,
        foreignLocalPerL: Double,
        localPerEur: Double,
        liters: Double,
        consumptionL100: Double,
        oneWayKm: Double,
    ): Calculation {
        require(localPerEur > 0.0) { "Wechselkurs muss positiv sein" }
        val foreignEur = foreignLocalPerL / localPerEur
        val diff = germanEurPerL - foreignEur
        val gross = liters * diff
        val costPerKm = consumptionL100 / 100.0 * foreignEur
        val trip = 2.0 * oneWayKm * costPerKm
        val breakEven = if (diff > 0.0 && costPerKm > 0.0) gross / costPerKm / 2.0 else null
        return Calculation(
            foreignEurPerL = foreignEur,
            diffPerL = diff,
            grossSaving = gross,
            costPerKm = costPerKm,
            tripCost = trip,
            netSaving = gross - trip,
            breakEvenOneWayKm = breakEven,
        )
    }
}
