package de.grenztanken.app.data

import de.grenztanken.app.Config
import de.grenztanken.app.model.CapPeriod
import de.grenztanken.app.model.Country
import de.grenztanken.app.model.EuWeekly
import de.grenztanken.app.model.RateInfo
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Amtliche Quellen für Tschechien und Polen.
 * Höchstpreise sind Obergrenzen; an der Säule kann es günstiger sein.
 */
object Sources {
    /** Tschechisches Finanzministerium: zuletzt verkündete Höchstpreise (ab 14 Uhr die für morgen). */
    const val CZ_URL =
        "https://mf.gov.cz/cs/kontrola-a-regulace/cenova-regulace-a-kontrola/maximalni-pripustne-ceny-benzinu-a-nafty"

    /** Polnisches Energieministerium: tägliche Bekanntmachungen. */
    const val PL_BASE = "https://www.gov.pl/web/energia/"

    /** Tschechische Nationalbank: Tageskurse als Text. */
    const val CNB_URL =
        "https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/denni_kurz.txt"

    /** Polnische Nationalbank: EUR-Mittelkurs (Tabelle A). */
    const val NBP_URL = "https://api.nbp.pl/api/exchangerates/rates/A/EUR/?format=json"

    /** fuel-prices.eu: Wochenreihe aus dem EU Weekly Oil Bulletin, EUR/l inkl. Steuern, CC BY 4.0. */
    const val EU_DIRECT_URL = "https://www.fuel-prices.eu/lang/api/country-history.php?cc="

    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /**
     * Zuletzt verkündeter tschechischer Höchstpreis mit Gültigkeitszeitraum.
     * null = Seite erreichbar, aber kein Höchstpreis gefunden (z. B. Regulierung beendet).
     */
    suspend fun cz(today: LocalDate): CapPeriod? {
        val (code, body) = Http.get(CZ_URL)
        if (code != 200) throw IOException("HTTP $code")
        val cap = Parsers.parseCz(Parsers.htmlToText(body), today) ?: return null
        val from = cap.validFrom ?: return null
        val to = cap.validUntil?.takeIf { !it.isBefore(from) } ?: from
        return CapPeriod(from, to, cap.petrol, cap.diesel)
    }

    /** Polnische Bekanntmachung, die für [day] gilt; null, wenn (noch) keine gefunden wurde. */
    suspend fun pl(day: LocalDate): CapPeriod? {
        for (candidate in Parsers.plCandidates(day)) {
            val (code, body) = Http.get(PL_BASE + candidate.slug)
            if (code != 200) continue
            val prices = Parsers.parsePl(Parsers.htmlToText(body)) ?: continue
            return CapPeriod(candidate.from, candidate.to, prices.first, prices.second)
        }
        return null
    }

    suspend fun czk(): RateInfo? {
        val (code, body) = Http.get(CNB_URL)
        if (code != 200) throw IOException("ČNB HTTP $code")
        return Parsers.parseCnbEur(body)?.let { RateInfo(it.first, it.second) }
    }

    suspend fun pln(): RateInfo? {
        val (code, body) = Http.get(NBP_URL)
        if (code != 200) throw IOException("NBP HTTP $code")
        val rate = JSONObject(body).getJSONArray("rates").getJSONObject(0)
        val date = runCatching {
            LocalDate.parse(rate.getString("effectiveDate")).format(DATE)
        }.getOrDefault(rate.optString("effectiveDate"))
        return RateInfo(rate.getDouble("mid"), date)
    }

    /** EU-Wochendurchschnitt; über das eigene GitHub-Relay, falls eingerichtet, sonst direkt. */
    suspend fun eu(country: Country): EuWeekly? {
        val url = if (Config.EU_RELAY_BASE_URL.isNotBlank()) {
            Config.EU_RELAY_BASE_URL.trimEnd('/') + "/eu-${country.code}.json"
        } else {
            EU_DIRECT_URL + country.code
        }
        val (code, body) = Http.get(url)
        if (code != 200) throw IOException("HTTP $code")
        return Parsers.parseEuHistory(body)?.let { EuWeekly(it.e95, it.diesel, it.week) }
    }
}
