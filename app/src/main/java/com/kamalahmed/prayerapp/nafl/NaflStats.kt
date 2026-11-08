package com.kamalahmed.prayerapp.nafl

import com.kamalahmed.prayerapp.tracker.Stats
import com.kamalahmed.prayerapp.tracker.StatsPeriod
import java.time.LocalDate
import java.util.Locale

data class NaflItemStats(val item: NaflItem, val count: Int, val days: Int, val rate: Int, val streak: Int)
data class NaflDay(val date: LocalDate, val rakaat: Int, val completed: Int?)
data class NaflReport(val title: String, val items: List<NaflItemStats>, val daily: List<NaflDay>, val quranDays: Int, val pages: Int, val minutes: Int, val streak: Int)

object NaflStats {
    private fun date(s: String): LocalDate? = try { LocalDate.parse(s) } catch (_: Exception) { null }
    fun rakaat(logs: List<NaflLog>): Int = NaflDefaults.items.filter { it.baseline }.sumOf { item ->
        if (logs.any { it.itemId == item.id && it.count > 0 }) item.rakaat else 0
    }
    fun streak(logs: List<NaflLog>, id: String?, today: LocalDate): Int {
        val days = logs.filter { it.count > 0 && (id == null || it.itemId == id) }.mapNotNull { date(it.date) }.filter { !it.isAfter(today) }.toSet()
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) { count++; day = day.minusDays(1) }
        return count
    }
    fun report(items: List<NaflItem>, logs: List<NaflLog>, period: StatsPeriod, anchor: LocalDate, today: LocalDate): NaflReport {
        val (start, end) = Stats.range(period, anchor)
        val valid = logs.mapNotNull { l -> date(l.date)?.takeIf { !it.isAfter(today) }?.let { it to l } }
        val first = valid.minOfOrNull { it.first }
        val filtered = valid.filter { it.first >= start && it.first <= end }.map { it.second }
        val byDay = filtered.groupBy { it.date }
        val daily = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.map { d ->
            val dayLogs = byDay[d.toString()].orEmpty()
            NaflDay(d, rakaat(dayLogs), if (first == null || d < first || d > today) null else dayLogs.count { it.count > 0 })
        }.toList()
        val elapsed = daily.count { it.completed != null }
        val ranks = items.map { item ->
            val own = filtered.filter { it.itemId == item.id }
            val days = own.count { it.count > 0 }
            NaflItemStats(item, own.sumOf { it.count.coerceIn(0, 10_000) }, days, if (elapsed == 0) 0 else days * 100 / elapsed, streak(logs, item.id, today))
        }
        val quran = filtered.filter { it.itemId == "quran" }
        return NaflReport(Stats.title(period, start, end, Locale.getDefault()), ranks, daily, quran.count { it.count > 0 },
            quran.sumOf { it.pages.coerceIn(0, 10_000) }, quran.sumOf { it.minutes.coerceIn(0, 1440) }, streak(logs, null, today))
    }
}
