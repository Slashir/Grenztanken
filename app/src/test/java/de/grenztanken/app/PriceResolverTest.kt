package de.grenztanken.app

import de.grenztanken.app.logic.PriceResolver
import de.grenztanken.app.logic.PriceSource
import de.grenztanken.app.model.CapPeriod
import de.grenztanken.app.model.Country
import de.grenztanken.app.model.EuWeekly
import de.grenztanken.app.model.ForeignStation
import de.grenztanken.app.model.Fuel
import de.grenztanken.app.model.Market
import de.grenztanken.app.model.RateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PriceResolverTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val station = ForeignStation("1", "Brandov", Country.CZ, 20.0)
    private val rate = RateInfo(24.33, "26.10.2026")
    private val weekend = CapPeriod(LocalDate.of(2026, 10, 24), today, 44.55, 48.69)
    private val tue = CapPeriod(today.plusDays(1), today.plusDays(1), 44.20, 48.90)
    private val eu = EuWeekly(1.83, 1.97, LocalDate.of(2026, 10, 19))

    @Test
    fun saeulenpreisGehtVor() {
        val m = Market(czCaps = listOf(weekend), czk = rate)
        val p = PriceResolver.resolve(station.copy(manualPetrol = 43.9), Fuel.E10, m, today)!!
        assertEquals(PriceSource.MANUAL, p.source)
        assertEquals(43.9, p.price, 1e-9)
    }

    @Test
    fun hoechstpreisHeuteUndHinweisMorgen() {
        val m = Market(czCaps = listOf(weekend, tue), czk = rate, euCz = eu)
        val p = PriceResolver.resolve(station, Fuel.DIESEL, m, today)!!
        assertEquals(PriceSource.CAP_TODAY, p.source)
        assertEquals(48.69, p.price, 1e-9)
        val t = PriceResolver.tomorrow(station, Fuel.DIESEL, m, today)!!
        assertEquals(48.90, t.price, 1e-9)
        assertEquals(0.21, t.diff, 1e-9)
    }

    @Test
    fun nurMorgenBekannt() {
        val m = Market(czCaps = listOf(tue), czk = rate)
        assertEquals(PriceSource.CAP_TOMORROW, PriceResolver.resolve(station, Fuel.E5, m, today)!!.source)
        assertNull(PriceResolver.tomorrow(station, Fuel.E5, m, today))
    }

    @Test
    fun ohneHoechstpreisEuDurchschnittInEuro() {
        val m = Market(czk = rate, euCz = eu)
        val p = PriceResolver.resolve(station, Fuel.E10, m, today)!!
        assertEquals(PriceSource.EU_WEEKLY, p.source)
        assertEquals(1.83, p.price, 1e-9)
        assertEquals(1.0, p.perEur!!, 1e-9)
    }

    @Test
    fun abgelaufenerHoechstpreisZaehltNicht() {
        val old = CapPeriod(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 30), 44.0, 48.0)
        val m = Market(czCaps = listOf(old), czk = rate, euCz = eu)
        val p = PriceResolver.resolve(station, Fuel.E10, m, LocalDate.of(2026, 12, 1))!!
        assertEquals(PriceSource.EU_WEEKLY, p.source)
    }

    @Test
    fun verlaufOhneDoppelteUndAltes() {
        val old = CapPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), 1.0, 1.0)
        val merged = PriceResolver.merge(listOf(old, weekend), weekend, today)
        assertEquals(listOf(weekend), merged)
    }
}
