package de.grenztanken.app

import de.grenztanken.app.logic.Calc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcTest {

    @Test
    fun beispielTschechien() {
        // 50 l, DE 2,10 €/l, CZ 44,55 Kč/l bei 24,33 Kč/€, 7 l/100 km, 30 km einfach
        val c = Calc.compute(2.10, 44.55, 24.33, 50.0, 7.0, 30.0)
        assertEquals(1.83107, c.foreignEurPerL, 1e-4)
        assertEquals(0.26893, c.diffPerL, 1e-4)
        assertEquals(13.4464, c.grossSaving, 1e-3)
        assertEquals(7.6905, c.tripCost, 1e-3)
        assertEquals(5.7559, c.netSaving, 1e-3)
        assertEquals(52.453, c.breakEvenOneWayKm!!, 1e-2)
        assertTrue(c.worthIt)
    }

    @Test
    fun genauAmBreakEvenIstNettoNull() {
        val c = Calc.compute(2.0, 7.0, 4.25, 45.0, 6.5, 0.0)
        val atBreakEven = Calc.compute(2.0, 7.0, 4.25, 45.0, 6.5, c.breakEvenOneWayKm!!)
        assertEquals(0.0, atBreakEven.netSaving, 1e-9)
    }

    @Test
    fun verschleissWirdAbgezogen() {
        // wie oben, zusätzlich 8 ct/km Verschleiß: 60 km × 0,08 € = 4,80 €
        val c = Calc.compute(2.10, 44.55, 24.33, 50.0, 7.0, 30.0, wearEurPerKm = 0.08)
        assertEquals(7.6905, c.tripFuelCost, 1e-3)
        assertEquals(4.80, c.tripWearCost, 1e-9)
        assertEquals(0.9559, c.netSaving, 1e-3)
        // Break-even: 13,4464 € / (0,128175 + 0,08) €/km / 2
        assertEquals(32.296, c.breakEvenOneWayKm!!, 1e-2)
        val atBreakEven = Calc.compute(2.10, 44.55, 24.33, 50.0, 7.0, c.breakEvenOneWayKm!!, wearEurPerKm = 0.08)
        assertEquals(0.0, atBreakEven.netSaving, 1e-9)
    }

    @Test
    fun auslandTeurerLohntNie() {
        val c = Calc.compute(1.70, 46.0, 24.0, 50.0, 7.0, 5.0)
        assertNull(c.breakEvenOneWayKm)
        assertFalse(c.worthIt)
    }
}
