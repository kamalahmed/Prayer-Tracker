package com.kamalahmed.prayerapp

import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.Stats
import com.kamalahmed.prayerapp.tracker.StatsPeriod
import com.kamalahmed.prayerapp.ui.streaks.labelStep
import com.kamalahmed.prayerapp.ui.streaks.niceMax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Year view regression (beta.5 showed months before tracking as 0 bars and a broken grid). */
class StatsTrackedTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val data = mapOf(LocalDate.of(2026, 10, 20) to mapOf(Prayer.FAJR to PrayerStatus.ON_TIME))

    @Test fun monthsBeforeTrackingAreUntracked() {
        val r = Stats.report(data, StatsPeriod.YEAR, today, today, Prayer.obligatory)
        assertEquals(12, r.buckets.size)
        assertEquals(listOf(false, false, false, false, false, false, false, false, false, true, true, false), r.buckets.map { it.tracked })
        assertEquals(LocalDate.of(2026, 10, 20), r.firstDay)
        assertNull(r.buckets.first().onTimeRate)
        assertEquals(365, r.heatmap.size)
        assertNull(r.heatmap.first().second)
    }

    @Test fun weekDaysAfterTodayUntracked() {
        val r = Stats.report(data, StatsPeriod.WEEK, today, today, Prayer.obligatory)
        assertTrue(r.buckets.last().tracked) // Sunday 8 Nov = today
        val m = Stats.report(data, StatsPeriod.MONTH, today, today, Prayer.obligatory)
        assertFalse(m.buckets.last().tracked) // 30 Nov is in the future
    }

    @Test fun axisHelpers() {
        assertEquals(5, niceMax(0)); assertEquals(5, niceMax(5)); assertEquals(10, niceMax(7)); assertEquals(200, niceMax(150))
        assertEquals(1, labelStep(7, 300f)); assertTrue(labelStep(31, 300f) >= 3)
    }
}
