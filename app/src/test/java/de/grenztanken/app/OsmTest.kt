package de.grenztanken.app

import de.grenztanken.app.data.Osm
import de.grenztanken.app.model.Country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OsmTest {

    // Gekürzte Antwort von Nominatim (amenity=fuel, Umkreis Zittau, abgerufen 26.10.2026)
    private val fuelJson = """
        [
          {"osm_type":"node","osm_id":12406782556,"lat":"50.9203597","lon":"14.6079343","name":"EurOil",
           "address":{"road":"Husitská","town":"Varnsdorf","postcode":"470 47","country_code":"cz"},
           "extratags":{"fuel:diesel":"yes"}},
          {"osm_type":"way","osm_id":278497116,"lat":"50.8767","lon":"14.8258","name":"PCH",
           "address":{"road":"Aleja Trzech Państw","village":"Kopaczów","postcode":"59-921","country_code":"pl"}},
          {"osm_type":"node","osm_id":1,"lat":"50.8793","lon":"14.8251","name":"",
           "address":{"road":"Aleja Trzech Państw","village":"Kopaczów","country_code":"pl"},
           "extratags":{"brand":"Orlen"}},
          {"osm_type":"node","osm_id":2,"lat":"50.8938","lon":"14.8017","name":"Aral",
           "address":{"road":"Theodor-Körner-Allee","house_number":"2","city":"Zittau","country_code":"de"}},
          {"osm_type":"way","osm_id":278497116,"lat":"50.8767","lon":"14.8258","name":"PCH",
           "address":{"country_code":"pl"}}
        ]
    """.trimIndent()

    // Aral Zittau als Suchmittelpunkt
    private val lat = 50.8938
    private val lng = 14.8017

    @Test
    fun nurTschechienUndPolenNachEntfernungSortiert() {
        val list = Osm.parseFuel(fuelJson, lat, lng)
        assertEquals(listOf("Orlen", "PCH", "EurOil"), list.map { it.name })
        assertEquals(listOf(Country.PL, Country.PL, Country.CZ), list.map { it.country })
        assertEquals("Husitská, 470 47 Varnsdorf", list[2].address)
        assertEquals(2.3, list[0].distKm, 0.1)
        assertEquals(2.5, list[1].distKm, 0.1)
    }

    @Test
    fun namensersatz() {
        assertEquals("Orlen", Osm.displayName("", "Orlen", "Hlavní"))
        assertEquals("Tankstelle Hlavní", Osm.displayName("", "", "Hlavní"))
        assertEquals("Orlen Brandov", Osm.displayName("Orlen Brandov", "Orlen", ""))
        assertEquals("Shell – Autohof", Osm.displayName("Autohof", "Shell", ""))
    }

    @Test
    fun ort() {
        val p = Osm.parsePlace("""[{"lat":"50.8519","lon":"14.8616","name":"Hrádek nad Nisou"}]""")
        assertEquals(50.8519, p!!.first, 1e-9)
        assertEquals(14.8616, p.second, 1e-9)
        assertNull(Osm.parsePlace("[]"))
    }

    @Test
    fun route() {
        // Antwort von router.project-osrm.org: Aral Zittau -> PCH Kopaczów
        val ok = """{"code":"Ok","routes":[{"legs":[],"duration":266.9,"distance":2874.3}],"waypoints":[]}"""
        assertEquals(2.8743, Osm.parseRouteKm(ok)!!, 1e-9)
        assertNull(Osm.parseRouteKm("""{"code":"NoRoute","message":"Impossible route"}"""))
    }

    @Test
    fun luftlinie() {
        // Zittau – Hrádek nad Nisou, ca. 6 km
        assertEquals(6.0, Osm.distanceKm(50.8938, 14.8017, 50.8519, 14.8616), 0.5)
    }
}
