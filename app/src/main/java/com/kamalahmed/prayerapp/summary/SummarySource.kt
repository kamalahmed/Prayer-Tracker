package com.kamalahmed.prayerapp.summary

import androidx.room.withTransaction
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.qada.QadaEntry
import com.kamalahmed.prayerapp.tracker.*
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class SummaryHistory(val tracker: TrackerData,val items: List<NaflItem>,val logs: List<NaflLog>,val qada: List<QadaEntry>) {
    fun report(start: LocalDate,end: LocalDate,today: LocalDate,passed: List<com.kamalahmed.prayerapp.core.model.Prayer>)=
        WorshipSummary.build(tracker,items,logs,qada,start,end,today,passed)
}

@Singleton
class SummarySource @Inject constructor(private val db: TrackerDatabase) {
    // One transaction per invalidation: never combine pre-import fard with post-import optional counts.
    val history=db.invalidationTracker.createFlow("prayer_log","nafl_item","nafl_log","qada_entry",emitInitialState=true).map { read() }
    suspend fun read(): SummaryHistory=db.withTransaction {
        val prayerLogs=db.dao().all().filter { runCatching { LocalDate.parse(it.date) }.isSuccess }
        // Qada as one synthetic row per day (aggregate query): summaries only need date + count, never 18,000 rows.
        SummaryHistory(prayerLogs.toTrackerData(),db.nafl().items(),db.nafl().logs(),db.qada().dayTotals().map { QadaEntry(prayer="DAY",count=it.total.coerceIn(0,Int.MAX_VALUE.toLong()).toInt(),date=it.date,at=0) })
    }
}
