package com.kamalahmed.prayerapp

import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.Prayer.ASR
import com.kamalahmed.prayerapp.core.model.Prayer.DHUHR
import com.kamalahmed.prayerapp.core.model.Prayer.FAJR
import com.kamalahmed.prayerapp.core.model.Prayer.ISHA
import com.kamalahmed.prayerapp.core.model.Prayer.MAGHRIB
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.PrayerStatus.LATE
import com.kamalahmed.prayerapp.tracker.PrayerStatus.MISSED
import com.kamalahmed.prayerapp.tracker.PrayerStatus.ON_TIME
import com.kamalahmed.prayerapp.tracker.Stats
import com.kamalahmed.prayerapp.tracker.StatsPeriod
import com.kamalahmed.prayerapp.tracker.StreakRun
import com.kamalahmed.prayerapp.tracker.TrackerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class StatsTest {
    private val today = LocalDate.of(2026, 11, 4) // Wednesday
    private fun all(s: PrayerStatus) = Prayer.obligatory.associateWith { s }

    @Test fun periodBoundaries() {
        assertEquals(LocalDate.of(2026, 11, 2) to LocalDate.of(2026, 11, 8), Stats.range(StatsPeriod.WEEK, today))
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30), Stats.range(StatsPeriod.MONTH, today))
        assertEquals(LocalDate.of(2024, 2, 1) to LocalDate.of(2024, 2, 29), Stats.range(StatsPeriod.MONTH, LocalDate.of(2024, 2, 10)))
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 12, 31), Stats.range(StatsPeriod.YEAR, today))
        // Sunday belongs to the week starting the previous Monday.
        assertEquals(LocalDate.of(2026, 11, 2), Stats.range(StatsPeriod.WEEK, LocalDate.of(2026, 11, 8)).first)
        assertEquals(LocalDate.of(2025, 12, 29), Stats.range(StatsPeriod.WEEK, LocalDate.of(2026, 1, 1)).first)
    }

    @Test fun missedCountsOnlyElapsedPrayers() {
        val data: TrackerData = mapOf(
            LocalDate.of(2026, 11, 2) to all(ON_TIME),
            LocalDate.of(2026, 11, 3) to mapOf(FAJR to ON_TIME, DHUHR to LATE, ASR to MISSED), // Maghrib/Isha unmarked = missed
            today to mapOf(FAJR to ON_TIME), // Dhuhr passed & unmarked = missed; Asr.. not yet due
        )
        val r = Stats.report(data, StatsPeriod.WEEK, today, today, passedToday = listOf(FAJR, DHUHR))
        assertEquals(12, r.totals.due)
        assertEquals(7, r.totals.onTime)
        assertEquals(1, r.totals.late)
        assertEquals(4, r.totals.missed)
        assertEquals(67, r.totals.completionPct)
        // Future days in the week contribute nothing.
        assertTrue(r.buckets.drop(3).all { it.counts.due == 0 })
        assertEquals(7, r.buckets.size)
    }

    @Test fun daysBeforeTrackingStartedAreNotMissed() {
        val data: TrackerData = mapOf(today to all(ON_TIME))
        val r = Stats.report(data, StatsPeriod.MONTH, today, today, Prayer.obligatory)
        assertEquals(5, r.totals.due); assertEquals(0, r.totals.missed); assertEquals(100, r.totals.completionPct)
    }

    @Test fun mostMissedRankingAndYearBuckets() {
        val data: TrackerData = mapOf(
            LocalDate.of(2026, 10, 1) to mapOf(FAJR to MISSED, DHUHR to ON_TIME, ASR to ON_TIME, MAGHRIB to ON_TIME, ISHA to LATE),
            LocalDate.of(2026, 10, 2) to mapOf(FAJR to MISSED, DHUHR to ON_TIME, ASR to MISSED, MAGHRIB to ON_TIME, ISHA to ON_TIME),
            LocalDate.of(2026, 10, 3) to mapOf(FAJR to LATE, DHUHR to ON_TIME, ASR to ON_TIME, MAGHRIB to ON_TIME, ISHA to ON_TIME),
        )
        val r = Stats.report(data, StatsPeriod.MONTH, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 3), Prayer.obligatory, Locale.US)
        assertEquals(listOf(FAJR, ASR, DHUHR, MAGHRIB, ISHA), r.perPrayer.map { it.prayer })
        assertEquals(2, r.perPrayer[0].counts.missed); assertEquals(67, r.perPrayer[0].missedPct)
        assertTrue(r.insights.first().contains("Fajr"))
        val y = Stats.report(data, StatsPeriod.YEAR, today, today, Prayer.obligatory, Locale.US)
        assertEquals(12, y.buckets.size)
        assertEquals("Oct", y.buckets[9].label)
        assertEquals(31 * 5, y.buckets[9].counts.due) // whole October elapsed after tracking began
        assertEquals(3 + (31 * 5 - 15), y.buckets[9].counts.missed)
        assertEquals(0, y.buckets[8].counts.due)
        // November: every day from the 1st to today is elapsed and unmarked.
        assertEquals(4 * 5, y.buckets[10].counts.due)
        assertEquals(20, y.buckets[10].counts.missed)
    }

    @Test fun trendComparesWithPreviousPeriod() {
        val data: TrackerData = mapOf(
            LocalDate.of(2026, 10, 26) to mapOf(FAJR to ON_TIME, DHUHR to MISSED, ASR to MISSED, MAGHRIB to MISSED, ISHA to MISSED),
            LocalDate.of(2026, 11, 2) to all(ON_TIME),
        )
        val r = Stats.report(data, StatsPeriod.WEEK, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), Prayer.obligatory)
        assertEquals(100, r.totals.completionPct)
        assertTrue(r.previous.due > 0)
        assertTrue(r.trend > 0)
    }

    @Test fun streakHistoryRuns() {
        val d = LocalDate.of(2026, 10, 1)
        val data: TrackerData = mapOf(
            d to all(ON_TIME), d.plusDays(1) to all(LATE), d.plusDays(2) to all(ON_TIME),
            d.plusDays(3) to mapOf(FAJR to MISSED),
            d.plusDays(5) to all(ON_TIME),
            d.plusDays(7) to all(ON_TIME), d.plusDays(8) to all(ON_TIME),
        )
        assertEquals(
            listOf(StreakRun(d.plusDays(7), d.plusDays(8)), StreakRun(d.plusDays(5), d.plusDays(5)), StreakRun(d, d.plusDays(2))),
            Stats.streakHistory(data),
        )
        assertEquals(3, Stats.streakHistory(data).last().days)
        assertTrue(Stats.streakHistory(emptyMap()).isEmpty())
    }

    @Test fun emptyState() {
        val r = Stats.report(emptyMap(), StatsPeriod.WEEK, today, today, listOf(FAJR))
        assertTrue(r.isEmpty)
        assertTrue(r.insights.isNotEmpty() || r.totals.due <= 1)
    }
}
