package com.kamalahmed.prayerapp.widget

import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val settings = AppSettings()
    private val schedule = PrayerSchedule(settings.toCalcParams())
    private val day = LocalDate.of(2026, 11, 2)
    private fun at(h: Int, m: Int) = ZonedDateTime.of(day.atTime(h, m), zone)

    @Test fun styleRoundTrip() {
        val s = WidgetStyle(theme = WidgetTheme.GRADIENT, accent = AccentPalette[3].argb, opacity = 42, corner = 7, padV = 3, padH = 5, align = WidgetAlign.BOTTOM,
            spacing = 9, textScale = 1.25f, iconScale = 0.75f, boxScale = 1.1f, weight = WidgetWeight.BOLD, showHijri = false,
            showCountdown = false, showIcons = false, showAmPm = false, showSunrise = true, showMidnight = true,
            clock24 = Tri.ON, abbreviations = Tri.OFF)
        assertEquals(s, WidgetStyle.decode(s.encode(), WidgetKind.LARGE))
    }

    @Test fun decodeGarbageFallsBackToDefaults() {
        assertEquals(WidgetStyle.defaults(WidgetKind.MINIMAL), WidgetStyle.decode(null, WidgetKind.MINIMAL))
        val d = WidgetStyle.decode("theme=NOPE;opacity=abc;corner=999;junk", WidgetKind.NEXT_SMALL)
        assertEquals(WidgetTheme.GLASS, d.theme)
        assertEquals(80, d.opacity)
        assertEquals(40, d.corner) // clamped
    }

    @Test fun triResolution() {
        val s = WidgetStyle(clock24 = Tri.APP, abbreviations = Tri.OFF)
        assertTrue(s.use24h(true)); assertFalse(s.use24h(false)); assertFalse(s.abbr(true))
    }

    @Test fun paletteOpacityIsRealAlpha() {
        val p = WidgetPalette.of(WidgetStyle(theme = WidgetTheme.SOLID, opacity = 50))
        assertEquals(128, (p.bg ushr 24) and 0xFF)
        assertEquals(0, (WidgetPalette.of(WidgetStyle(opacity = 0)).bg ushr 24) and 0xFF)
    }

    @Test fun nextAndRowsAfternoon() {
        val d = WidgetData.build(schedule, settings, emptyMap(), at(14, 0))
        assertEquals(Prayer.ASR, d.next.prayer)
        assertEquals(Prayer.DHUHR, d.previous.prayer)
        assertEquals(day, d.listDate)
        assertEquals(listOf(Prayer.ASR), d.rows.filter { it.isNext }.map { it.prayer })
        assertEquals(22, d.hijri.day)
        assertEquals(setOf(Prayer.FAJR, Prayer.DHUHR), d.markable)
        assertTrue(d.progress in 0.5f..1f)
        assertEquals(d.next.time, d.nextBoundary)
    }

    @Test fun afterIshaListsTomorrow() {
        val d = WidgetData.build(schedule, settings, emptyMap(), at(20, 0))
        assertEquals(day.plusDays(1), d.listDate)
        assertEquals(Prayer.FAJR, d.next.prayer)
        assertEquals(day.plusDays(1), d.next.time.toLocalDate())
        assertTrue(d.rows.first { it.prayer == Prayer.FAJR }.isNext)
        // Islamic midnight (~23:23) comes before the next Fajr.
        assertTrue(d.nextBoundary.isBefore(d.next.time))
    }

    @Test fun streakAndTodayStatus() {
        val full = Prayer.obligatory.associateWith { PrayerStatus.ON_TIME }
        val data = mapOf(day.minusDays(1) to full, day.minusDays(2) to full, day to mapOf(Prayer.FAJR to PrayerStatus.LATE))
        val d = WidgetData.build(schedule, settings, data, at(14, 0))
        assertEquals(2, d.streakDays)
        assertEquals(1, d.prayedToday)
    }
}
