package de.grenztanken.app

import de.grenztanken.app.data.Adac
import de.grenztanken.app.data.JsonCodec
import de.grenztanken.app.model.AdacCost
import de.grenztanken.app.model.AdacPoint
import de.grenztanken.app.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdacTest {

    private fun page(state: String) =
        """<html><script nonce="x">window.__APOLLO_STATE__=$state;</script><script>window.__ENV__="prod";</script></html>"""

    // Gekürzt aus adac.de/rund-ums-fahrzeug/autokatalog/marken-modelle/hyundai/tucson/3generation-facelift/344972/
    // (Tucson 1.6 T-GDI Select ab 09/25, abgerufen 27.10.2026): Werte für mehrere Haltedauern und Fahrleistungen.
    private val gridPage = page(
        """
        {"ROOT_QUERY":{"carPage({\"carId\":344972})":{"carPage":{"__typename":"CarPage",
          "costs":{"__typename":"ApilCosts","adacPrice":"37240 Euro","isElectricCar":false,"costs":[
            {"months":24,"kilometers":15000,"perMonth":1160,"perKilometer":92.9,"deterioration":722,"workshop":61,"energyConsumption":7},
            {"months":60,"kilometers":5000,"perMonth":668,"perKilometer":160.5,"deterioration":364,"workshop":58,"energyConsumption":7},
            {"months":60,"kilometers":10000,"perMonth":759,"perKilometer":91.2,"deterioration":379,"workshop":69,"energyConsumption":7},
            {"months":60,"kilometers":15000,"perMonth":861,"perKilometer":68.9,"deterioration":399,"workshop":85,"energyConsumption":7},
            {"months":60,"kilometers":20000,"perMonth":974,"perKilometer":58.5,"deterioration":421,"workshop":110,"energyConsumption":7},
            {"months":60,"kilometers":25000,"perMonth":1081,"perKilometer":51.9,"deterioration":442,"workshop":131,"energyConsumption":7},
            {"months":60,"kilometers":30000,"perMonth":1200,"perKilometer":48,"deterioration":463,"workshop":163,"energyConsumption":7}
          ]}}}}}
        """.trimIndent(),
    )

    // Ältere Variante (…/335470/, 06/24–09/25): nur 5 Jahre und 15.000 km.
    private val singlePage = page(
        """
        {"ROOT_QUERY":{"carPage({\"carId\":335470})":{"carPage":{"costs":{"costs":[
          {"months":60,"kilometers":15000,"perMonth":686,"perKilometer":54.9,"deterioration":207,"workshop":108,"energyConsumption":6.8}
        ]}}}}}
        """.trimIndent(),
    )

    private fun cost(html: String) =
        AdacCost("Tucson", "https://www.adac.de/x/", Adac.parseCosts(Adac.extractState(html)!!)!!.points)

    @Test
    fun parsesAllCombinations() {
        val c = Adac.parseCosts(Adac.extractState(gridPage)!!)
        assertNotNull(c)
        assertEquals(7, c!!.points.size)
        assertEquals(AdacPoint(60, 15_000, 85.0, 399.0), c.points.first { it.months == 60 && it.kmPerYear == 15_000 })
        assertEquals(7.0, c.consumption!!, 1e-9)
        assertEquals(listOf(24, 60), cost(gridPage).holdings)
    }

    @Test
    fun usesFiveYearsByDefaultNotTheFirstEntry() {
        // Früher wurde versehentlich der 2-Jahres-Wert genommen (Werkstatt 61 €, Wertverlust 722 €).
        val c = cost(gridPage)
        assertEquals(60, c.usedMonths)
        assertEquals(85.0, c.workshopPerMonth(15_000), 1e-9)
        assertEquals(399.0, c.deteriorationPerMonth(15_000), 1e-9)
        assertEquals(61.0, c.copy(months = 24).workshopPerMonth(15_000), 1e-9)
    }

    @Test
    fun interpolatesMonthlyCostBetweenSteps() {
        val c = cost(gridPage)
        // 12.000 km: 69 + (85 − 69) × 2/5 = 75,4 €
        assertEquals(75.4, c.workshopPerMonth(12_000), 1e-9)
        // Durchschnitt: 75,4 € × 12 ÷ 12.000 km = 7,54 ct
        assertEquals(7.54, c.averageCentPerKm(12_000), 1e-9)
    }

    @Test
    fun additionalCostPerKmFromNeighbouringSteps() {
        val c = cost(gridPage)
        // 15.000 km: (110 − 69) € ÷ 10.000 km × 12 = 4,92 ct
        assertEquals(4.92, c.centPerKm(15_000), 1e-9)
        // 12.000 km: zwischen 7.000 (62,4 €) und 17.000 km (95 €) → 3,912 ct
        assertEquals(3.912, c.centPerKm(12_000), 1e-9)
        // Unterhalb und oberhalb der ADAC-Werte gilt die Randstufe.
        assertEquals((85.0 - 58.0) / 10_000 * 1200, c.centPerKm(3_000), 1e-9)
        assertEquals((163.0 - 110.0) / 10_000 * 1200, c.centPerKm(40_000), 1e-9)
        // Wertverlust: (421 − 379) € ÷ 10.000 km × 12 = 5,04 ct
        assertEquals(5.04, c.deteriorationCentPerKm(15_000), 1e-9)
        assertEquals(4.92 + 5.04, c.copy(withDeterioration = true).centPerKm(15_000), 1e-9)
    }

    @Test
    fun singleValueFallsBackToAverageWorkshopCost() {
        val c = cost(singlePage)
        assertFalse(c.hasMileageSteps)
        // 108 € × 12 ÷ 15.000 km = 8,64 ct, unabhängig von der eigenen Fahrleistung
        assertEquals(8.64, c.centPerKm(15_000), 1e-9)
        assertEquals(8.64, c.centPerKm(10_000), 1e-9)
        // Wertverlust je km lässt sich aus einem einzigen Wert nicht bestimmen.
        assertEquals(8.64, c.copy(withDeterioration = true).centPerKm(15_000), 1e-9)
        assertTrue(cost(gridPage).hasMileageSteps)
    }

    @Test
    fun noCostsForBrandNewModel() {
        val html = page("""{"ROOT_QUERY":{"carPage({\"carId\":353260})":{"carPage":{"costs":null}}}}""")
        assertNull(Adac.parseCosts(Adac.extractState(html)!!))
    }

    @Test
    fun unknownPageFormat() {
        assertNull(Adac.extractState("<html><body>Wartung</body></html>"))
    }

    @Test
    fun brandsModelsGenerationsVariants() {
        val brands = Adac.parseBrands(
            Adac.extractState(
                page(
                    """{"ApilBrandCollectionItem:21":{"id":"21","slug":"hyundai","name":"Hyundai"},
                        "ApilBrandCollectionItem:11":{"id":"11","slug":"alfa-romeo","name":"Alfa Romeo"},
                        "ROOT_QUERY":{}}""",
                ),
            )!!,
        )
        assertEquals(listOf("alfa-romeo", "hyundai"), brands.map { it.slug })

        val models = Adac.parseModels(
            Adac.extractState(
                page(
                    """{"ApilRangeCollectionItem:{\"slug\":\"tucson\"}":{"name":"Tucson","slug":"tucson"},
                        "ApilRangeCollectionItem:{\"slug\":\"i30\"}":{"name":"i30","slug":"i30"}}""",
                ),
            )!!,
        )
        assertEquals(listOf("i30", "tucson"), models.map { it.slug })

        // Reihenfolge wie auf der Modellseite (über die Verweise in ApilRange), nicht nach Schlüssel.
        val gens = Adac.parseGenerations(
            Adac.extractState(
                page(
                    """{"ApilGenerationCollectionItem:3051":{"name":"3. Generation","slug":"3generation","facelift":0,
                          "manufacturedFrom":2020,"manufacturedUntil":2024},
                        "ApilGenerationCollectionItem:3480":{"name":"3. Generation","slug":"3generation-facelift","facelift":1,
                          "manufacturedFrom":2024,"manufacturedUntil":null},
                        "ApilRange:316":{"name":"Tucson","generations":[
                          {"__ref":"ApilGenerationCollectionItem:3480"},{"__ref":"ApilGenerationCollectionItem:3051"}]}}""",
                ),
            )!!,
        )
        assertEquals(listOf("3generation-facelift", "3generation"), gens.map { it.slug })
        assertEquals("seit 2024", gens[0].years)
        assertEquals(true, gens[0].facelift)
        assertEquals("2020–2024", gens[1].years)

        val variants = Adac.parseVariants(
            Adac.extractState(
                page(
                    """{"ApilCarCollectionItem:2":{"id":"2","name":"Hyundai Tucson 1.6 T-GDI Select (06/24 - 09/25)",
                          "fuelType":"Super","powerInHP":"160","sorting":5},
                        "ApilCarCollectionItem:1":{"id":"1","name":"Hyundai Tucson 1.6 CRDi (06/24 - 11/25)",
                          "fuelType":"Diesel","powerInHP":"136","sorting":1}}""",
                ),
            )!!,
        )
        assertEquals(listOf("1", "2"), variants.map { it.id })
        assertEquals("Super", variants[1].fuel)
        assertEquals("160", variants[1].hp)
    }

    @Test
    fun settingsUseAdacOrManualValue() {
        val manual = Settings(manualWearCentPerKm = 3.0)
        assertEquals(3.0, manual.wearCentPerKm, 1e-9)
        val adac = manual.copy(adacCost = cost(gridPage), kmPerYear = 12_000)
        assertEquals(3.912, adac.wearCentPerKm, 1e-9)
    }

    @Test
    fun settingsRoundTrip() {
        val s = Settings(
            manualWearCentPerKm = 3.0,
            kmPerYear = 12_000,
            adacCost = cost(gridPage).copy(months = 24, withDeterioration = true),
        )
        assertEquals(s, JsonCodec.settingsFromJson(JsonCodec.settingsToJson(s)))
        // Alte Einstellungen ohne die neuen Felder
        val old = JsonCodec.settingsFromJson("""{"apiKey":"","fuel":"E10","consumption":7,"liters":50,"wearCentPerKm":2.5}""")
        assertEquals(2.5, old.wearCentPerKm, 1e-9)
        assertEquals(15_000, old.kmPerYear)
        assertNull(old.adacCost)
    }
}
