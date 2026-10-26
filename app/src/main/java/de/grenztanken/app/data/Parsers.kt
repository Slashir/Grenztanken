package de.grenztanken.app.data

import java.time.LocalDate

/**
 * Reine Text-Parser ohne Android-Abhängigkeiten, damit sie als JVM-Unit-Tests laufen.
 */
object Parsers {

    private val NAMED_ENTITIES = mapOf(
        "nbsp" to " ", "amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">",
        "ndash" to "–", "mdash" to "—",
        "aacute" to "á", "Aacute" to "Á", "eacute" to "é", "Eacute" to "É",
        "iacute" to "í", "Iacute" to "Í", "oacute" to "ó", "Oacute" to "Ó",
        "uacute" to "ú", "Uacute" to "Ú", "yacute" to "ý", "Yacute" to "Ý",
    )

    /** HTML grob in Fließtext umwandeln: Skripte/Tags weg, Entities auflösen, Leerraum normalisieren. */
    fun htmlToText(html: String): String {
        var s = html.replace(Regex("(?is)<(script|style|noscript)\\b[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?s)<[^>]+>"), " ")
        s = Regex("&#(x?)([0-9a-fA-F]+);").replace(s) { m ->
            val code = if (m.groupValues[1].isNotEmpty()) {
                m.groupValues[2].toIntOrNull(16)
            } else {
                m.groupValues[2].toIntOrNull()
            }
            code?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value
        }
        s = Regex("&([a-zA-Z]+);").replace(s) { m -> NAMED_ENTITIES[m.groupValues[1]] ?: m.value }
        s = s.replace(' ', ' ').replace(' ', ' ')
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    // ---------------------------------------------------------------- Tschechien

    data class CzCap(
        val petrol: Double,
        val diesel: Double,
        val validity: String,
        val validFrom: LocalDate?,
        val validUntil: LocalDate?,
    )

    private val CZ_MONTHS = listOf(
        "ledna", "února", "března", "dubna", "května", "června",
        "července", "srpna", "září", "října", "listopadu", "prosince",
    )

    /**
     * Liest von der Seite des tschechischen Finanzministeriums z. B.
     * "Maximální přípustná cena benzinu: 44,55 Kč s DPH / litr" und
     * "Účinnost: 24. října 2026 od 00:00 - 26. října do 24:00 na celém území ČR".
     */
    fun parseCz(text: String, today: LocalDate): CzCap? {
        val petrol = Regex("""cena benzinu\s*:?\s*(\d{1,3}[.,]\d{1,2})""")
            .find(text)?.groupValues?.get(1)?.let { num(it) } ?: return null
        val diesel = Regex("""cena nafty\s*:?\s*(\d{1,3}[.,]\d{1,2})""")
            .find(text)?.groupValues?.get(1)?.let { num(it) } ?: return null
        if (petrol !in 15.0..100.0 || diesel !in 15.0..100.0) return null

        val validity = (
            Regex("""nnost\s*:\s*(.{5,160}?)(?:\s+na celém|\s+Na základě|\s+Maximální)""").find(text)
                ?: Regex("""nnost\s*:\s*(.{5,80})""").find(text)
            )?.groupValues?.get(1)?.trim().orEmpty()

        val dates = czDates(validity, today.year)
        return CzCap(petrol, diesel, validity, dates.firstOrNull(), dates.lastOrNull())
    }

    /**
     * Alle Daten im Gültigkeitstext, z. B. "24. října 2026 od 00:00 - 26. října do 24:00"
     * → [2026-10-24, 2026-10-26]. Fehlt bei einem Datum das Jahr, gilt das genannte bzw. [defaultYear].
     */
    fun czDates(validity: String, defaultYear: Int): List<LocalDate> {
        val re = Regex("""(\d{1,2})\.\s*(${CZ_MONTHS.joinToString("|")})(?:\s+(\d{4}))?""")
        val all = re.findAll(validity).toList()
        val year = all.mapNotNull { it.groupValues[3].toIntOrNull() }.lastOrNull() ?: defaultYear
        return all.mapNotNull { m ->
            val month = CZ_MONTHS.indexOf(m.groupValues[2]) + 1
            val y = m.groupValues[3].toIntOrNull() ?: year
            runCatching { LocalDate.of(y, month, m.groupValues[1].toInt()) }.getOrNull()
        }
    }

    // ---------------------------------------------------------------- Polen

    /**
     * Liest aus der Bekanntmachung des polnischen Energieministeriums z. B.
     * "Benzyna 95 - 6,70 zł/l, benzyna 98 - 7,43 zł/l, olej napędowy - 7,98 zł/l".
     * Ergebnis: (Benzin 95, Diesel).
     */
    fun parsePl(text: String): Pair<Double, Double>? {
        val petrol = Regex("""(?i)benzyna\s*95\s*[-–:]\s*(\d{1,2}[.,]\d{1,2})""")
            .find(text)?.groupValues?.get(1)?.let { num(it) } ?: return null
        val diesel = Regex("""(?i)olej napędowy\s*[-–:]\s*(\d{1,2}[.,]\d{1,2})""")
            .find(text)?.groupValues?.get(1)?.let { num(it) } ?: return null
        if (petrol !in 2.0..20.0 || diesel !in 2.0..20.0) return null
        return petrol to diesel
    }

    data class PlCandidate(val slug: String, val from: LocalDate, val to: LocalDate)

    private val PL_MONTHS = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "wrzesnia", "pazdziernika", "listopada", "grudnia",
    )
    private const val PL_PREFIX = "maksymalna-cena-detaliczna-paliw-obowiazujaca-"

