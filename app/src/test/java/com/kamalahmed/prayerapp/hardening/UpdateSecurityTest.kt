package com.kamalahmed.prayerapp.hardening

import com.kamalahmed.prayerapp.update.ReleaseParser
import com.kamalahmed.prayerapp.update.UpdateSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateSecurityTest {
    private val pkg = "com.kamalahmed.prayerapp"
    private val key = setOf("022dddfbdf9c07eef5f47e4cdf141782fbbeb2559ff7a1691ea381333dce481d")

    @Test fun onlyHttpsGithubUrlsAreTrusted() {
        assertTrue(UpdateSecurity.isTrustedUrl("https://github.com/kamalahmed/Prayer-Tracker/releases/download/v1/a.apk"))
        assertTrue(UpdateSecurity.isTrustedUrl("https://release-assets.githubusercontent.com/x"))
        assertTrue(UpdateSecurity.isTrustedUrl("https://objects.githubusercontent.com/x"))
        assertFalse(UpdateSecurity.isTrustedUrl("http://github.com/a.apk"))
        assertFalse(UpdateSecurity.isTrustedUrl("https://github.com.evil.example/a.apk"))
        assertFalse(UpdateSecurity.isTrustedUrl("https://evil.example/a.apk"))
        assertFalse(UpdateSecurity.isTrustedUrl("https://user@github.com/a.apk"))
        assertFalse(UpdateSecurity.isTrustedUrl("file:///sdcard/a.apk"))
        assertFalse(UpdateSecurity.isTrustedUrl("not a url"))
        assertFalse(UpdateSecurity.isTrustedUrl(null))
        assertFalse(UpdateSecurity.isTrustedUrl(""))
    }

    @Test(expected = IOException::class) fun missingHashRefusesInstall() { UpdateSecurity.requireSha(null) }
    @Test(expected = IOException::class) fun malformedHashRefusesInstall() { UpdateSecurity.requireSha("abc123") }
    @Test fun validHashIsNormalised() { assertEquals("a".repeat(64), UpdateSecurity.requireSha(" " + "A".repeat(64) + "\n")) }

    @Test fun apkChecks() {
        assertNull(UpdateSecurity.checkApk(pkg, 6, key, pkg, 5, key))
        assertNotNull("downgrade", UpdateSecurity.checkApk(pkg, 4, key, pkg, 5, key))
        assertNotNull("same version reinstall", UpdateSecurity.checkApk(pkg, 5, key, pkg, 5, key))
        assertNotNull("other app", UpdateSecurity.checkApk("com.evil", 6, key, pkg, 5, key))
        assertNotNull("not an apk", UpdateSecurity.checkApk(null, -1, emptySet(), pkg, 5, key))
        assertNotNull("other key", UpdateSecurity.checkApk(pkg, 6, setOf("ff".repeat(32)), pkg, 5, key))
        assertNotNull("unsigned", UpdateSecurity.checkApk(pkg, 6, emptySet(), pkg, 5, key))
    }

    @Test fun malformedReleaseJsonIsRejectedOrSkipped() {
        assertTrue(runCatching { ReleaseParser.parseList("{not json") }.isFailure)
        // One bad entry (no tag_name) is skipped, the good one survives.
        val list = ReleaseParser.parseList("""[{"name":"x"},{"tag_name":"v9.0.0","assets":[]}]""")
        assertEquals(listOf("v9.0.0"), list.map { it.tag })
        // Hostile notes: no crash, no hash picked up from garbage.
        val r = ReleaseParser.parseList("""[{"tag_name":"v9.0.0","body":"SHA-256: zz versionCode: 99999999999999"}]""").single()
        assertNull(r.notesSha256); assertNull(r.versionCode)
    }

    @Test fun downgradeIsNeverPicked() {
        val list = ReleaseParser.parseList("""[{"tag_name":"v0.1.0-beta.4","assets":[{"name":"a.apk","browser_download_url":"https://github.com/a.apk","size":1}],"body":"versionCode: 5"}]""")
        assertNull(ReleaseParser.pickUpdate(list, true, "0.1.0-beta.5", 6))
    }
}
