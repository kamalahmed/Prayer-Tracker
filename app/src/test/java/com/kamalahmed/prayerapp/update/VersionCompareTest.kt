package com.kamalahmed.prayerapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    private fun v(s: String) = SemVer.parse(s)!!

    @Test fun parsesWithPrefixAndSuffix() {
        assertEquals(SemVer(0, 1, 0, listOf("beta", "1")), v("v0.1.0-beta.1"))
        assertEquals(SemVer(1, 2, 0), v("1.2"))
        assertEquals(SemVer(1, 0, 0), v("1.0.0+build.7"))
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse(""))
    }

    @Test fun orderingFollowsSemver() {
        val sorted = listOf("1.0.0", "1.0.0-alpha", "1.0.0-beta.11", "1.0.0-beta.2", "1.0.0-alpha.1", "1.0.0-rc.1", "0.9.9", "1.0.0-beta")
            .map(::v).sorted().map { it.toString() }
        assertEquals(listOf("0.9.9", "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0"), sorted)
    }

    @Test fun betaBeforeRelease() {
        assertTrue(v("0.1.0") > v("0.1.0-beta.1"))
        assertTrue(v("0.1.0-beta.2") > v("0.1.0-beta.1"))
        assertTrue(v("0.1.0-beta.10") > v("0.1.0-beta.9"))
        assertTrue(v("0.1.1-beta.1") > v("0.1.0"))
    }

    @Test fun isNewerUsesNameThenCode() {
        assertTrue(VersionCompare.isNewer("0.1.0-beta.2", null, "0.1.0-beta.1", 2))
        assertFalse(VersionCompare.isNewer("0.1.0-beta.1", null, "0.1.0-beta.1", 2))
        assertTrue(VersionCompare.isNewer("0.1.0-beta.1", 3, "0.1.0-beta.1", 2))
        assertFalse(VersionCompare.isNewer("0.1.0-beta.1", 9, "0.1.0", 2)) // name older wins over code
        assertFalse(VersionCompare.isNewer("0.0.9", null, "0.1.0-beta.1", 2))
    }
}
