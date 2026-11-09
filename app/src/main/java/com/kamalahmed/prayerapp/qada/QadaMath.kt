package com.kamalahmed.prayerapp.qada

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Debt calculator: turns a span of missed time into per-prayer counts (user can edit them afterwards). */
object QadaCalculator {
    const val DAYS_PER_YEAR = 365.2425
    const val DAYS_PER_MONTH = 30.436875

    /** Days in [from]..[to] inclusive; 0 if reversed. */
    fun daysBetween(from: LocalDate, to: LocalDate): Int =
        if (to.isBefore(from)) 0 else (ChronoUnit.DAYS.between(from, to) + 1).toInt()

    fun daysFor(years: Int, months: Int, days: Int = 0): Int =
        (max(0, years) * DAYS_PER_YEAR + max(0, months) * DAYS_PER_MONTH + max(0, days)).roundToInt()

    /** Removes [excludePerMonth] days (e.g. menstruation) for every month in the span. */
    fun applyExclusion(days: Int, excludePerMonth: Int): Int {
        if (excludePerMonth <= 0 || days <= 0) return max(0, days)
        val months = days / DAYS_PER_MONTH
        return max(0, (days - months * excludePerMonth.coerceAtMost(30)).roundToInt())
    }

    fun counts(days: Int, witr: Boolean): Map<QadaPrayer, Int> =
        QadaPrayer.entries.filter { it != QadaPrayer.WITR || witr }.associateWith { max(0, days) }
}

data class QadaLine(val prayer: QadaPrayer, val owed: Int, val done: Int) {
    val remaining get() = max(0, owed - done)
    val pct: Float get() = if (owed <= 0) 0f else (done.toFloat() / owed).coerceIn(0f, 1f)
}

data class Span(val years: Int, val months: Int, val days: Int) {
    override fun toString(): String = listOfNotNull(
        years.takeIf { it > 0 }?.let { "$it ${if (it == 1) "year" else "years"}" },
        months.takeIf { it > 0 }?.let { "$it ${if (it == 1) "month" else "months"}" },
        days.takeIf { it > 0 || (years == 0 && months == 0) }?.let { "$it ${if (it == 1) "day" else "days"}" },
    ).joinToString(" ")
}

data class QadaReport(
    val lines: List<QadaLine>,
    val totalOwed: Int,
    val totalDone: Int,
    /** Full days covered: a day = the 5 fard prayers. Uses the per-prayer minimum (a day is only complete when all 5 are). */
    val daysCovered: Int,
    /** Days-equivalent of all fard prayers made up (total / 5), shown alongside. */
    val daysEquivalent: Double,
    val daysRemaining: Int,
    val today: Int,
    val last7: Int,
    val last30: Int,
    /** Average prayers per day over the last 30 days (or since first entry if newer). */
    val pacePerDay: Double,
    val finishDate: LocalDate?,
    val streakDays: Int,
    val bestStreakDays: Int,
    /** Oldest -> newest: (week start, count), last 12 weeks. */
    val weekly: List<Pair<LocalDate, Int>>,
    /** Oldest -> newest: (month start, count), last 12 months. */
    val monthly: List<Pair<LocalDate, Int>>,
    val goal: Int,
) {
    val remaining get() = max(0, totalOwed - totalDone)
    val progress: Float get() = if (totalOwed <= 0) 0f else (totalDone.toFloat() / totalOwed).coerceIn(0f, 1f)
    val hasDebt get() = totalOwed > 0
}

object QadaStats {
    fun span(days: Int): Span {
        val d = max(0, days)
        var y = (d / QadaCalculator.DAYS_PER_YEAR).toInt()
        val restAfterYears = d - y * QadaCalculator.DAYS_PER_YEAR
        var m = (restAfterYears / QadaCalculator.DAYS_PER_MONTH).toInt()
        var rest = (restAfterYears - m * QadaCalculator.DAYS_PER_MONTH).roundToInt().coerceAtLeast(0)
        // Fractional year/month lengths can leave "30 days"; carry so 3652 days reads "10 years", not "9 years 11 months 30 days".
        if (rest >= 30) { m++; rest = 0 }
        if (m >= 12) { y++; m = 0 }
        return Span(y, m, rest)
    }

    fun report(debt: List<QadaDebt>, entries: List<QadaEntry>, config: QadaConfig, today: LocalDate): QadaReport {
        val parsed = entries.mapNotNull { e ->
            val p = QadaPrayer.of(e.prayer) ?: return@mapNotNull null
            val d = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@mapNotNull null
            Triple(p, d, max(0, e.count))
        }
        return report(debt, parsed.groupBy { it.first }.mapValues { (_, l) -> l.sumOf { it.third } },
            parsed.groupBy { it.second }.mapValues { (_, l) -> l.sumOf { it.third } }, config, today)
    }

