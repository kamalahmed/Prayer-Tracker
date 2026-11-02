package com.kamalahmed.prayerapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {
    private val json = javaClass.classLoader!!.getResource("releases_sample.json")!!.readText()
    private val list = ReleaseParser.parseList(json)

    @Test fun parsesAllReleases() {
        assertEquals(5, list.size)
        val b2 = list[0]
        assertEquals("v0.2.0-beta.2", b2.tag)
        assertEquals("0.2.0-beta.2", b2.version)
        assertTrue(b2.prerelease)
        assertEquals("PrayerApp-v0.2.0-beta.2.apk", b2.apk!!.name)
        assertEquals(4_500_000L, b2.apk!!.size)
        assertEquals("PrayerApp-v0.2.0-beta.2.apk.sha256", b2.sha256Asset!!.name)
        assertEquals(5, b2.versionCode)
    }

    @Test fun handlesNullBodyEmptyNameAndNotesChecksum() {
        assertEquals("", list[1].notes)
        assertTrue(list[1].draft)
        assertEquals("v0.1.1", list[2].name)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", list[2].notesSha256)
        assertNull(list[2].sha256Asset)
        assertNull(list[4].apk)
    }

    @Test fun picksNewestWithBeta() {
        assertEquals("v0.2.0-beta.2", ReleaseParser.pickUpdate(list, true, "0.1.0-beta.1", 2)?.tag)
    }

    @Test fun picksStableOnlyWhenBetaDisabledAndSkipsDrafts() {
        assertEquals("v0.1.1", ReleaseParser.pickUpdate(list, false, "0.1.0-beta.1", 2)?.tag)
    }

    @Test fun noneWhenUpToDate() {
        assertNull(ReleaseParser.pickUpdate(list, true, "0.2.0-beta.2", 5))
        assertNull(ReleaseParser.pickUpdate(list, false, "0.1.1", 4))
    }

    @Test fun parsesShaFile() {
        val h = "a".repeat(64)
        assertEquals(h, ReleaseParser.parseShaFile("$h  PrayerApp.apk\n"))
        assertEquals(h, ReleaseParser.parseShaFile(h.uppercase()))
        assertNull(ReleaseParser.parseShaFile("nope"))
        assertFalse(list[0].notes.isEmpty())
    }
}
