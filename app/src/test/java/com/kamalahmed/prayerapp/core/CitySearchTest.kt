package com.kamalahmed.prayerapp.core

import com.kamalahmed.prayerapp.core.location.CityDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.time.ZoneId

/** Runs against the real bundled GeoNames asset. */
class CitySearchTest {
    companion object {
        lateinit var db: CityDatabase
        @BeforeClass @JvmStatic fun load() {
            val f = listOf("src/main/assets/cities.tsv.gz", "app/src/main/assets/cities.tsv.gz").map(::File).first { it.exists() }
            db = f.inputStream().use { CityDatabase.load(it) }
        }
    }

    private fun top(q: String) = db.search(q, 5).first()

    @Test fun datasetIsWorldwide() {
        assertTrue("size ${db.size}", db.size > 60_000)
    }

    @Test fun findsExpectedCitiesFirst() {
        val expected = mapOf(
            "sylhet" to ("Sylhet" to "BD"), "dhaka" to ("Dhaka" to "BD"), "sharjah" to ("Sharjah" to "AE"),
            "dubai" to ("Dubai" to "AE"), "london" to ("London" to "GB"), "new york" to ("New York City" to "US"),
            "tokyo" to ("Tokyo" to "JP"), "istanbul" to ("Istanbul" to "TR"), "karachi" to ("Karachi" to "PK"),
            "jakarta" to ("Jakarta" to "ID"), "lagos" to ("Lagos" to "NG"),
        )
        expected.forEach { (q, e) ->
            val c = top(q)
            assertEquals("query '$q' -> ${c.display}", e, c.name to c.countryCode)
        }
    }

    @Test fun meccaFindsSaudiMakkah() {
        assertEquals("SA", top("mecca").countryCode)
        assertEquals("SA", top("makkah").countryCode)
        assertEquals("Asia/Riyadh", top("mecca").tz)
    }

    @Test fun bangladeshCities() {
        listOf("dhaka", "sylhet", "chittagong", "chattogram", "rajshahi", "khulna", "comilla", "moulvibazar").forEach { q ->
            assertTrue("'$q' not found in BD", db.search(q, 5).any { it.countryCode == "BD" })
        }
        val s = top("sylhet")
        assertEquals("Asia/Dhaka", s.tz)
        assertEquals(24.899, s.lat, 0.05); assertEquals(91.872, s.lng, 0.05)
        assertEquals("Sylhet, Sylhet Division, Bangladesh", s.display)
    }

    @Test fun accentAndCaseInsensitive() {
        assertEquals("Zürich", top("zurich").name)
        assertEquals("Zürich", top("ZÜRICH").name)
        assertEquals("São Paulo", top("sao paulo").name)
    }

    @Test fun prefixSearchRanksByPopulation() {
        val r = db.search("syl", 10)
        assertEquals("Sylhet", r.first().name)
        val k = db.search("kar", 3)
        assertEquals("Karachi", k.first().name)
    }

    @Test fun countryQualifier() {
        val r = db.search("hyderabad, pakistan", 3)
        assertTrue(r.isNotEmpty() && r.all { it.countryCode == "PK" })
        assertTrue(db.search("springfield, US", 5).all { it.countryCode == "US" })
    }

    @Test fun nearestAndZonesValid() {
        assertEquals("Asia/Dhaka", db.nearest(24.9, 91.87)!!.tz)
        db.search("", 500).forEach { ZoneId.of(it.tz) }
        listOf("sylhet", "tokyo", "lagos", "new york").forEach { ZoneId.of(top(it).tz) }
    }

    @Test fun flagAndNoResults() {
        assertEquals("\uD83C\uDDE7\uD83C\uDDE9", top("sylhet").flag)
        assertTrue(db.search("qqzzxxj").isEmpty())
    }
}
