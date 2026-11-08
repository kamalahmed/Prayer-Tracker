package com.kamalahmed.prayerapp.nafl

import com.kamalahmed.prayerapp.tracker.StatsPeriod
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NaflTest {
    private val today = LocalDate.of(2026, 11, 8)
    @Test fun defaultsHaveTwelveBaselineRakaatAndOptionalSlotsOff() {
        assertEquals(12, NaflDefaults.items.filter { it.baseline }.sumOf { it.rakaat })
        assertEquals(5, NaflDefaults.items.count { it.baseline && it.enabled })
        assertFalse(NaflDefaults.items.first { it.id == "asr_before" }.enabled)
        assertFalse(NaflDefaults.items.first { it.id == "witr" }.enabled)
        assertEquals(1, NaflDefaults.items.count { it.id == "quran" })
    }
    @Test fun optionalSlotsDoNotInflateTwelveRakaat() {
        val logs = NaflDefaults.items.filter { it.rakaat > 0 }.map { NaflLog(today.toString(), it.id, 1) }
        assertEquals(12, NaflStats.rakaat(logs))
    }
    @Test fun periodCountsAndQuranDaysUseFlagsNotPages() {
        val logs = listOf(NaflLog(today.toString(), "quran", 1, 5, 10),
            NaflLog(today.minusDays(1).toString(), "quran", 0, 9, 20),
            NaflLog(today.minusMonths(1).toString(), "quran", 1),
            NaflLog(today.toString(), "dhikr", 33))
        val r = NaflStats.report(NaflDefaults.items, logs, StatsPeriod.MONTH, today, today)
        assertEquals(1, r.quranDays)
        assertEquals(33, r.items.first { it.item.id == "dhikr" }.count)
        assertEquals(1, r.items.first { it.item.id == "quran" }.days)
    }
    @Test fun streakAllowsYesterdayAndBreaksAtGap() {
        val logs = listOf(1L, 2L, 4L).map { NaflLog(today.minusDays(it).toString(), "quran", 1) }
        assertEquals(2, NaflStats.streak(logs, "quran", today))
        assertEquals(0, NaflStats.streak(logs, "duha", today))
    }
    @Test fun futureAndMalformedRowsDoNotAffectStats() {
        val logs = listOf(NaflLog("bad", "quran", 99), NaflLog(today.plusDays(1).toString(), "quran", 1))
        val r = NaflStats.report(NaflDefaults.items, logs, StatsPeriod.YEAR, today, today)
        assertEquals(0, r.quranDays)
        assertEquals(0, r.items.sumOf { it.count })
    }
    @Test fun emptyPeriodHasNoNaNAndCalendarIncludesLeapDay() {
        val d = LocalDate.of(2024, 2, 29)
        val r = NaflStats.report(NaflDefaults.items, emptyList(), StatsPeriod.MONTH, d, d)
        assertEquals(29, r.daily.size)
        assertTrue(r.items.all { it.rate == 0 })
        assertTrue(r.daily.all { it.completed == null })
    }
    @Test fun nextReminderIsTomorrowWhenTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 11, 8, 16, 0, 0, 0, ZoneId.of("Asia/Dubai"))
        assertEquals(now.plusDays(1).withHour(9), QuranReminderTime.next(9 * 60, now))
        assertEquals(now.withHour(17), QuranReminderTime.next(17 * 60, now))
    }
    @Test fun monthRatesExcludeFutureAndPreTrackingDays() {
        val logs = listOf(NaflLog(today.minusDays(1).toString(), "fajr_before", 1), NaflLog(today.toString(), "fajr_before", 1))
        val r = NaflStats.report(NaflDefaults.items, logs, StatsPeriod.MONTH, today, today)
        assertEquals(100, r.items.first { it.item.id == "fajr_before" }.rate)
        assertEquals(2, r.daily.first { it.date == today }.rakaat)
        assertNull(r.daily.first { it.date == today.plusDays(1) }.completed)
    }
    @Test fun invalidReminderTimeRejected() {
        assertThrows(IllegalArgumentException::class.java) { QuranReminderTime.next(1440, ZonedDateTime.now()) }
    }
}
