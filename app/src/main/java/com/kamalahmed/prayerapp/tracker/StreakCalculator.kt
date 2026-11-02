package com.kamalahmed.prayerapp.tracker

import com.kamalahmed.prayerapp.core.model.Prayer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Day -> prayer -> status. Missing entries mean "not marked". */
typealias TrackerData = Map<LocalDate, Map<Prayer, PrayerStatus>>

data class PeriodStats(val prayed: Int, val onTime: Int, val late: Int, val missed: Int, val possible: Int) {
    val prayedPct: Int get() = if (possible == 0) 0 else prayed * 100 / possible
    val onTimePct: Int get() = if (prayed == 0) 0 else onTime * 100 / prayed
}

data class StreakStats(
    /** Consecutive days with all 5 prayers prayed, ending today or yesterday. */
    val currentDays: Int,
    val bestDays: Int,
    /** Consecutive prayed prayers, counting back from the latest marked/passed prayer. */
    val currentPrayers: Int,
    val week: PeriodStats,
    val month: PeriodStats,
    /** 0..5 prayed count per day for the heatmap (last N days, oldest first). */
    val heatmap: List<Pair<LocalDate, Int>>,
)

object StreakCalculator {

    fun prayed(s: PrayerStatus?) = s == PrayerStatus.ON_TIME || s == PrayerStatus.LATE

    fun dayComplete(day: Map<Prayer, PrayerStatus>?) = day != null && Prayer.obligatory.all { prayed(day[it]) }

    /**
     * @param passedToday prayers of today whose time has already started (used so an unfinished
     * today does not break the streak, and for the prayer-level streak).
     */
    fun compute(data: TrackerData, today: LocalDate, passedToday: List<Prayer>, heatmapDays: Int = 84): StreakStats {
        // Current day streak: today counts if complete; otherwise start from yesterday.
        var d = if (dayComplete(data[today])) today else today.minusDays(1)
        var current = 0
        while (dayComplete(data[d])) { current++; d = d.minusDays(1) }

        // Best day streak over all data.
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (date in data.keys.sorted()) {
            if (dayComplete(data[date])) {
                run = if (prev != null && prev.plusDays(1) == date) run + 1 else 1
                prev = date
                best = maxOf(best, run)
            } else { run = 0; prev = null }
        }
        best = maxOf(best, current)

        // Prayer-level streak walking backwards over passed prayers.
        var prayers = 0
        var day = today
        var list = passedToday.sortedByDescending { it.ordinal }
        // Don't let the current (still open) prayer break the streak if unmarked.
        var skipFirstUnmarked = true
        loop@ while (true) {
            for (p in list) {
                val st = data[day]?.get(p)
                if (prayed(st)) { prayers++; skipFirstUnmarked = false }
                else if (st == null && skipFirstUnmarked) { skipFirstUnmarked = false }
                else break@loop
            }
            day = day.minusDays(1)
            if (data.keys.none { !it.isAfter(day) }) break
            list = Prayer.obligatory.sortedByDescending { it.ordinal }
        }

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthStart = today.withDayOfMonth(1)
        return StreakStats(
            currentDays = current,
            bestDays = best,
            currentPrayers = prayers,
            week = period(data, weekStart, today, passedToday),
            month = period(data, monthStart, today, passedToday),
            heatmap = (heatmapDays - 1 downTo 0).map { i ->
                val date = today.minusDays(i.toLong())
                date to Prayer.obligatory.count { prayed(data[date]?.get(it)) }
            },
        )
    }

    fun period(data: TrackerData, from: LocalDate, today: LocalDate, passedToday: List<Prayer>): PeriodStats {
        var prayed = 0; var onTime = 0; var late = 0; var missed = 0; var possible = 0
        var d = from
        while (!d.isAfter(today)) {
            val due = if (d == today) passedToday.filter { it.isPrayer } else Prayer.obligatory
            possible += due.size
            for (p in Prayer.obligatory) when (data[d]?.get(p)) {
                PrayerStatus.ON_TIME -> { prayed++; onTime++ }
                PrayerStatus.LATE -> { prayed++; late++ }
                PrayerStatus.MISSED -> missed++
                null -> {}
            }
            d = d.plusDays(1)
        }
        return PeriodStats(prayed, onTime, late, missed, maxOf(possible, prayed))
    }
}