    /** Same report from aggregate rows (per-prayer totals + per-day totals): what the app uses, O(days) not O(entries). */
    fun report(debt: List<QadaDebt>, done: Map<QadaPrayer, Int>, perDay: Map<LocalDate, Int>, config: QadaConfig, today: LocalDate): QadaReport {
        val owed = debt.mapNotNull { d -> QadaPrayer.of(d.prayer)?.let { it to max(0, d.owed) } }.toMap()
        val shown = QadaPrayer.entries.filter { it != QadaPrayer.WITR || config.witr || (owed[it] ?: 0) > 0 || (done[it] ?: 0) > 0 }
        val lines = shown.map { QadaLine(it, owed[it] ?: 0, done[it] ?: 0) }
        val fard = lines.filter { it.prayer in QadaPrayer.fard }
        // Count progress against the debt only (extra prayers beyond the debt don't inflate the percentage).
        val totalOwed = lines.sumOf { it.owed }
        val totalDone = lines.sumOf { if (it.owed > 0) minOf(it.done, it.owed) else it.done }
        val fardDone = fard.sumOf { if (it.owed > 0) minOf(it.done, it.owed) else it.done }
        val daysCovered = if (fard.size < 5) 0 else fard.minOf { if (it.owed > 0) minOf(it.done, it.owed) else it.done }
        val daysRemaining = if (fard.isEmpty()) 0 else fard.maxOf { it.remaining }

        fun sumSince(from: LocalDate) = perDay.filterKeys { !it.isBefore(from) && !it.isAfter(today) }.values.sum()
        val last7 = sumSince(today.minusDays(6)); val last30 = sumSince(today.minusDays(29))
        val first = perDay.keys.minOrNull()
        val window = if (first == null) 30 else minOf(30, QadaCalculator.daysBetween(first, today)).coerceAtLeast(1)
        val pace = if (first == null) 0.0 else last30.toDouble() / window
        val remaining = max(0, totalOwed - totalDone)
        val finish = if (pace <= 0.0 || remaining == 0) null else today.plusDays(ceil(remaining / pace).toLong().coerceAtMost(365L * 200))

        val active = perDay.filterValues { it > 0 }.keys.toSortedSet()
        var streak = 0
        var d = if (today in active) today else today.minusDays(1)
        while (d in active) { streak++; d = d.minusDays(1) }
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (day in active) { run = if (prev != null && prev.plusDays(1) == day) run + 1 else 1; best = max(best, run); prev = day }

        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekly = (11 downTo 0).map { i ->
            val ws = thisWeek.minusWeeks(i.toLong())
            ws to perDay.filterKeys { !it.isBefore(ws) && it.isBefore(ws.plusWeeks(1)) }.values.sum()
        }
        val thisMonth = today.withDayOfMonth(1)
        val monthly = (11 downTo 0).map { i ->
            val ms = thisMonth.minusMonths(i.toLong())
            ms to perDay.filterKeys { !it.isBefore(ms) && it.isBefore(ms.plusMonths(1)) }.values.sum()
        }
        return QadaReport(
            lines, totalOwed, totalDone, daysCovered, fardDone / 5.0, daysRemaining,
            perDay[today] ?: 0, last7, last30, pace, finish, streak, best, weekly, monthly, config.dailyGoal,
        )
    }
}

/** One collapsible history row: a day (with per-prayer counts), a month (weeks inside) or a year (months inside). */
data class QadaHistoryRow(
    val key: String,
    val kind: Kind,
    val start: LocalDate,
    val end: LocalDate,
    val total: Int,
    val activeDays: Int,
    val perPrayer: Map<QadaPrayer, Int> = emptyMap(),
    val children: List<QadaHistoryRow> = emptyList(),
) { enum class Kind { DAY, WEEK, MONTH, YEAR } }

/**
 * Compact history: one row per day for the recent [DAY_WINDOW] days, then one row per month (weeks inside) for the
 * last 12 months, then one row per year (months inside). Row count stays tiny however many entries exist.
 */
object QadaHistory {
    const val DAY_WINDOW = 14L

    fun dayWindowStart(today: LocalDate): LocalDate = today.minusDays(DAY_WINDOW - 1)

    fun build(perDay: Map<LocalDate, Int>, breakdown: Map<LocalDate, Map<QadaPrayer, Int>>, today: LocalDate): List<QadaHistoryRow> {
        val windowStart = dayWindowStart(today)
        val active = perDay.filter { it.value > 0 && !it.key.isAfter(today) }
        val days = active.filterKeys { !it.isBefore(windowStart) }.toSortedMap(reverseOrder()).map { (d, n) ->
            QadaHistoryRow("d:$d", QadaHistoryRow.Kind.DAY, d, d, n, 1, breakdown[d].orEmpty().filterValues { it > 0 })
        }
        val older = active.filterKeys { it.isBefore(windowStart) }
        val monthCut = today.withDayOfMonth(1).minusMonths(11)
        val months = older.filterKeys { !it.isBefore(monthCut) }.entries.groupBy { it.key.withDayOfMonth(1) }.toSortedMap(reverseOrder()).map { (ms, list) ->
            val me = ms.plusMonths(1).minusDays(1)
            val weeks = list.groupBy { it.key.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedMap(reverseOrder()).map { (ws, wl) ->
                val s = maxOf(ws, ms); val e = minOf(ws.plusDays(6), me)
                QadaHistoryRow("w:$s", QadaHistoryRow.Kind.WEEK, s, e, wl.sumOf { it.value }, wl.size)
            }
            QadaHistoryRow("m:$ms", QadaHistoryRow.Kind.MONTH, ms, me, list.sumOf { it.value }, list.size, children = weeks)
        }
        val years = older.filterKeys { it.isBefore(monthCut) }.entries.groupBy { it.key.year }.toSortedMap(reverseOrder()).map { (y, list) ->
            val inner = list.groupBy { it.key.withDayOfMonth(1) }.toSortedMap(reverseOrder()).map { (ms, ml) ->
                QadaHistoryRow("ym:$ms", QadaHistoryRow.Kind.MONTH, ms, ms.plusMonths(1).minusDays(1), ml.sumOf { it.value }, ml.size)
            }
            val s = list.minOf { it.key }; val e = list.maxOf { it.key }
            QadaHistoryRow("y:$y", QadaHistoryRow.Kind.YEAR, s, e, list.sumOf { it.value }, list.size, children = inner)
        }
        return days + months + years
    }
}