    private fun plMonth(d: LocalDate) = PL_MONTHS[d.monthValue - 1]

    /**
     * Mögliche Adressen (Slugs) der Bekanntmachungen auf gov.pl, die für [date] gelten.
     * Bekannte Muster:
     *  - "...-obowiazujaca-21-pazdziernika-2026-r" (ein Tag)
     *  - "...-obowiazujaca-w-dniach-24-26-pazdziernika-2026-r" (Wochenende/Feiertage)
     * Zeiträume über einen Monatswechsel sind geraten (Monat bei beiden Tagen).
     */
    fun plCandidates(date: LocalDate): List<PlCandidate> {
        val out = mutableListOf(
            PlCandidate("$PL_PREFIX${date.dayOfMonth}-${plMonth(date)}-${date.year}-r", date, date),
        )
        for (back in 0..3) {
            val start = date.minusDays(back.toLong())
            for (len in 1..3) {
                val end = start.plusDays(len.toLong())
                if (end.isBefore(date)) continue
                val slug = if (start.monthValue == end.monthValue) {
                    "${PL_PREFIX}w-dniach-${start.dayOfMonth}-${end.dayOfMonth}-${plMonth(end)}-${end.year}-r"
                } else {
                    "${PL_PREFIX}w-dniach-${start.dayOfMonth}-${plMonth(start)}-${end.dayOfMonth}-${plMonth(end)}-${end.year}-r"
                }
                out += PlCandidate(slug, start, end)
            }
        }
        return out.distinctBy { it.slug }
    }

    // ---------------------------------------------------------------- Wechselkurse

    /**
     * ČNB-Tageskurs (denni_kurz.txt):
     * Zeile 1 "26.10.2026 #207", Zeile 2 Kopf, dann "EMU|euro|1|EUR|24,330".
     * Ergebnis: (CZK pro 1 EUR, Datum).
     */
    fun parseCnbEur(txt: String): Pair<Double, String>? {
        val lines = txt.lines()
        val date = lines.firstOrNull()?.trim()?.substringBefore(' ').orEmpty()
        val line = lines.firstOrNull { it.split('|').getOrNull(3)?.trim() == "EUR" } ?: return null
        val fields = line.split('|')
        val amount = fields.getOrNull(2)?.trim()?.let { num(it) }?.takeIf { it > 0.0 } ?: 1.0
        val rate = fields.getOrNull(4)?.let { num(it) } ?: return null
        return (rate / amount) to date
    }

    // ---------------------------------------------------------------- EU-Wochendurchschnitt

    data class EuWeek(val e95: Double, val diesel: Double, val week: LocalDate)

    /**
     * Antwort von fuel-prices.eu `country-history.php` (Quelle: EU Weekly Oil Bulletin):
     * {"cc":"CZ","labels":["2026-10-12","2026-10-19"],"e95":[1.81,1.83],"diesel":[1.95,1.97],...}
     * Preise in EUR/l inkl. Steuern; fehlende Wochen stehen als 0.
     * Ergebnis: jeweils der letzte Wert > 0 und die Woche des jüngeren der beiden.
     */
    fun parseEuHistory(json: String): EuWeek? {
        val labels = jsonArray(json, "labels")?.map { it.trim().trim('"') } ?: return null
        val e95 = jsonArray(json, "e95")?.map { it.trim().toDoubleOrNull() ?: 0.0 } ?: return null
        val diesel = jsonArray(json, "diesel")?.map { it.trim().toDoubleOrNull() ?: 0.0 } ?: return null
        val iE = lastPositive(e95, labels.size) ?: return null
        val iD = lastPositive(diesel, labels.size) ?: return null
        val week = runCatching { LocalDate.parse(labels[maxOf(iE, iD)]) }.getOrNull() ?: return null
        return EuWeek(e95[iE], diesel[iD], week)
    }

    private fun lastPositive(values: List<Double>, limit: Int): Int? =
        (minOf(values.size, limit) - 1 downTo 0).firstOrNull { values[it] > 0.0 }

    /** Inhalt eines flachen JSON-Arrays als Rohwerte (ohne verschachtelte Strukturen). */
    private fun jsonArray(json: String, key: String): List<String>? {
        val m = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\\[([^\\[\\]]*)]").find(json) ?: return null
        val body = m.groupValues[1].trim()
        return if (body.isEmpty()) emptyList() else body.split(',')
    }
}
