package com.kamalahmed.prayerapp.core.location

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.zip.GZIPInputStream

/**
 * Offline world city database bundled as `assets/cities.tsv.gz`, built from GeoNames `cities5000`
 * (CC BY 4.0, https://www.geonames.org/) by `tools/build_cities.py`. ~70k places with population >= 5000.
 *
 * Search is accent/case-insensitive, matches name, ASCII name and alternate names (prefix, word prefix,
 * then substring), and ranks by match quality then population. "city, country" queries are supported.
 */
class CityDatabase private constructor(private val entries: Array<Entry>) {

    class Entry(val city: City, val keys: Array<String>)

    val size get() = entries.size

    fun search(query: String, limit: Int = 50): List<City> {
        val parts = query.split(',').map { fold(it) }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return entries.take(limit).map { it.city }
        val q = parts[0]
        val qualifier = parts.drop(1).joinToString(" ")
        val scored = ArrayList<Pair<Int, Entry>>()
        for (e in entries) {
            val tier = matchTier(e.keys, q)
            if (tier < 0) continue
            if (qualifier.isNotEmpty() && !matchesQualifier(e.city, qualifier)) continue
            scored += tier to e
        }
        // entries are already in population order; stable sort by tier keeps that within a tier
        return scored.sortedBy { it.first }.take(limit).map { it.second.city }
    }

    /** Nearest bundled city (used to pick a time zone for manually entered coordinates). */
    fun nearest(lat: Double, lng: Double): City? =
        entries.minByOrNull { com.kamalahmed.prayerapp.core.prayer.Geo.distanceKm(lat, lng, it.city.lat, it.city.lng) }?.city

    private fun matchesQualifier(c: City, q: String): Boolean =
        fold(c.country).startsWith(q) || fold(c.region).startsWith(q) || c.countryCode.equals(q, ignoreCase = true) ||
            fold(c.country).split(' ').any { it.startsWith(q) } || fold(c.region).split(' ').any { it.startsWith(q) }

    companion object {
        /** Lower is better: 0 exact (name or alternate), 1 name prefix, 2 alternate prefix, 3 word prefix, 4 substring.
         *  keys[0] = folded name, keys[1] = folded ascii name, rest = folded alternate names. */
        internal fun matchTier(keys: Array<String>, q: String): Int {
            var best = -1
            fun take(t: Int) { if (best < 0 || t < best) best = t }
            for ((i, k) in keys.withIndex()) {
                val alt = i >= 2
                when {
                    k == q -> take(if (alt && q.length < 4) 2 else 0)
                    k.startsWith(q) -> take(if (alt) 2 else 1)
                    k.contains(" $q") || k.contains("-$q") -> take(3)
                    q.length >= 3 && k.contains(q) -> take(4)
                }
                if (best == 0) return 0
            }
            return best
        }

        private val MARKS = Regex("\\p{Mn}+")
        fun fold(s: String): String =
            MARKS.replace(Normalizer.normalize(s.trim(), Normalizer.Form.NFKD), "").lowercase()
                .replace('\u2019', '\'').replace(Regex("\\s+"), " ")

        fun load(input: InputStream): CityDatabase {
            val reader = BufferedReader(InputStreamReader(GZIPInputStream(input), Charsets.UTF_8), 1 shl 16)
            var tz: List<String> = emptyList()
            val countries = HashMap<String, String>()
            val out = ArrayList<Entry>(72_000)
            reader.useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t')
                    when {
                        p[0] == "#tz" -> tz = p.drop(1)
                        p[0] == "#cc" -> p.drop(1).forEach { kv -> val i = kv.indexOf('='); if (i > 0) countries[kv.substring(0, i)] = kv.substring(i + 1) }
                        p.size >= 9 -> {
                            val cc = p[3]
                            val city = City(
                                name = p[0], country = countries[cc] ?: cc, lat = p[5].toDouble(), lng = p[6].toDouble(),
                                tz = tz[p[7].toInt()], region = p[4], countryCode = cc, population = p[8].toLongOrNull() ?: 0,
                            )
                            val keys = ArrayList<String>(4)
                            keys += fold(p[0]); keys += fold(p[1])
                            if (p[2].isNotEmpty()) p[2].split('|').forEach { keys += it }
                            out += Entry(city, keys.toTypedArray())
                        }
                    }
                }
            }
            return CityDatabase(out.toTypedArray())
        }

        @Volatile private var instance: CityDatabase? = null

        /** Loads once from app assets (about 0.5 s on a mid-range phone; call off the main thread). */
        fun get(context: android.content.Context): CityDatabase =
            instance ?: synchronized(this) {
                instance ?: context.assets.open("cities.tsv.gz").use { load(it) }.also { instance = it }
            }
    }
}
