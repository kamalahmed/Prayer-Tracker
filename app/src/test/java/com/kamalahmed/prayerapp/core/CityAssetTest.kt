package com.kamalahmed.prayerapp.core

import com.kamalahmed.prayerapp.core.location.CityDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Regression for the beta.3 Settings > Location crash: aapt gunzips `*.gz` assets, so the APK contains
 * `assets/cities.tsv` (plain text) while the code opened `cities.tsv.gz`. The loader must accept the
 * packaged (plain) form and the asset name list must include the packaged name.
 */
class CityAssetTest {
    private val src = listOf("src/main/assets/cities.tsv.gz", "app/src/main/assets/cities.tsv.gz").map(::File).first { it.exists() }

    @Test fun loadsPlainTextAsPackagedInApk() {
        val plain = GZIPInputStream(src.inputStream()).use { it.readBytes() }
        val db = CityDatabase.load(plain.inputStream())
        assertTrue(db.size > 60_000)
        assertEquals("Sylhet", db.search("sylhet", 1).first().name)
    }

    @Test fun loadsGzipToo() {
        val db = src.inputStream().use { CityDatabase.load(it) }
        assertTrue(db.size > 60_000)
    }

    @Test fun assetNamesCoverPackagedName() {
        // AGP strips ".gz" when packaging; the stripped name must be tried.
        assertTrue(CityDatabase.ASSET_NAMES.contains(src.name.removeSuffix(".gz")))
    }

    /** If a release APK has been built, check that what the code opens really is inside it. */
    @Test fun releaseApkContainsOpenedAsset() {
        val dir = listOf("build/outputs/apk/release", "app/build/outputs/apk/release").map(::File).firstOrNull { it.isDirectory } ?: return
        val apk = dir.listFiles { f -> f.extension == "apk" }?.maxByOrNull { it.lastModified() } ?: return
        val names = java.util.zip.ZipFile(apk).use { z -> z.entries().asSequence().map { it.name }.toSet() }
        assertTrue("APK assets: ${names.filter { it.startsWith("assets/") }}",
            CityDatabase.ASSET_NAMES.any { "assets/$it" in names })
    }
}
