package com.kamalahmed.prayerapp.phase21

import com.kamalahmed.prayerapp.qada.QadaConfig
import com.kamalahmed.prayerapp.qada.QadaDebt
import com.kamalahmed.prayerapp.qada.QadaEntry
import com.kamalahmed.prayerapp.qada.QadaHistory
import com.kamalahmed.prayerapp.qada.QadaHistoryRow.Kind
import com.kamalahmed.prayerapp.qada.QadaPrayer
import com.kamalahmed.prayerapp.qada.QadaStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** Grouped history and the aggregate report: pure, no database. */
class QadaHistoryTest {
    private val today = LocalDate.of(2026, 11, 9)

    @Test fun recentDaysThenMonthsWithWeeksThenYears() {
        val perDay = mapOf(
            today to 3, today.minusDays(1) to 6, today.minusDays(13) to 2, // inside the 14-day window
            today.minusDays(14) to 4, LocalDate.of(2026, 10, 5) to 5, LocalDate.of(2026, 10, 6) to 1, // older: October 2026
            LocalDate.of(2025, 12, 31) to 7, // within 12 months -> month row
            LocalDate.of(2025, 3, 2) to 9, LocalDate.of(2024, 6, 1) to 11, // older than 12 months -> year rows
            today.plusDays(1) to 99, today.minusDays(3) to 0, // future and empty days are ignored
        )
        val rows = QadaHistory.build(perDay, mapOf(today to mapOf(QadaPrayer.FAJR to 2, QadaPrayer.DHUHR to 1)), today)
        assertEquals(listOf(Kind.DAY, Kind.DAY, Kind.DAY, Kind.MONTH, Kind.MONTH, Kind.YEAR, Kind.YEAR), rows.map { it.kind })
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(13)), rows.take(3).map { it.start })
        assertEquals(mapOf(QadaPrayer.FAJR to 2, QadaPrayer.DHUHR to 1), rows[0].perPrayer)
        val oct = rows[3]
        assertEquals(LocalDate.of(2026, 10, 1), oct.start)
        assertEquals(4 + 5 + 1, oct.total); assertEquals(3, oct.activeDays)
        assertEquals(oct.total, oct.children.sumOf { it.total })
        assertTrue(oct.children.all { it.kind == Kind.WEEK && !it.start.isBefore(oct.start) && !it.end.isAfter(oct.end) })
        assertEquals(2025, rows[5].start.year); assertEquals(9, rows[5].total)
        assertEquals(2024, rows[6].start.year)
        // Totals are conserved: nothing lost, nothing double-counted.
        assertEquals(perDay.filter { it.value > 0 && !it.key.isAfter(today) }.values.sum(), rows.sumOf { it.total })
    }

    @Test fun eighteenThousandPrayersStayAShortList() {
        // ~10 years of logging, every day: 18,250 prayers across 3,650 days.
        val perDay = (0 until 3650).associate { today.minusDays(it.toLong()) to 5 }
        val rows = QadaHistory.build(perDay, emptyMap(), today)
        assertTrue("rows=${rows.size}", rows.size <= 14 + 12 + 11)
        assertEquals(18_250, rows.sumOf { it.total })
    }

    @Test fun aggregateReportMatchesEntryReport() {
        val rnd = Random(7)
        val entries = (0 until 2000).map {
            QadaEntry(prayer = QadaPrayer.fard.random(rnd).name, count = rnd.nextInt(1, 6), date = today.minusDays(rnd.nextLong(0, 500)).toString())
        }
        val debt = QadaPrayer.fard.map { QadaDebt(it.name, 3650) }
        val config = QadaConfig(dailyGoal = 5, setupDone = true)
        val a = QadaStats.report(debt, entries, config, today)
        val totals = entries.groupBy { QadaPrayer.of(it.prayer)!! }.mapValues { (_, l) -> l.sumOf { it.count } }
        val days = entries.groupBy { LocalDate.parse(it.date) }.mapValues { (_, l) -> l.sumOf { it.count } }
        assertEquals(a, QadaStats.report(debt, totals, days, config, today))
    }

    @Test fun snackbarTitles() {
        val fard = QadaPrayer.fard
        assertEquals("Fajr +1 · ×3 today", com.kamalahmed.prayerapp.ui.qada.snackTitle(listOf(QadaPrayer.FAJR), 1, false, mapOf(QadaPrayer.FAJR to 2)))
        assertEquals("Asr +10 · ×10 today", com.kamalahmed.prayerapp.ui.qada.snackTitle(listOf(QadaPrayer.ASR), 10, false, emptyMap()))
        assertEquals("Full day logged · 5 prayers", com.kamalahmed.prayerapp.ui.qada.snackTitle(fard, 1, false, emptyMap()))
        assertEquals("Daily goal reached · MashaAllah", com.kamalahmed.prayerapp.ui.qada.snackTitle(fard, 1, true, emptyMap()))
    }
}
