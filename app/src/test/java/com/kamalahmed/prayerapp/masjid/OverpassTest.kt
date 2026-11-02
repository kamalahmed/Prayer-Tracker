package com.kamalahmed.prayerapp.masjid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassTest {
    private val json = """{"elements":[
      {"type":"node","id":1,"lat":25.3800,"lon":55.4000,"tags":{"amenity":"place_of_worship","religion":"muslim","name":"Far Masjid"}},
      {"type":"way","id":2,"center":{"lat":25.3705,"lon":55.4005},"tags":{"building":"mosque","name":"مسجد النور","name:en":"Al Noor","addr:street":"King Faisal St","addr:city":"Sharjah"}},
      {"type":"node","id":3,"lat":25.37052,"lon":55.40052,"tags":{"amenity":"place_of_worship","religion":"muslim","name":"مسجد النور"}},
      {"type":"node","id":4,"lat":25.3710,"lon":55.3990,"tags":{"amenity":"place_of_worship","religion":"muslim","name":"Masjid Bilal","name:ar":"مسجد بلال"}},
      {"type":"relation","id":5,"tags":{"name":"no coords"}}
    ]}"""

    @Test fun parsesSortsAndDedupes() {
        val list = Overpass.parse(json, 25.37, 55.40)
        assertEquals(listOf("مسجد النور", "Masjid Bilal", "Far Masjid"), list.map { it.name })
        assertEquals("King Faisal St, Sharjah", list[0].address)
        assertEquals("مسجد بلال", list[1].nameAr)
        assertTrue(list.zipWithNext().all { it.first.distanceM <= it.second.distanceM })
        assertTrue(list[2].distanceM in 1050..1150)
    }

    @Test fun queryAndCellKey() {
        val q = Overpass.query(25.37, 55.40, 3000)
        assertTrue(q.contains("[\"religion\"=\"muslim\"](around:3000,25.37000,55.40000)"))
        assertTrue(q.contains("[\"building\"=\"mosque\"]"))
        assertEquals("25.37_55.40", Overpass.cellKey(25.3712, 55.4031))
        assertEquals(Overpass.cellKey(25.3712, 55.4031), Overpass.cellKey(25.3740, 55.3990))
        assertEquals("850 m", Overpass.formatDistance(850)); assertEquals("1.2 km", Overpass.formatDistance(1234))
        assertEquals(2, Overpass.endpoints.size)
        assertEquals(0, Overpass.parse("""{"elements":[]}""", 0.0, 0.0).size)
    }
}
