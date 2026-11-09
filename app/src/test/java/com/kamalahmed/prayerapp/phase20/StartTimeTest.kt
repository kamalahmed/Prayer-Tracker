package com.kamalahmed.prayerapp.phase20

import com.kamalahmed.prayerapp.alarm.AlarmPlanner
import com.kamalahmed.prayerapp.alarm.AlarmType
import com.kamalahmed.prayerapp.alarm.Iqamah
import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.CalcParams
import com.kamalahmed.prayerapp.core.prayer.CalculationMethod
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.core.prayer.PrayerTimesCalculator
import com.kamalahmed.prayerapp.core.prayer.StartTimeLimits
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.ui.settings.StartTimeText
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class StartTimeTest {
    private val dhakaZone = ZoneId.of("Asia/Dhaka")
    private val dhaka = CalcParams(23.8103, 90.4125, dhakaZone, CalculationMethod.KARACHI)
    private val date = LocalDate.of(2026, 11, 9)
    private val bangladesh = AppSettings(calcMethod = "KARACHI", latitude = 23.8103, longitude = 90.4125, cityName = "Dhaka", timeZoneId = "Asia/Dhaka")

    private fun day(p: CalcParams, d: LocalDate = date) = PrayerTimesCalculator.calculate(d, p)

    @Test fun largeOffsetBeyondOldThirtyMinuteCapIsApplied() {
        val raw = day(dhaka)[Prayer.DHUHR]
        val shifted = day(dhaka.copy(adjustments = mapOf(Prayer.DHUHR to 114)))
        assertEquals(raw.plusMinutes(114), shifted[Prayer.DHUHR])
        assertTrue(shifted.limited.isEmpty())
        // Other prayers untouched.
        assertEquals(day(dhaka)[Prayer.ASR], shifted[Prayer.ASR])
    }

    @Test fun fixedClockTimeIsTheSameEveryDayAndOverridesOffset() {
        val p = dhaka.copy(fixedTimes = mapOf(Prayer.DHUHR to 13 * 60 + 30), adjustments = mapOf(Prayer.DHUHR to 5))
        for (d in listOf(date, date.plusDays(40), LocalDate.of(2027, 6, 21))) {
            assertEquals(LocalTime.of(13, 30), day(p, d)[Prayer.DHUHR].toLocalTime())
            assertEquals(d, day(p, d)[Prayer.DHUHR].toLocalDate())
        }
    }

    @Test fun offsetsAreBoundedToSixHours() {
        val raw = day(dhaka)[Prayer.ISHA]
        val p = dhaka.copy(adjustments = mapOf(Prayer.ISHA to 5000))
        val isha = day(p)[Prayer.ISHA]
        assertTrue(isha <= raw.plusMinutes(StartTimeLimits.MAX_OFFSET.toLong()))
        assertTrue(isha < day(dhaka, date.plusDays(1))[Prayer.FAJR])
    }

    @Test fun ishaCanMovePastMidnightButStaysBeforeNextFajr() {
        val fixed = day(dhaka.copy(fixedTimes = mapOf(Prayer.ISHA to 30)))
        assertEquals(date.plusDays(1).atTime(0, 30).atZone(dhakaZone), fixed[Prayer.ISHA])
        val far = day(dhaka.copy(fixedTimes = mapOf(Prayer.ISHA to 11 * 60)))
        val nextFajr = day(dhaka, date.plusDays(1))[Prayer.FAJR]
        assertEquals(nextFajr.minusMinutes(1), far[Prayer.ISHA])
        assertTrue(Prayer.ISHA in far.limited)
    }

    @Test fun fajrCannotGoBeforeMidnightOrPastSunrise() {
        val early = day(dhaka.copy(adjustments = mapOf(Prayer.FAJR to -360)))
        assertEquals(date, early[Prayer.FAJR].toLocalDate())
        val late = day(dhaka.copy(adjustments = mapOf(Prayer.FAJR to 300)))
        assertTrue(late[Prayer.FAJR] < late[Prayer.SUNRISE])
        assertTrue(Prayer.FAJR in late.limited)
    }

    @Test fun changedPrayerIsLimitedNotItsNeighbour() {
        val base = day(dhaka)
        val d = day(dhaka.copy(fixedTimes = mapOf(Prayer.DHUHR to 18 * 60)))
        assertEquals(base[Prayer.ASR], d[Prayer.ASR])
        assertEquals(base[Prayer.ASR].minusMinutes(1), d[Prayer.DHUHR])
        assertEquals(setOf(Prayer.DHUHR), d.limited)
        val order = d.times.values.toList()
        assertEquals(order.sorted(), order)
    }

    @Test fun orderIsStrictForEveryExtremeCombination() {
        for (a in listOf(-360, -120, 0, 120, 360)) for (b in listOf(-360, 0, 360)) {
            val p = dhaka.copy(adjustments = mapOf(Prayer.FAJR to a, Prayer.DHUHR to b, Prayer.ASR to a, Prayer.MAGHRIB to b, Prayer.ISHA to a))
            val t = day(p).times.values.toList()
            for (i in 1 until t.size) assertTrue("$a/$b", t[i] > t[i - 1])
        }
    }

    @Test fun noUserChangeKeepsTheOriginalCalculation() {
        val p = CalcParams(25.3463, 55.4209, ZoneId.of("Asia/Dubai"))
        assertEquals(day(p).times, day(p.copy(adjustments = Prayer.entries.associateWith { 0 })).times)
        assertTrue(day(p).limited.isEmpty())
    }

    @Test fun alarmsAndIqamahFollowTheAdjustedStart() {
        val s = bangladesh.copy(startFixed = mapOf(Prayer.DHUHR to 13 * 60 + 30), iqamaMinutes = bangladesh.iqamaMinutes + (Prayer.DHUHR to 10),
            iqamaNotifications = setOf(Prayer.DHUHR), iqamaBefore = mapOf(Prayer.DHUHR to 5))
        val sch = PrayerSchedule(s.toCalcParams())
        val now = ZonedDateTime.of(date, LocalTime.of(9, 0), dhakaZone)
        val plan = AlarmPlanner.plan(s, sch, now)
        val start = plan.single { it.type == AlarmType.PRAYER && it.prayer == Prayer.DHUHR }
        assertEquals(date.atTime(13, 30).atZone(dhakaZone), start.at)
        val notice = plan.single { it.type == AlarmType.IQAMA_NOTICE }
        assertEquals(date.atTime(13, 35).atZone(dhakaZone), notice.at)
        assertEquals(date.atTime(13, 40).atZone(dhakaZone), Iqamah.time(s, Prayer.DHUHR, start.at))
        assertTrue(start.at < notice.at)
    }

    @Test fun shiftedIshaAfterMidnightIsLoggedOnItsPrayerDay() {
        val sch = PrayerSchedule(dhaka.copy(fixedTimes = mapOf(Prayer.ISHA to 30)))
        val isha = sch.day(date)[Prayer.ISHA]
        assertEquals(date.plusDays(1), isha.toLocalDate())
        assertEquals(date, sch.dateOf(Prayer.ISHA, isha))
        assertEquals(date, sch.dateOf(Prayer.DHUHR, sch.day(date)[Prayer.DHUHR]))
    }

    @Test fun editorHelpersExplainAndComputeOffsets() {
        assertEquals(114, StartTimeText.offsetFor(11 * 60 + 36, 13 * 60 + 30))
        assertEquals(50, StartTimeText.offsetFor(23 * 60 + 40, 30))   // past midnight
        assertEquals(-20, StartTimeText.offsetFor(10, 23 * 60 + 50))
        assertEquals("+114 min (1 h 54 min)", StartTimeText.offset(114))
        assertEquals("−5 min", StartTimeText.offset(-5))
        assertTrue(StartTimeText.summary(0, 810, "11:36 AM", "1:30 PM", false).startsWith("Fixed 1:30 PM"))
        assertTrue(StartTimeText.summary(114, null, "11:36 AM", "1:30 PM", true).endsWith("limited to keep prayer order"))
    }
}
