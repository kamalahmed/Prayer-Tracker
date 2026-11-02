package com.kamalahmed.prayerapp

import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.StreakCalculator
import com.kamalahmed.prayerapp.tracker.TrackerData
import com.kamalahmed.prayerapp.tracker.TrackerRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class StreakTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val full = Prayer.obligatory.associateWith { PrayerStatus.ON_TIME }

    private fun days(vararg offsets: Int, day: Map<Prayer, PrayerStatus> = full): TrackerData =
        offsets.associate { today.minusDays(it.toLong()) to day }

    @Test fun emptyDataIsZero() {
        val s = StreakCalculator.compute(emptyMap(), today, emptyList())
        assertEquals(0, s.currentDays); assertEquals(0, s.bestDays); assertEquals(0, s.currentPrayers)
    }

    @Test fun incompleteTodayDoesNotBreakStreak() {
        val data = days(1, 2, 3) + (today to mapOf(Prayer.FAJR to PrayerStatus.ON_TIME))
        val s = StreakCalculator.compute(data, today, listOf(Prayer.FAJR))
        assertEquals(3, s.currentDays)
    }

    @Test fun completeTodayCounts() {
        val s = StreakCalculator.compute(days(0, 1, 2), today, Prayer.obligatory)
        assertEquals(3, s.currentDays)
    }

    @Test fun gapBreaksCurrentButBestRemembers() {
        val data = days(1, 2) + days(5, 6, 7, 8)
        val s = StreakCalculator.compute(data, today, emptyList())
        assertEquals(2, s.currentDays)
        assertEquals(4, s.bestDays)
    }

    @Test fun lateCountsAsPrayedMissedBreaks() {
        val late = full + (Prayer.ASR to PrayerStatus.LATE)
        val missed = full + (Prayer.ASR to PrayerStatus.MISSED)
        assertEquals(1, StreakCalculator.compute(days(1, day = late), today, emptyList()).currentDays)
        assertEquals(0, StreakCalculator.compute(days(1, day = missed), today, emptyList()).currentDays)
    }

    @Test fun prayerLevelStreak() {
        // Yesterday all 5, today Fajr + Dhuhr prayed, Asr just started and unmarked
        val data = days(1) + (today to mapOf(Prayer.FAJR to PrayerStatus.ON_TIME, Prayer.DHUHR to PrayerStatus.LATE))
        val s = StreakCalculator.compute(data, today, listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR))
        assertEquals(7, s.currentPrayers)
    }

    @Test fun weekStats() {
        // Monday 2 Nov 2026 is the week start; Fajr, Dhuhr passed, Fajr on time
        val data = mapOf(today to mapOf(Prayer.FAJR to PrayerStatus.ON_TIME))
        val s = StreakCalculator.compute(data, today, listOf(Prayer.FAJR, Prayer.DHUHR))
        assertEquals(1, s.week.prayed); assertEquals(2, s.week.possible); assertEquals(50, s.week.prayedPct)
    }

    @Test fun heatmapSize() {
        val s = StreakCalculator.compute(days(0), today, Prayer.obligatory)
        assertEquals(84, s.heatmap.size)
        assertEquals(5, s.heatmap.last().second)
    }

    @Test fun onTimeVsLateWindow() {
        val sched = PrayerSchedule(AppSettings().toCalcParams())
        val z = ZoneId.of("Asia/Dubai")
        assertEquals(PrayerStatus.ON_TIME, TrackerRepository.statusFor(sched, today, Prayer.FAJR, ZonedDateTime.of(today, LocalTime.of(6, 0), z)))
        assertEquals(PrayerStatus.LATE, TrackerRepository.statusFor(sched, today, Prayer.FAJR, ZonedDateTime.of(today, LocalTime.of(7, 0), z)))
        assertEquals(PrayerStatus.ON_TIME, TrackerRepository.statusFor(sched, today, Prayer.ISHA, ZonedDateTime.of(today, LocalTime.of(22, 0), z)))
    }
}
