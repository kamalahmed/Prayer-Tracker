package com.kamalahmed.prayerapp.summary

import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.qada.QadaEntry
import com.kamalahmed.prayerapp.tracker.*
import java.time.LocalDate

data class SummaryDay(val date: LocalDate,val fard: Counts=Counts(),val sunnah: Int=0,val otherPrayers: Long=0,val otherWorship: Long=0,val dhikr: Long=0,val quran: Boolean=false,val quranPages: Long=0,val quranMinutes: Long=0,val qada: Long=0)
data class CounterTotal(val item: NaflItem,val count: Long)
data class WorshipReport(val start: LocalDate,val end: LocalDate,val days: List<SummaryDay>,val fard: Counts=Counts(),val sunnah: Int=0,val otherPrayers: Long=0,val otherWorship: Long=0,val counters: List<CounterTotal> = emptyList(),val dhikr: Long=0,val quranDays: Int=0,val quranPages: Long=0,val quranMinutes: Long=0,val qada: Long=0,val bestDay: SummaryDay?=null,val fardRun: Int=0,val quranRun: Int=0)
/** Derived-only report. No writes, no invented mixed worship score, no hidden-history deletion. */
object WorshipSummary {
    private val baseline=NaflDefaults.items.filter { it.baseline }.map { it.id }.toSet()
    fun isOtherPrayer(item: NaflItem)=item.id !in baseline && !item.isCounter && (item.rakaat>0 || item.id in setOf("tahajjud","duha"))
    private fun parse(value: String)=try { LocalDate.parse(value) } catch(_: Exception) { null }
    fun build(tracker: TrackerData,items: List<NaflItem>,logs: List<NaflLog>,entries: List<QadaEntry>,start: LocalDate,end: LocalDate,today: LocalDate,passed: List<Prayer>): WorshipReport {
        require(end>=start && java.time.temporal.ChronoUnit.DAYS.between(start,end)<=366) { "Choose a day, week, month or year" }
        val through=minOf(end,today)
        val valid=logs.mapNotNull { l -> parse(l.date)?.takeIf { it in start..through }?.let { it to l } }
        val byDay=valid.groupBy({ it.first },{ it.second })
        val qada=entries.mapNotNull { e -> parse(e.date)?.takeIf { it in start..through }?.let { it to e.count.coerceAtLeast(0).toLong() } }.groupBy({it.first},{it.second})
        val definitions=(NaflDefaults.items+items).associateBy { it.id }
        val counterIds=definitions.values.filter { it.isCounter }.map { it.id }.toSet()
        val prayerIds=definitions.values.filter(::isOtherPrayer).map { it.id }.toSet()
        val first=Stats.firstDay(tracker.filterKeys { it<=today },today)
        val days=generateSequence(start) { it.plusDays(1) }.takeWhile { it<=through }.map { date ->
            val rows=byDay[date].orEmpty()
            val reading=rows.filter { it.itemId=="quran" }
            SummaryDay(date,Stats.dayCounts(tracker,date,today,passed,first),NaflStats.rakaat(rows),
                rows.filter { it.itemId in prayerIds }.sumOf { it.count.coerceIn(0,10_000).toLong() },
                rows.filter { it.itemId !in baseline && it.itemId !in prayerIds && it.itemId !in counterIds && it.itemId!="quran" }.sumOf { it.count.coerceIn(0,10_000).toLong() },
                rows.filter { it.itemId in counterIds }.sumOf { it.count.coerceIn(0,1_000_000).toLong() },reading.any { it.count>0 },
                reading.sumOf { it.pages.coerceIn(0,10_000).toLong() },reading.sumOf { it.minutes.coerceIn(0,1440).toLong() },qada[date].orEmpty().sum())
        }.toList()
        val counters=definitions.values.filter { it.isCounter }.map { item -> CounterTotal(item,valid.filter { it.second.itemId==item.id }.sumOf { it.second.count.coerceIn(0,1_000_000).toLong() }) }.sortedByDescending { it.count }
        fun run(test: (SummaryDay)->Boolean): Int { var n=0; var best=0; days.forEach { n=if(test(it)) n+1 else 0; best=maxOf(best,n) }; return best }
        return WorshipReport(start,end,days,days.fold(Counts()) { total,d -> total+d.fard },days.sumOf { it.sunnah },days.sumOf { it.otherPrayers },days.sumOf { it.otherWorship },counters,
            days.sumOf { it.dhikr },days.count { it.quran },days.sumOf { it.quranPages },days.sumOf { it.quranMinutes },days.sumOf { it.qada },
            days.filter { it.fard.prayed>0 }.maxByOrNull { it.fard.prayed },run { it.fard.prayed==5 },run { it.quran })
    }
}
