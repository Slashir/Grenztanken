package de.grenztanken.app

import de.grenztanken.app.model.DePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DePriceTest {

    private val open = DePrice("a", 1.799, 1.739, 1.659, "open", fetchedAt = 1_000L)
    private val closed = DePrice("a", null, null, null, "closed", fetchedAt = 5_000L)

    @Test
    fun geschlossenBehaeltLetztenPreis() {
        val m = DePrice.merge(open, closed)
        assertEquals(1.739, m.e10!!, 1e-9)
        assertEquals("closed", m.status)
        assertEquals(5_000L, m.fetchedAt)
        assertEquals(1_000L, m.pricedAt)
        assertTrue(m.isStale)
    }

    @Test
    fun neuerPreisErsetztAlten() {
        val fresh = DePrice("a", 1.759, 1.699, 1.629, "open", fetchedAt = 9_000L)
        val m = DePrice.merge(open, fresh)
        assertEquals(1.699, m.e10!!, 1e-9)
        assertEquals(9_000L, m.pricedAt)
        assertFalse(m.isStale)
    }

    @Test
    fun andereTankstelleOhnePreisBleibtLeer() {
        val other = closed.copy(stationId = "b")
        assertNull(DePrice.merge(open, other).e10)
    }

    @Test
    fun ohneFruehereAbfrageBleibtLeer() {
        val m = DePrice.merge(null, closed)
        assertNull(m.e10)
        assertFalse(m.isStale)
    }
}
