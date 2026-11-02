package com.kamalahmed.prayerapp.core

import com.kamalahmed.prayerapp.core.hijri.HijriCalendar
import com.kamalahmed.prayerapp.core.hijri.IslamicHolidays
import com.kamalahmed.prayerapp.core.location.GeoFence
import com.kamalahmed.prayerapp.core.model.AsrMethod
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.CalcParams
import com.kamalahmed.prayerapp.core.prayer.CalculationMethod
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.core.prayer.PrayerTimesCalculator
import com.kamalahmed.prayerapp.core.prayer.Qibla
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class PrayerTimesTest {
    private val dubai = ZoneId.of("Asia/Dubai")
    private val sharjah = CalcParams(25.3463, 55.4209, dubai, CalculationMethod.DUBAI)
    private val date = LocalDate.of(2026, 11, 2)

    private fun assertNear(expected: String, actual: ZonedDateTime, tolMin: Long = 2, label: String) {
        val exp = LocalTime.parse(expected)
        val diff = abs(Duration.between(exp, actual.toLocalTime()).toMinutes())
        assertTrue("$label expected ~$expected but was ${actual.toLocalTime()}", diff <= tolMin)
    }

    @Test fun sharjahDubaiMethod_matchesPublishedTimes() {
        val d = PrayerTimesCalculator.calculate(date, sharjah)
        println("Sharjah 2026-11-02: " + d.times.map { "${it.key}=${it.value.toLocalTime()}" } + " midnight=${d.midnight.toLocalTime()} lastThird=${d.lastThird.toLocalTime()}")
        assertNear("05:08", d[Prayer.FAJR], label = "Fajr")
        assertNear("06:27", d[Prayer.SUNRISE], label = "Sunrise")
        assertNear("12:02", d[Prayer.DHUHR], label = "Dhuhr")
        assertNear("15:13", d[Prayer.ASR], label = "Asr")
        assertNear("17:37", d[Prayer.MAGHRIB], label = "Maghrib")
        assertNear("18:56", d[Prayer.ISHA], label = "Isha")
    }

    @Test fun hanafiAsr_isLater() {
        val std = PrayerTimesCalculator.calculate(date, sharjah)
        val han = PrayerTimesCalculator.calculate(date, sharjah.copy(asr = AsrMethod.HANAFI))
        assertTrue(han[Prayer.ASR].isAfter(std[Prayer.ASR].plusMinutes(30)))
    }

    @Test fun userAdjustments_shiftByMinutes() {
        val base = PrayerTimesCalculator.calculate(date, sharjah)
        val adj = PrayerTimesCalculator.calculate(date, sharjah.copy(adjustments = mapOf(Prayer.FAJR to 4, Prayer.ISHA to -2)))
        assertEquals(base[Prayer.FAJR].plusMinutes(4), adj[Prayer.FAJR])
        assertEquals(base[Prayer.ISHA].minusMinutes(2), adj[Prayer.ISHA])
    }

    @Test fun ummAlQura_ishaIs90MinAfterMaghrib() {
        val d = PrayerTimesCalculator.calculate(date, sharjah.copy(method = CalculationMethod.UMM_AL_QURA))
        assertEquals(90, Duration.between(d[Prayer.MAGHRIB], d[Prayer.ISHA]).toMinutes())
    }

    @Test fun allMethods_produceOrderedTimes() {
        CalculationMethod.entries.forEach { m ->
            val d = PrayerTimesCalculator.calculate(date, sharjah.copy(method = m))
            val list = Prayer.entries.map { d[it] }
            assertEquals("$m ordered", list.sorted(), list)
            assertTrue(d.midnight.isAfter(d[Prayer.MAGHRIB]))
            assertTrue(d.lastThird.isAfter(d.midnight))
        }
    }

    @Test fun schedule_nextPrayerAcrossMidnight() {
        val s = PrayerSchedule(sharjah)
        val lateNight = ZonedDateTime.of(date, LocalTime.of(23, 30), dubai)
        val next = s.next(lateNight)
        assertEquals(Prayer.FAJR, next.prayer)
        assertEquals(date.plusDays(1), next.time.toLocalDate())
        assertEquals(Prayer.ISHA, s.current(lateNight).prayer)
    }

    @Test fun qibla_fromSharjah() {
        val b = Qibla.bearing(25.3463, 55.4209)
        assertTrue("bearing $b", b in 254.0..260.0)
    }

    @Test fun hijri_conversionAndAdjust() {
        val h = HijriCalendar.of(date)
        println("Hijri for $date = ${h.format()}")
        assertEquals(1448, h.year)
        val plus = HijriCalendar.of(date, 1)
        assertEquals(HijriCalendar.of(date.plusDays(1)), plus)
        val back = HijriCalendar.toGregorian(h.year, h.month, h.day)
        assertEquals(date, back)
    }

    @Test fun hijri_autoIncrementAfterMaghrib() {
        val d = PrayerTimesCalculator.calculate(date, sharjah)
        val after = d[Prayer.MAGHRIB].plusMinutes(1)
        assertEquals(HijriCalendar.of(date, 1), HijriCalendar.at(after, d[Prayer.MAGHRIB], 0, true))
        assertEquals(HijriCalendar.of(date), HijriCalendar.at(after, d[Prayer.MAGHRIB], 0, false))
    }

    @Test fun holidays_upcomingSorted() {
        val list = IslamicHolidays.upcoming(date)
        assertTrue(list.isNotEmpty())
        assertEquals(list.sortedBy { it.date }, list)
        assertTrue(list.all { !it.date.isBefore(date) })
    }

    @Test fun geofence() {
        assertFalse(GeoFence.shouldUpdate(25.3463, 55.4209, 25.2048, 55.2708, 25)) // Sharjah->Dubai ~22km
        assertTrue(GeoFence.shouldUpdate(25.3463, 55.4209, 24.4539, 54.3773, 25))
    }
}
