package de.grenztanken.app

import de.grenztanken.app.data.Parsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParsersTest {

    // Textauszug der Seite des tschechischen Finanzministeriums (Stand 23.10.2026)
    private val czHtml = """
        <h1>Maximální přípustné ceny benzinu a nafty</h1>
        <p><strong>Termín stanovení:</strong> 23. října 2026 ve 14:00 formou sdělení Ministerstva financí
        <strong>Účinnost:</strong> 24. října 2026 od 00:00 - 26. října do 24:00 na celém území ČR</p>
        <p>Na základě rozhodnutí Vlády ČR z 21. září 2026 platí od 1. do 31. října 2026 opětovná regulace.</p>
        <h4>Maximální přípustná cena benzinu: 44,55&nbsp;Kč s DPH / litr</h4>
        <h4>Maximální přípustná cena nafty: 48,69 Kč s DPH / litr</h4>
    """.trimIndent()

    @Test
    fun tschechienHoechstpreis() {
        val cap = Parsers.parseCz(Parsers.htmlToText(czHtml), LocalDate.of(2026, 10, 26))
        assertNotNull(cap)
        assertEquals(44.55, cap!!.petrol, 1e-9)
        assertEquals(48.69, cap.diesel, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 24), cap.validFrom)
        assertEquals(LocalDate.of(2026, 10, 26), cap.validUntil)
        assertTrue(cap.validity.startsWith("24. října 2026"))
    }

    @Test
    fun tschechienOhneWerte() {
        assertNull(Parsers.parseCz("Regulace cen skončila.", LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun entitiesWerdenAufgeloest() {
        val t = Parsers.htmlToText("<p>&Uacute;&#269;innost: 1.&#160;listopadu</p>")
        assertEquals("Účinnost: 1. listopadu", t)
    }

    @Test
    fun polenHoechstpreis() {
        val text = "Benzyna 95 - 6,70 zł/l, benzyna 98 - 7,43 zł/l, olej napędowy - 7,98 zł/l. " +
            "Minister Energii podał nowe maksymalne ceny"
        assertEquals(6.70 to 7.98, Parsers.parsePl(text))
    }

    @Test
    fun polenMitPunkt() {
        val text = "Benzyna 95 - 5.98 zł/l, benzyna 98 - 6,55 zł/l, olej napędowy - 6,37 zł/l."
        assertEquals(5.98 to 6.37, Parsers.parsePl(text))
    }

    @Test
    fun polenSlugs() {
        val slugs = Parsers.plCandidates(LocalDate.of(2026, 10, 26)).map { it.slug }
        assertEquals("maksymalna-cena-detaliczna-paliw-obowiazujaca-26-pazdziernika-2026-r", slugs.first())
        assertTrue(slugs.contains("maksymalna-cena-detaliczna-paliw-obowiazujaca-w-dniach-24-26-pazdziernika-2026-r"))
        assertEquals(10, slugs.size)
    }

    @Test
    fun cnbKurs() {
        val txt = "26.10.2026 #207\nzemě|měna|množství|kód|kurz\nAustrálie|dolar|1|AUD|15,123\nEMU|euro|1|EUR|24,330\n"
        assertEquals(24.33 to "26.10.2026", Parsers.parseCnbEur(txt))
    }

    @Test
    fun tschechienEinzelnerTag() {
        val dates = Parsers.czDates("27. října 2026 od 00:00 do 24:00", 2026)
        assertEquals(listOf(LocalDate.of(2026, 10, 27)), dates)
    }

    @Test
    fun euWochenreiheLetzterGueltigerWert() {
        val json = """{"cc":"CZ","labels":["2026-10-05","2026-10-12","2026-10-19"],""" +
            """"e95":[1.81,1.83,0],"diesel":[1.95,1.97,1.99],"count":3,"missing_weeks":[]}"""
        val w = Parsers.parseEuHistory(json)
        assertNotNull(w)
        assertEquals(1.83, w!!.e95, 1e-9)
        assertEquals(1.99, w.diesel, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 19), w.week)
    }

    @Test
    fun euOhneDaten() {
        assertNull(Parsers.parseEuHistory("""{"error":"bad cc"}"""))
    }
}
