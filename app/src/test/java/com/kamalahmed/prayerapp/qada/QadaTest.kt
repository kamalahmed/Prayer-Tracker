package com.kamalahmed.prayerapp.qada

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QadaTest {
    private val today = LocalDate.of(2026, 11, 8)
    private fun debt(n: Int, witr: Boolean = false) = QadaCalculator.counts(n, witr).map { (p, c) -> QadaDebt(p.name, c) }
    private fun e(p: QadaPrayer, n: Int, d: LocalDate) = QadaEntry(prayer = p.name, count = n, date = d.toString())

    @Test fun calculatorRanges() {
        assertEquals(366, QadaCalculator.daysBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
        assertEquals(0, QadaCalculator.daysBetween(today, today.minusDays(1)))
        assertEquals(3652, QadaCalculator.daysFor(10, 0))
        assertEquals(365 + 30, QadaCalculator.daysFor(1, 1), 1)
        assertEquals(5, QadaCalculator.counts(10, false).size)
        assertEquals(6, QadaCalculator.counts(10, true).size)
    }

    @Test fun exclusionRemovesDaysPerMonth() {
        val d = QadaCalculator.daysFor(1, 0)
        val net = QadaCalculator.applyExclusion(d, 7)
        assertEquals(d - 84, net, 1)
        assertEquals(0, QadaCalculator.applyExclusion(10, 30))
    }

    private fun assertEquals(a: Int, b: Int, tol: Int) = assertTrue("$a vs $b", kotlin.math.abs(a - b) <= tol)

    @Test fun reportProgressDaysAndPace() {
        val entries = listOf(
            e(QadaPrayer.FAJR, 10, today), e(QadaPrayer.DHUHR, 10, today.minusDays(1)), e(QadaPrayer.ASR, 10, today.minusDays(2)),
            e(QadaPrayer.MAGHRIB, 10, today.minusDays(3)), e(QadaPrayer.ISHA, 8, today.minusDays(5)),
        )
        val r = QadaStats.report(debt(100), entries, QadaConfig(setupDone = true), today)
        assertEquals(500, r.totalOwed)
        assertEquals(48, r.totalDone)
        assertEquals(8, r.daysCovered) // limited by Isha
        assertEquals(92, r.daysRemaining) // Isha has the most left
        assertEquals(10, r.today)
        assertEquals(48, r.last7)
        assertEquals(8.0, r.pacePerDay, 1e-9) // 48 over 6 days since the first entry
        assertEquals(today.plusDays(57), r.finishDate) // ceil(452 / 8)
        assertEquals(4, r.streakDays)
        assertEquals(4, r.bestStreakDays)
        assertEquals(48, r.weekly.sumOf { it.second })
        assertEquals(12, r.monthly.size)
        assertEquals(48 / 500f, r.progress, 1e-6f)
    }

    @Test fun extraBeyondDebtDoesNotInflateProgress() {
        val r = QadaStats.report(debt(1), QadaPrayer.fard.map { e(it, 5, today) }, QadaConfig(setupDone = true), today)
        assertEquals(1f, r.progress, 0f)
        assertEquals(0, r.remaining)
        assertNull(r.finishDate)
    }

    @Test fun witrShownOnlyWhenEnabledOrUsed() {
        assertEquals(5, QadaStats.report(debt(3), emptyList(), QadaConfig(), today).lines.size)
        assertEquals(6, QadaStats.report(debt(3, true), emptyList(), QadaConfig(witr = true), today).lines.size)
    }

    @Test fun badRowsAreIgnored() {
        val r = QadaStats.report(listOf(QadaDebt("NOPE", 4), QadaDebt("FAJR", -3)),
            listOf(QadaEntry(prayer = "FAJR", count = 2, date = "garbage"), e(QadaPrayer.FAJR, -5, today)), QadaConfig(), today)
        assertEquals(0, r.totalOwed); assertEquals(0, r.totalDone)
    }

    @Test fun emptyHasNoPaceOrFinish() {
        val r = QadaStats.report(emptyList(), emptyList(), QadaConfig(), today)
        assertEquals(0.0, r.pacePerDay, 0.0); assertNull(r.finishDate); assertEquals(0, r.streakDays)
    }

    @Test fun spanFormatting() {
        assertEquals("10 years", QadaStats.span(3652).toString())
        assertEquals("0 days", QadaStats.span(0).toString())
        val s = QadaStats.span(365 + 61 + 3)
        assertEquals(1, s.years); assertEquals(2, s.months)
    }

    @Test fun reminderDelay() {
        val now = java.time.LocalDateTime.of(2026, 11, 8, 21, 0)
        assertEquals(23 * 60L, QadaReminderWorker.initialDelay(now, 20 * 60).toMinutes())
        assertEquals(60L, QadaReminderWorker.initialDelay(now, 22 * 60).toMinutes())
    }
}
