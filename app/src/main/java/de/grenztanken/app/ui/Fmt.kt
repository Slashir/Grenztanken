package de.grenztanken.app.ui

import de.grenztanken.app.model.CapPeriod
import de.grenztanken.app.model.Country
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private val L: Locale = Locale.GERMANY

    fun dec(v: Double, digits: Int): String = String.format(L, "%.${digits}f", v)
    fun eur(v: Double): String = minus(String.format(L, "%.2f €", v))
    fun eurSigned(v: Double): String = (if (v > 0) "+" else "") + eur(v)
    fun eurPerL(v: Double): String = String.format(L, "%.3f €/l", v)
    /** Unterschied in Cent mit Vorzeichen, z. B. "−1,0 ct". */
    fun ctDelta(ct: Double): String = minus(String.format(L, "%+.1f ct", ct))
    fun ctPerL(v: Double): String = minus(String.format(L, "%+.1f ct/l", v * 100.0))
    fun int(v: Int): String = String.format(L, "%,d", v)
    fun km(v: Double): String = String.format(L, "%.0f km", v)
    fun local(v: Double, c: Country): String = String.format(L, "%.2f %s/l", v, c.currencySymbol)
    fun localDiff(v: Double, c: Country): String = minus(String.format(L, "%+.2f %s/l", v, c.currencySymbol))

    /** Typografisches Minus statt Bindestrich, damit Beträge sauber untereinander stehen. */
    private fun minus(s: String): String = s.replace('-', '−')
    fun date(d: LocalDate): String = d.format(DAY_YEAR)

    /** "am 27.10." oder "24.10.–26.10." */
    fun period(p: CapPeriod): String =
        if (p.from == p.to) "am ${p.from.format(DAY)}" else "${p.from.format(DAY)}–${p.to.format(DAY)}"

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.", L)
    private val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", L)

    fun time(ms: Long): String = SimpleDateFormat("dd.MM. HH:mm", L).format(Date(ms))

    /** Zahl für ein Eingabefeld, mit Komma. */
    fun input(v: Double?): String = when {
        v == null -> ""
        v % 1.0 == 0.0 -> v.toLong().toString()
        else -> v.toString().replace('.', ',')
    }

    /** Eingabe mit Komma oder Punkt. */
    fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    fun status(s: String): String = when (s) {
        "open" -> "geöffnet"
        "closed" -> "geschlossen"
        "no prices" -> "keine Preise"
        else -> s
    }
}
