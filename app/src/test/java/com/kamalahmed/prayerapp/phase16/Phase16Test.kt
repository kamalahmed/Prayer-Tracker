package com.kamalahmed.prayerapp.phase16

import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.alarm.*
import com.kamalahmed.prayerapp.core.model.*
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.widget.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlinx.coroutines.flow.first

class Phase16Test {
    @Test fun clockTwelveHandlesMidnightNoonAndEvening() {
        assertEquals(0, Clock12.minute(12, 0, false))
        assertEquals(720, Clock12.minute(12, 0, true))
        assertEquals(1205, Clock12.minute(8, 5, true))
        assertEquals("12:00 AM", Clock12.label(0))
        assertEquals("12:00 PM", Clock12.label(720))
    }
    @Test fun presetsAreCountersWithOptionalTargets() {
        assertTrue(NaflDefaults.items.first { it.id == "istighfar" }.isCounter)
        assertEquals(100, NaflDefaults.items.first { it.id == "istighfar" }.dailyTarget)
        assertFalse(NaflDefaults.items.first { it.id == "quran" }.isCounter)
    }
    @Test fun iqamahNoticesDefaultOffAndIndependentOfAdhanMode() {
        val base = AppSettings()
        val sch = PrayerSchedule(base.toCalcParams())
        val now = ZonedDateTime.of(2026,11,2,10,0,0,0,sch.zone)
        assertTrue(AlarmPlanner.plan(base,sch,now).none { it.type == AlarmType.IQAMA_NOTICE })
        val s = base.copy(notifyModes = Prayer.entries.associateWith { NotifyMode.OFF }, iqamaNotifications = setOf(Prayer.ASR), iqamaBefore = mapOf(Prayer.ASR to 5))
        val alarm = AlarmPlanner.plan(s,sch,now).single { it.type == AlarmType.IQAMA_NOTICE }
        assertEquals(sch.day(now.toLocalDate())[Prayer.ASR].plusMinutes(15), alarm.at)
    }
    @Test fun fixedTimeAndFridayOverride() {
        val date = LocalDate.of(2026,11,6)
        val adhan = date.atTime(12,0).atZone(ZoneId.of("Asia/Dubai"))
        val s = AppSettings(iqamaFixed = mapOf(Prayer.DHUHR to 780), jumuahEnabled = true, jumuahMinute = 810)
        assertEquals(13, Iqamah.time(s, Prayer.DHUHR, adhan)!!.hour)
        assertEquals(30, Iqamah.time(s, Prayer.DHUHR, adhan)!!.minute)
        assertEquals(0, Iqamah.time(s, Prayer.DHUHR, adhan.plusDays(1))!!.minute)
    }
    @Test fun ishaCanCrossMidnightButOtherFixedTimesCannotPrecedeAdhan() {
        val at = ZonedDateTime.of(2026,11,8,19,0,0,0,ZoneId.of("Asia/Dubai"))
        val s = AppSettings(iqamaFixed = mapOf(Prayer.ISHA to 10, Prayer.ASR to 10))
        assertEquals(at.toLocalDate().plusDays(1), Iqamah.time(s,Prayer.ISHA,at)!!.toLocalDate())
        assertNull(Iqamah.time(s,Prayer.ASR,at))
    }
    @Test fun newWidgetStylesRoundTripAndRemainInstanceSpecific() {
        val s = WidgetStyle(timeMode = WidgetTimeMode.BOTH, counterId = "salawat")
        assertEquals(s, WidgetStyle.decode(s.encode(), WidgetKind.TASBIH))
        assertEquals(WidgetTimeMode.START, WidgetStyle.decode(null, WidgetKind.ALL_WIDE).timeMode)
        assertEquals(WidgetTimeMode.BOTH, WidgetStyle.defaults(WidgetKind.IQAMAH_TABLE).timeMode)
    }
    @Test fun sessionResetDoesNotOwnEarlierWidgetTapsOrOtherDates() {
        val ledger = CounterSession()
        ledger.record("2026-11-08","a",5); ledger.record("2026-11-08","a",-1)
        ledger.record("2026-11-08","b",7); ledger.record("2026-11-07","a",9)
        assertEquals(4,ledger.count("2026-11-08","a"))
        ledger.clear("2026-11-08","a")
        assertEquals(0,ledger.count("2026-11-08","a")); assertEquals(7,ledger.count("2026-11-08","b"))
        assertEquals(9,ledger.count("2026-11-07","a")); assertEquals(0,ledger.count("2026-11-09","a"))
    }
    @Test fun iqamahFridayOnlyFindsNextWeekEvenAfterTodaysNotice() {
        val s = AppSettings(jumuahEnabled = true,jumuahNotify = true,jumuahMinute = 810,jumuahBefore = 5)
        val schedule = PrayerSchedule(s.toCalcParams())
        val now = ZonedDateTime.of(2026,11,6,14,0,0,0,schedule.zone)
        val next = AlarmPlanner.plan(s,schedule,now).single { it.type == AlarmType.IQAMA_NOTICE }
        assertEquals(LocalDate.of(2026,11,13),next.at.toLocalDate())
        assertEquals(LocalTime.of(13,25),next.at.toLocalTime())
    }
    @Test fun counterStatsAreNotTruncatedAtOldTenThousandLimit() {
        val day = LocalDate.of(2026,11,8)
        val item = NaflDefaults.items.first { it.id == "salawat" }
        val logs = listOf(NaflLog(day.toString(),item.id,25000),NaflLog(day.minusDays(1).toString(),item.id,1000000))
        for (period in com.kamalahmed.prayerapp.tracker.StatsPeriod.entries) {
            assertEquals(1025000,NaflStats.report(listOf(item),logs,period,day,day).items.single().count)
        }
    }
    @Test fun widgetIqamahCountdownUsesIqamahNotNextAdhan() {
        val s = AppSettings()
        val schedule = PrayerSchedule(s.toCalcParams())
        val day = LocalDate.of(2026,11,8)
        val adhan = schedule.day(day)[Prayer.DHUHR]
        val data = WidgetData.build(schedule,s,emptyMap(),adhan.plusMinutes(2))
        val ui = WidgetUiState(WidgetStyle(timeMode = WidgetTimeMode.IQAMAH),data,false)
        assertEquals(Prayer.DHUHR,ui.next.prayer)
        assertEquals(adhan.plusMinutes(s.iqama(Prayer.DHUHR).toLong()),ui.next.time)
        val row = data.rows.first { it.prayer == Prayer.DHUHR }
        assertEquals(ui.time(ui.next.time),ui.rowTime(row)); assertTrue(ui.isNext(row)); assertFalse(ui.passed(row)); assertTrue(ui.progress in 0f..1f)
        val both = WidgetUiState(WidgetStyle(timeMode = WidgetTimeMode.BOTH),data,false)
        assertTrue(both.rowTime(row).startsWith("A ")); assertTrue(both.rowTime(row).contains("\nI "))
    }
    @Test fun fridayOverrideSupportsOffsetModeToo() {
        val adhan = ZonedDateTime.of(2026,11,6,12,10,0,0,ZoneId.of("Asia/Dubai"))
        val s = AppSettings(jumuahEnabled = true,jumuahFixed = false,jumuahOffset = 35)
        assertEquals(adhan.plusMinutes(35),Iqamah.time(s,Prayer.DHUHR,adhan))
        assertFalse(Iqamah.enabled(s,Prayer.DHUHR,adhan))
    }
    @Test fun liveWidgetInitialReloadClosesSubscriptionRace() = kotlinx.coroutines.runBlocking {
        val rows = kotlinx.coroutines.flow.MutableStateFlow<Any?>(42)
        val settings = kotlinx.coroutines.flow.MutableStateFlow<Any?>("settings")
        val ticks = kotlinx.coroutines.flow.MutableStateFlow(0L)
        val result = WidgetLive.changes(rows,settings,ticks,skipInitial = false) { rows.value }.first()
        assertEquals(42,result)
    }
}
