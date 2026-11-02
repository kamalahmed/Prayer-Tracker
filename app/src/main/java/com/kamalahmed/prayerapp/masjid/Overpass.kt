package com.kamalahmed.prayerapp.masjid

import org.json.JSONObject
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Mosque(
    val id: String,
    val name: String,
    val nameAr: String?,
    val lat: Double,
    val lng: Double,
    val address: String?,
    val distanceM: Int,
)

/** Pure helpers for the OpenStreetMap Overpass mosque search (unit tested). */
object Overpass {
    val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    fun query(lat: Double, lng: Double, radiusM: Int): String {
        val a = String.format(Locale.US, "around:%d,%.5f,%.5f", radiusM, lat, lng)
        return "[out:json][timeout:20];(" +
            "nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]($a);" +
            "nwr[\"building\"=\"mosque\"]($a);" +
            ");out center tags 80;"
    }

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** ~1.1 km grid cell used as the cache key. */
    fun cellKey(lat: Double, lng: Double): String = String.format(Locale.US, "%.2f_%.2f", lat, lng)

    fun parse(json: String, fromLat: Double, fromLng: Double): List<Mosque> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = LinkedHashMap<String, Mosque>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val center = e.optJSONObject("center")
            val lat = if (e.has("lat")) e.getDouble("lat") else center?.optDouble("lat") ?: continue
            val lng = if (e.has("lon")) e.getDouble("lon") else center?.optDouble("lon") ?: continue
            if (lat.isNaN() || lng.isNaN()) continue
            val nameAr = tags.optString("name:ar").ifBlank { null }
            val name = tags.optString("name").ifBlank { tags.optString("name:en").ifBlank { nameAr ?: "Masjid" } }
            val street = listOf(tags.optString("addr:housenumber"), tags.optString("addr:street")).filter { it.isNotBlank() }.joinToString(" ")
            val address = listOf(street, tags.optString("addr:city")).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
            val id = e.optString("type") + "/" + e.optLong("id")
            out[id] = Mosque(id, name, nameAr?.takeIf { it != name }, lat, lng, address, distanceM(fromLat, fromLng, lat, lng).roundToInt())
        }
        // A mosque mapped both as a building and as a node: drop near-duplicates with the same name.
        val sorted = out.values.sortedBy { it.distanceM }
        val kept = ArrayList<Mosque>()
        for (m in sorted) if (kept.none { it.name == m.name && distanceM(it.lat, it.lng, m.lat, m.lng) < 60 }) kept += m
        return kept
    }

    fun formatDistance(m: Int): String = if (m < 1000) "$m m" else String.format(Locale.US, "%.1f km", m / 1000.0)
}
