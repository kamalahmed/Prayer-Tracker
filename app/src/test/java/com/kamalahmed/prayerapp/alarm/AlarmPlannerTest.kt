package com.kamalahmed.prayerapp.alarm

import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmPlannerTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val base = AppSettings()
    private val sched = PrayerSchedule(base.toCalcParams())
    private val now = ZonedDateTime.of(LocalDate.of(2026, 11, 2), LocalTime.of(13, 0), zone)

    @Test fun defaultSchedulesFivePrayersNotSunrise() {
        val plan = AlarmPlanner.plan(base, sched, now)
        val prayers = plan.filter { it.type == AlarmType.PRAYER }.map { it.prayer }.toSet()
        assertEquals(Prayer.obligatory.toSet(), prayers)
        assertTrue(plan.all { it.at.isAfter(now) })
        // Fajr already passed today -> tomorrow
        assertEquals(LocalDate.of(2026, 11, 3), plan.first { it.prayer == Prayer.FAJR }.at.toLocalDate())
        // Asr later today
        assertEquals(LocalDate.of(2026, 11, 2), plan.first { it.prayer == Prayer.ASR }.at.toLocalDate())
    }

    @Test fun offModeSkipped() {
        val s = base.copy(notifyModes = base.notifyModes + (Prayer.ASR to NotifyMode.OFF))
        assertTrue(AlarmPlanner.plan(s, sched, now).none { it.prayer == Prayer.ASR })
    }

    @Test fun remindersAndIqama() {
        val s = base.copy(preReminderMinutes = 10, iqamaEnabled = true)
        val plan = AlarmPlanner.plan(s, sched, now)
        val asr = plan.first { it.prayer == Prayer.ASR && it.type == AlarmType.PRAYER }
        val rem = plan.first { it.prayer == Prayer.ASR && it.type == AlarmType.REMINDER }
        val iq = plan.first { it.prayer == Prayer.ASR && it.type == AlarmType.IQAMA }
        assertEquals(asr.at.minusMinutes(10), rem.at)
        assertEquals(asr.at.plusMinutes(20), iq.at)
        // Dhuhr was at 12:02, iqama 20 min -> 12:22 passed -> tomorrow
        assertEquals(LocalDate.of(2026, 11, 3), plan.first { it.prayer == Prayer.DHUHR && it.type == AlarmType.IQAMA }.at.toLocalDate())
    }

    @Test fun uniqueRequestCodes() {
        val plan = AlarmPlanner.plan(base.copy(preReminderMinutes = 5, iqamaEnabled = true), sched, now)
        assertEquals(plan.size, plan.map { it.requestCode }.toSet().size)
    }
}
