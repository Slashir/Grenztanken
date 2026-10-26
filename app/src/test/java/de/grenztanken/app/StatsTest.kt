package de.grenztanken.app

import de.grenztanken.app.logic.Stats
import de.grenztanken.app.model.Area
import de.grenztanken.app.model.AreaStation
import de.grenztanken.app.model.Fuel
import de.grenztanken.app.model.PriceObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {

    private val h = 3_600_000L

    private fun obs(hour: Long, e10: Double?) = PriceObservation(hour * h, null, e10, null)

    @Test
    fun umgebungNurOffeneMitPreis() {
        val area = Area(
            "own", 5.0, 0L,
            listOf(
                AreaStation("a", "Jet", 1.2, null, 1.709, null, isOpen = true),
                AreaStation("b", "Aral", 2.0, null, 1.799, null, isOpen = true),
                AreaStation("c", "Shell", 3.0, null, 1.599, null, isOpen = false),
                AreaStation("d", "Freie", 4.0, null, null, 1.6, isOpen = true),
            ),
        )
        val s = Stats.area(area, Fuel.E10)!!
        assertEquals(2, s.count)
        assertEquals(1.754, s.average, 1e-9)
        assertEquals("Jet", s.cheapest.name)
        assertEquals(1.709, s.cheapestPrice, 1e-9)
        assertNull(Stats.area(area.copy(stations = emptyList()), Fuel.E10))
    }

    @Test
    fun zeitgewichtet() {
        // 8 Uhr 1,80 (gilt 2 h), 10 Uhr 1,70 (gilt 1 h bis 11 Uhr = Ende)
        val hist = listOf(obs(8, 1.80), obs(10, 1.70))
        val a = Stats.timeWeighted(hist, Fuel.E10, 0L, 11 * h)!!
        assertEquals((1.80 * 2 + 1.70 * 1) / 3, a.average, 1e-9)
        assertEquals(2, a.observations)
        assertEquals(1.70, a.min, 1e-9)
        assertEquals(1.80, a.max, 1e-9)
    }

    @Test
    fun langeLueckeZaehltHoechstensZweiStunden() {
        // 6 Uhr 1,90, nächster Abruf erst 18 Uhr 1,70; Ende 19 Uhr
        val hist = listOf(obs(6, 1.90), obs(18, 1.70))
        val a = Stats.timeWeighted(hist, Fuel.E10, 0L, 19 * h)!!
        assertEquals((1.90 * 2 + 1.70 * 1) / 3, a.average, 1e-9)
    }

    @Test
    fun einzelnerAbrufAmEnde() {
        val a = Stats.timeWeighted(listOf(obs(5, 1.75)), Fuel.E10, 0L, 5 * h)
        assertNull(a) // liegt nicht vor dem Ende des Zeitraums
        val b = Stats.timeWeighted(listOf(obs(5, 1.75)), Fuel.E10, 0L, 5 * h + 1)!!
        assertEquals(1.75, b.average, 1e-9)
    }

    @Test
    fun aufzeichnungVerwirftAlteUndLeere() {
        val old = obs(0, 1.8)
        val recent = obs(24 * 13, 1.7)
        val now = obs(24 * 15, 1.75)
        val hist = Stats.record(listOf(old, recent), now)
        assertEquals(listOf(recent, now), hist)
        assertEquals(hist, Stats.record(hist, PriceObservation(24 * 15 * h + 1, null, null, null)))
    }
}
