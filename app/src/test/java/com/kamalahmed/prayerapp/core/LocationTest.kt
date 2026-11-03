package com.kamalahmed.prayerapp.core

import com.kamalahmed.prayerapp.core.location.GeoFence
import com.kamalahmed.prayerapp.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTest {
    @Test fun geofence() {
        // Sharjah -> Dubai (~13 km)
        assertTrue(GeoFence.shouldUpdate(25.3463, 55.4209, 25.2048, 55.2708, 10))
        assertFalse(GeoFence.shouldUpdate(25.3463, 55.4209, 25.35, 55.43, 10))
    }

    @Test fun defaultsAreSharjahDubai() {
        val s = AppSettings()
        assertEquals("Sharjah", s.cityName)
        assertEquals("DUBAI", s.calcMethod)
        assertFalse(s.onboardingDone)
    }
}
