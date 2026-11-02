package com.kamalahmed.prayerapp.masjid

import android.content.Context
import com.kamalahmed.prayerapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MasjidResult {
    data class Found(val mosques: List<Mosque>, val fromCache: Boolean, val radiusM: Int) : MasjidResult
    data class Error(val offline: Boolean, val message: String) : MasjidResult
}

/** Nearby mosques from OpenStreetMap via the free Overpass API (no key). Cached 24h per ~1 km cell. */
@Singleton
class MasjidRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val cacheDir get() = File(context.cacheDir, "masjid").apply { mkdirs() }

    suspend fun nearby(lat: Double, lng: Double, force: Boolean = false): MasjidResult = withContext(Dispatchers.IO) {
        val key = Overpass.cellKey(lat, lng)
        val cache = File(cacheDir, "$key.json")
        if (!force && cache.exists() && System.currentTimeMillis() - cache.lastModified() < CACHE_MS) {
            runCatching { cache.readText() }.getOrNull()?.let { body ->
                val r = body.substringBefore('\n').toIntOrNull() ?: RADII.first()
                return@withContext MasjidResult.Found(Overpass.parse(body.substringAfter('\n'), lat, lng), true, r)
            }
        }
        var lastError: Exception? = null
        for (radius in RADII) {
            val body = try { fetch(Overpass.query(lat, lng, radius)) } catch (e: Exception) { lastError = e; null } ?: break
            val list = Overpass.parse(body, lat, lng)
            if (list.isNotEmpty() || radius == RADII.last()) {
                runCatching { cache.writeText("$radius\n$body") }
                return@withContext MasjidResult.Found(list, false, radius)
            }
        }
        // Network failed: fall back to a stale cache if we have one.
        if (cache.exists()) runCatching { cache.readText() }.getOrNull()?.let { body ->
            return@withContext MasjidResult.Found(Overpass.parse(body.substringAfter('\n'), lat, lng), true, body.substringBefore('\n').toIntOrNull() ?: RADII.first())
        }
        val offline = lastError is java.net.UnknownHostException || lastError is java.net.ConnectException
        MasjidResult.Error(offline, if (offline) "You appear to be offline." else "OpenStreetMap servers are busy. Try again in a minute.")
    }

    private fun fetch(q: String): String {
        var last: Exception? = null
        for (ep in Overpass.endpoints) {
            try {
                val c = (URL(ep).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    connectTimeout = 10_000; readTimeout = 25_000
                    setRequestProperty("User-Agent", "PrayerApp/${BuildConfig.VERSION_NAME} (+https://github.com/kamalahmed/Prayer-Tracker)")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                try {
                    c.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                    if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} from $ep")
                    return c.inputStream.bufferedReader().use { it.readText() }
                } finally { c.disconnect() }
            } catch (e: Exception) { last = e }
        }
        throw last ?: IOException("no endpoint")
    }

    companion object {
        const val CACHE_MS = 24L * 60 * 60 * 1000
        val RADII = listOf(3_000, 10_000)
    }
}
