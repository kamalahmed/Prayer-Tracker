package com.kamalahmed.prayerapp.phase19

import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.summary.*
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.tracker.*
import com.kamalahmed.prayerapp.qada.QadaEntry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ModelsTest {
    private val day=LocalDate.of(2026,11,8)
    @Test fun ringRetainsFullLapAtExactTargetsAndContinuesAfterwards() {
        assertEquals(0f,CounterProgress.of(0,100).fraction,0.001f)
        assertEquals(1f,CounterProgress.of(100,100).fraction,0.001f)
        assertEquals(1,CounterProgress.of(101,100).laps)
        assertEquals(0.01f,CounterProgress.of(101,100).fraction,0.001f)
        assertEquals(3,CounterProgress.of(300,100).laps)
        assertEquals(1f,CounterProgress.of(300,100).fraction,0.001f)
        assertEquals(3,CounterProgress.of(300,100).activeLap)
    }
    @Test fun freeCountingDoesNotInventTargetProgressAndInputsAreBounded() {
        assertFalse(CounterProgress.of(55,0).hasTarget)
        assertEquals(0f,CounterProgress.of(-1,33).fraction,0f)
        assertEquals(1_000_000,CounterProgress.of(Int.MAX_VALUE,1).laps)
    }
    @Test fun summaryUsesDifferentUnitsAndKeepsHiddenCounterHistory() {
        val items=NaflDefaults.items.map { if(it.id=="istighfar") it.copy(enabled=false) else it }
        val logs=listOf(NaflLog(day.toString(),"fajr_before",1),NaflLog(day.toString(),"istighfar",1000),NaflLog(day.toString(),"quran",1,8,22),NaflLog(day.toString(),"duha",1))
        val data=mapOf(day to Prayer.obligatory.associateWith { PrayerStatus.ON_TIME })
        val report=WorshipSummary.build(data,items,logs,listOf(QadaEntry(prayer="FAJR",count=3,date=day.toString())),day,day,day,Prayer.obligatory)
        assertEquals(5,report.fard.prayed); assertEquals(2,report.sunnah);assertEquals(1000L,report.dhikr)
        assertEquals(1L,report.otherPrayers);assertEquals(8L,report.quranPages);assertEquals(1,report.quranDays);assertEquals(3L,report.qada)
    }
    @Test fun weeklySummaryExcludesFutureAndMalformedHistoryAndUsesLongTotals() {
        val monday=day.minusDays(6)
        val logs=(0L..6L).map { NaflLog(monday.plusDays(it).toString(),"istighfar",1_000_000) }+listOf(NaflLog("broken","quran",1),NaflLog(day.plusDays(1).toString(),"quran",1))
        val r=WorshipSummary.build(emptyMap(),NaflDefaults.items,logs,emptyList(),monday,day.plusDays(2),day,emptyList())
        assertEquals(7_000_000L,r.dhikr);assertEquals(0,r.quranDays);assertEquals(7,r.days.size);assertEquals(0,r.fard.prayed)
    }
    @Test fun detailSeriesMustNotAggregateFutureLogsIntoACurrentMonthlyBar() {
        val logs=listOf(NaflLog(day.toString(),"istighfar",1),NaflLog(day.plusDays(1).toString(),"istighfar",999999))
        val ui=com.kamalahmed.prayerapp.ui.nafl.NaflUi(NaflDefaults.items,logs,emptyList(),StatsPeriod.YEAR,day,day,day,NaflStats.report(NaflDefaults.items,logs,StatsPeriod.YEAR,day,day))
        assertEquals(1,com.kamalahmed.prayerapp.ui.nafl.itemDays(ui,NaflDefaults.items.first { it.id=="istighfar" }).sumOf { it.second })
    }
    @Test fun bestDayIsBasedOnlyOnRecordedFardNotAMixedWorshipScore() {
        val data=mapOf(day.minusDays(1) to Prayer.obligatory.associateWith { PrayerStatus.ON_TIME },day to mapOf(Prayer.FAJR to PrayerStatus.ON_TIME))
        val r=WorshipSummary.build(data,NaflDefaults.items,listOf(NaflLog(day.toString(),"istighfar",999999)),emptyList(),day.minusDays(1),day,day,Prayer.obligatory)
        assertEquals(day.minusDays(1),r.bestDay?.date)
    }
}
