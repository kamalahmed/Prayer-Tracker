package com.kamalahmed.prayerapp.tracker

import com.kamalahmed.prayerapp.core.model.Prayer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class StatsPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

/** Counts for any span. `missed` = explicitly missed + elapsed-but-unmarked; `due` = elapsed prayers. */
data class Counts(val onTime: Int = 0, val late: Int = 0, val missed: Int = 0, val due: Int = 0) {
    val prayed get() = onTime + late
    val completionPct: Int get() = if (due == 0) 0 else Math.round(prayed * 100f / due)
    val onTimePct: Int get() = if (prayed == 0) 0 else Math.round(onTime * 100f / prayed)
    operator fun plus(o: Counts) = Counts(onTime + o.onTime, late + o.late, missed + o.missed, due + o.due)
}

data class Bucket(val label: String, val start: LocalDate, val end: LocalDate, val counts: Counts, val tracked: Boolean = counts.due > 0) {
    /** Share of tracked prayers prayed on time (0..100), null when nothing was due. */
    val onTimeRate: Int? get() = if (counts.due == 0) null else Math.round(counts.onTime * 100f / counts.due)
}
data class PrayerRank(val prayer: Prayer, val counts: Counts) {
    val missedPct: Int get() = if (counts.due == 0) 0 else Math.round(counts.missed * 100f / counts.due)
}
data class StreakRun(val start: LocalDate, val end: LocalDate) { val days: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt() }

data class PeriodReport(
    val period: StatsPeriod,
    val start: LocalDate,
    val end: LocalDate,
    val title: String,
    val totals: Counts,
    val previous: Counts,
    val buckets: List<Bucket>,
    /** Sorted most-missed first. */
    val perPrayer: List<PrayerRank>,
    /** Day -> prayed count (0..5), null for days not yet tracked / in the future. */
    val heatmap: List<Pair<LocalDate, Int?>>,
    val insights: List<String>,
    val isEmpty: Boolean,
    /** First tracked day (days before it are shown as "no data", not as missed). */
    val firstDay: LocalDate = start,
) {
    /** Completion % change vs the previous period (percentage points). */
    val trend: Int get() = totals.completionPct - previous.completionPct
}

/** Pure statistics over tracker data (unit tested). */
object Stats {
    private fun prayed(s: PrayerStatus?) = s == PrayerStatus.ON_TIME || s == PrayerStatus.LATE

    fun range(period: StatsPeriod, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        StatsPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        StatsPeriod.MONTH -> anchor.withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
        StatsPeriod.YEAR -> anchor.withDayOfYear(1).let { it to it.plusYears(1).minusDays(1) }
    }

    fun shift(period: StatsPeriod, anchor: LocalDate, by: Long): LocalDate = when (period) {
        StatsPeriod.WEEK -> anchor.plusWeeks(by)
        StatsPeriod.MONTH -> anchor.plusMonths(by)
        StatsPeriod.YEAR -> anchor.plusYears(by)
    }

    /** First day tracking started: days before it are not counted as missed. */
    fun firstDay(data: TrackerData, today: LocalDate): LocalDate = data.keys.minOrNull()?.coerceAtMost(today) ?: today

    /** Prayers that have become due on [d]. */
    fun duePrayers(d: LocalDate, today: LocalDate, passedToday: List<Prayer>, first: LocalDate): List<Prayer> = when {
        d.isBefore(first) || d.isAfter(today) -> emptyList()
        d == today -> Prayer.obligatory.filter { it in passedToday }
        else -> Prayer.obligatory
    }

    fun dayCounts(data: TrackerData, d: LocalDate, today: LocalDate, passedToday: List<Prayer>, first: LocalDate, only: Prayer? = null): Counts {
        val due = duePrayers(d, today, passedToday, first)
        val day = data[d].orEmpty()
        var on = 0; var late = 0; var missed = 0; var dueN = 0
        for (p in Prayer.obligatory) {
            if (only != null && p != only) continue
            val st = day[p]
            val isDue = p in due || st != null && !d.isAfter(today) // a mark always counts (e.g. marked early)
            if (!isDue) continue
            dueN++
            when (st) {
                PrayerStatus.ON_TIME -> on++
                PrayerStatus.LATE -> late++
                PrayerStatus.MISSED, null -> missed++
            }
        }
        return Counts(on, late, missed, dueN)
    }

