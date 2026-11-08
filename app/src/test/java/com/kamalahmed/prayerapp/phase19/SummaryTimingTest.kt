package com.kamalahmed.prayerapp.phase19

import com.kamalahmed.prayerapp.summary.*
import com.kamalahmed.prayerapp.data.settings.AppSettings
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class SummaryTimingTest {
    private val sunday=ZonedDateTime.parse("2026-11-08T21:30:00+04:00[Asia/Dubai]")
    @Test fun notificationsAreOffByDefault() { assertTrue(SummaryTiming.due(sunday,AppSettings(),emptyMap()).isEmpty()) }
    @Test fun dailyTimeBoundaryAndSuccessfulDeliveryDeduplication() {
        val s=AppSettings(summaryDaily=true)
        assertTrue(SummaryTiming.due(sunday.minusMinutes(1),s,emptyMap()).isEmpty())
        assertEquals(listOf(SummaryRequest(SummaryKind.DAY,sunday.toLocalDate())),SummaryTiming.due(sunday,s,emptyMap()))
        assertTrue(SummaryTiming.due(sunday,s,mapOf(SummaryKind.DAY to sunday.toLocalDate())).isEmpty())
    }
    @Test fun weeklyIsSundayEveningAndUsesTheMondayPeriodKey() {
        val s=AppSettings(summaryWeekly=true)
        assertEquals(LocalDate.of(2026,11,2),SummaryTiming.due(sunday,s,emptyMap()).single().anchor)
        assertTrue(SummaryTiming.due(sunday.minusDays(1),s,emptyMap()).isEmpty())
        assertTrue(SummaryTiming.due(sunday.withHour(19),s,emptyMap()).isEmpty())
        assertTrue(SummaryTiming.due(sunday,s,mapOf(SummaryKind.WEEK to LocalDate.of(2026,11,2))).isEmpty())
    }
    @Test fun weeklyDeliveryCanCatchUpAfterSundayDozeWithoutReportingANewWeek() {
        val mondayMorning=sunday.plusDays(1).withHour(7)
        val due=SummaryTiming.due(mondayMorning,AppSettings(summaryWeekly=true),emptyMap())
        assertEquals(listOf(SummaryRequest(SummaryKind.WEEK,LocalDate.of(2026,11,2))),due)
        assertTrue(SummaryTiming.due(mondayMorning.plusDays(1),AppSettings(summaryWeekly=true),emptyMap()).isEmpty())
    }
    @Test fun backwardClockDoesNotRedeliverAnOlderDay() {
        assertTrue(SummaryTiming.due(sunday,AppSettings(summaryDaily=true),mapOf(SummaryKind.DAY to sunday.toLocalDate().plusDays(1))).isEmpty())
    }
    @Test fun dstUsesWallClockAndBothReportsCanBeDueIndependently() {
        val now=ZonedDateTime.parse("2026-11-01T21:30:00-05:00[America/New_York]")
        val due=SummaryTiming.due(now,AppSettings(summaryDaily=true,summaryWeekly=true),emptyMap())
        assertEquals(2,due.size);assertEquals(now.toLocalDate(),due.first().anchor)
    }
}
