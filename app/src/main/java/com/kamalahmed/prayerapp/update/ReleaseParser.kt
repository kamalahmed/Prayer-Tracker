package com.kamalahmed.prayerapp.update

import org.json.JSONArray
import org.json.JSONObject

data class ReleaseAsset(val name: String, val url: String, val size: Long, val contentType: String)

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val htmlUrl: String,
    val publishedAt: String,
    val apk: ReleaseAsset?,
    val sha256Asset: ReleaseAsset?,
    /** SHA-256 found in the release notes (e.g. "SHA-256: abc…" or "sha256 abc… file.apk"). */
    val notesSha256: String?,
    /** Optional "versionCode: N" line in the notes. */
    val versionCode: Int?,
) {
    val version: String get() = tag.removePrefix("v").removePrefix("V")
}

/** Parses the GitHub "list releases" / "get release" JSON. Pure (org.json only) so it is unit-testable. */
object ReleaseParser {
    private val SHA_RX = Regex("""(?i)sha-?256[^0-9a-f]{0,40}([0-9a-f]{64})""")
    private val CODE_RX = Regex("""(?i)version\s*code\s*[:=]?\s*(\d{1,9})""")
    private val HEX64 = Regex("""\b([0-9a-fA-F]{64})\b""")

    fun parseList(json: String): List<ReleaseInfo> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { runCatching { parseRelease(arr.getJSONObject(it)) }.getOrNull() }
    }

    fun parseRelease(o: JSONObject): ReleaseInfo {
        val assets = o.optJSONArray("assets") ?: JSONArray()
        val list = (0 until assets.length()).map { i ->
            val a = assets.getJSONObject(i)
            ReleaseAsset(a.optString("name"), a.optString("browser_download_url"), a.optLong("size", -1), a.optString("content_type"))
        }
        val apk = list.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        val sha = list.firstOrNull { apk != null && it.name.equals(apk.name + ".sha256", true) }
            ?: list.firstOrNull { it.name.endsWith(".sha256", true) || it.name.endsWith(".sha256sum", true) }
        val body = o.optString("body").let { if (it == "null") "" else it }
        return ReleaseInfo(
            tag = o.getString("tag_name"),
            name = o.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: o.getString("tag_name"),
            notes = body,
            prerelease = o.optBoolean("prerelease"),
            draft = o.optBoolean("draft"),
            htmlUrl = o.optString("html_url"),
            publishedAt = o.optString("published_at"),
            apk = apk,
            sha256Asset = sha,
            notesSha256 = SHA_RX.find(body)?.groupValues?.get(1)?.lowercase(),
            versionCode = CODE_RX.find(body)?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    /** Extracts the hash from a `sha256sum`-style file ("<hex>  name.apk") or a bare hex string. */
    fun parseShaFile(text: String): String? = HEX64.find(text)?.groupValues?.get(1)?.lowercase()

    /** Newest installable release (has an APK, not a draft, beta only if allowed) that is newer than installed. */
    fun pickUpdate(releases: List<ReleaseInfo>, includeBeta: Boolean, localName: String, localCode: Int): ReleaseInfo? =
        releases.asSequence()
            .filter { !it.draft && it.apk != null && (includeBeta || !it.prerelease) }
            .filter { SemVer.parse(it.version) != null }
            .sortedWith(compareByDescending<ReleaseInfo> { SemVer.parse(it.version)!! }.thenByDescending { it.versionCode ?: 0 })
            .firstOrNull { VersionCompare.isNewer(it.version, it.versionCode, localName, localCode) }
}