    fun counts(data: TrackerData, from: LocalDate, to: LocalDate, today: LocalDate, passedToday: List<Prayer>, first: LocalDate, only: Prayer? = null): Counts {
        var c = Counts(); var d = from
        while (!d.isAfter(to)) { c += dayCounts(data, d, today, passedToday, first, only); d = d.plusDays(1) }
        return c
    }

    /** All runs of consecutive complete days (all 5 prayed), newest first. */
    fun streakHistory(data: TrackerData): List<StreakRun> {
        val days = data.filterValues { day -> Prayer.obligatory.all { prayed(day[it]) } }.keys.sorted()
        val runs = ArrayList<StreakRun>()
        var s: LocalDate? = null; var e: LocalDate? = null
        for (d in days) {
            if (e != null && e.plusDays(1) == d) e = d
            else { if (s != null) runs += StreakRun(s, e!!); s = d; e = d }
        }
        if (s != null) runs += StreakRun(s, e!!)
        return runs.reversed()
    }

    fun report(data: TrackerData, period: StatsPeriod, anchor: LocalDate, today: LocalDate, passedToday: List<Prayer>, locale: Locale = Locale.getDefault()): PeriodReport {
        val first = firstDay(data, today)
        val (start, end) = range(period, anchor)
        val (pStart, pEnd) = range(period, shift(period, anchor, -1))
        val totals = counts(data, start, end, today, passedToday, first)
        val previous = counts(data, pStart, pEnd, today, passedToday, first)
        val buckets = when (period) {
            StatsPeriod.WEEK, StatsPeriod.MONTH -> generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { d ->
                val label = if (period == StatsPeriod.WEEK) d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) else "${d.dayOfMonth}"
                Bucket(label, d, d, dayCounts(data, d, today, passedToday, first), tracked = !d.isBefore(first) && !d.isAfter(today))
            }.toList()
            StatsPeriod.YEAR -> (0 until 12).map { i ->
                val ms = start.plusMonths(i.toLong()); val me = ms.plusMonths(1).minusDays(1)
                Bucket(ms.month.getDisplayName(TextStyle.SHORT, locale), ms, me, counts(data, ms, me, today, passedToday, first),
                    tracked = !me.isBefore(first) && !ms.isAfter(today))
            }
        }
        val perPrayer = Prayer.obligatory.map { p -> PrayerRank(p, counts(data, start, end, today, passedToday, first, p)) }
            .sortedWith(compareByDescending<PrayerRank> { it.counts.missed }.thenBy { it.prayer.ordinal })
        val heat = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { d ->
            d to if (d.isAfter(today) || d.isBefore(first)) null else Prayer.obligatory.count { prayed(data[d]?.get(it)) }
        }.toList()
        val isEmpty = data.isEmpty() || totals.due == 0
        return PeriodReport(period, start, end, title(period, start, end, locale), totals, previous, buckets, perPrayer, heat,
            insights(period, totals, previous, perPrayer), isEmpty, first)
    }

    fun title(period: StatsPeriod, start: LocalDate, end: LocalDate, locale: Locale): String = when (period) {
        StatsPeriod.WEEK -> "${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, locale)} – ${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, locale)} ${end.year}"
        StatsPeriod.MONTH -> "${start.month.getDisplayName(TextStyle.FULL, locale)} ${start.year}"
        StatsPeriod.YEAR -> "${start.year}"
    }

    fun insights(period: StatsPeriod, totals: Counts, previous: Counts, perPrayer: List<PrayerRank>): List<String> {
        if (totals.due == 0) return emptyList()
        val name = period.label.lowercase()
        val out = ArrayList<String>()
        val worst = perPrayer.firstOrNull() ?: return emptyList()
        if (worst.counts.missed > 0) out += "You miss ${worst.prayer.displayName} most often (${worst.counts.missed} times this $name)."
        else out += "No missed prayers this $name. MashaAllah!"
        val bestOnTime = perPrayer.filter { it.counts.prayed > 0 }.maxByOrNull { it.counts.onTimePct }
        if (bestOnTime != null) out += "${bestOnTime.prayer.displayName} is your most punctual prayer (${bestOnTime.counts.onTimePct}% on time)."
        if (previous.due > 0) {
            val t = totals.completionPct - previous.completionPct
            out += when {
                t > 0 -> "Up $t points from last $name. Keep going!"
                t < 0 -> "Down ${-t} points from last $name."
                else -> "Same completion as last $name."
            }
        }
        if (totals.late > 0 && totals.prayed > 0 && totals.onTimePct < 70) out += "${totals.late} prayers were late. Try setting adhan reminders."
        return out
    }
}
