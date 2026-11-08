package com.kamalahmed.prayerapp.phase17

import com.kamalahmed.prayerapp.update.*
import org.junit.Assert.*
import org.junit.Test

class AutoUpdateTest {
    @Test fun actualPublishedPrereleasesAreDetectedFromEveryOlderBeta() {
        val releases = ReleaseParser.parseList(javaClass.classLoader!!.getResource("releases_beta8_actual.json")!!.readText())
        for (beta in 1..7) {
            val r = ReleaseParser.pickUpdate(releases,true,"0.1.0-beta.$beta",beta+1)!!
            assertEquals("v0.1.0-beta.8",r.tag); assertEquals(9,r.versionCode)
            assertEquals("625851ad6c429db37675ae332796a1e6cce836bb082b8850c2835745d62eccbb",r.notesSha256)
        }
        assertNull(ReleaseParser.pickUpdate(releases,false,"0.1.0-beta.1",2))
        assertNull(ReleaseParser.pickUpdate(releases,true,"0.1.0-beta.8",9))
    }
    @Test fun resumeChecksAreLimitedToFiveMinutesAndClockRollbackRecovers() {
        val now = 1_000_000L
        assertTrue(UpdateThrottle.due(now,0))
        assertFalse(UpdateThrottle.due(now+299999,now))
        assertTrue(UpdateThrottle.due(now+300000,now))
        assertTrue(UpdateThrottle.due(now-1,now))
    }
    @Test fun notesVersionCodeBreaksEqualVersionTieAndRoundTripsForWorker() {
        val release = ReleaseParser.parseList("""[{"tag_name":"v1.0.0-beta.1","prerelease":true,"body":"versionCode: 11","assets":[{"name":"update.apk","browser_download_url":"https://github.com/x/update.apk","size":99}]}]""").single()
        assertEquals(release,ReleaseParser.parseRelease(ReleaseParser.toJson(release)))
        assertEquals(release,ReleaseParser.pickUpdate(listOf(release),true,"1.0.0-beta.1",10))
    }
}
