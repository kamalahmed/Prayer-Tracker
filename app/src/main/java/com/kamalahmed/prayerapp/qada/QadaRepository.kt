package com.kamalahmed.prayerapp.qada

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QadaRepository @Inject constructor(private val dao: QadaDao) {
    val debt: Flow<List<QadaDebt>> = dao.observeDebt()
    val entries: Flow<List<QadaEntry>> = dao.observeEntries()
    val config: Flow<QadaConfig> = dao.observeConfig().map { it ?: QadaConfig() }
    val totals: Flow<Map<QadaPrayer, Int>> = dao.observeTotals().map { rows ->
        rows.mapNotNull { r -> QadaPrayer.of(r.prayer)?.let { it to r.total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt() } }.toMap()
    }
    val dayTotals: Flow<Map<LocalDate, Int>> = dao.observeDayTotals().map(::parseDays)
    /** Per-day, per-prayer totals from [from] on (only the recent window is ever expanded per prayer). */
    fun breakdown(from: LocalDate): Flow<Map<LocalDate, Map<QadaPrayer, Int>>> = dao.observeBreakdown(from.toString()).map { rows ->
        rows.mapNotNull { r ->
            val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return@mapNotNull null
            val p = QadaPrayer.of(r.prayer) ?: return@mapNotNull null
            Triple(d, p, r.total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        }.groupBy { it.first }.mapValues { (_, l) -> l.associate { it.second to it.third } }
    }

    suspend fun setDebt(counts: Map<QadaPrayer, Int>, witr: Boolean) {
        dao.replaceDebt(counts.filterKeys { it != QadaPrayer.WITR || witr }.map { (p, n) -> QadaDebt(p.name, n.coerceIn(0, MAX_COUNT)) })
        val c = dao.config() ?: QadaConfig()
        dao.upsertConfig(c.copy(witr = witr, setupDone = true))
    }

    /** Logs [count] made-up prayers (1..[MAX_BATCH]). Returns the entry id. */
    suspend fun log(prayer: QadaPrayer, count: Int, date: LocalDate = LocalDate.now()): Long {
        require(count in 1..MAX_BATCH) { "count out of range" }
        return dao.insert(QadaEntry(prayer = prayer.name, count = count, date = date.toString()))
    }

    /** Logs several batches atomically enough for undo: returns every inserted id. */
    suspend fun logAll(items: List<Pair<QadaPrayer, Int>>, date: LocalDate = LocalDate.now()): List<Long> = items.map { (p, n) -> log(p, n, date) }
    /** Undoes exactly the entries of one action (snackbar Undo); returns how many rows were removed. */
    suspend fun deleteIds(ids: List<Long>): Int = if (ids.isEmpty()) 0 else dao.deleteIds(ids)
    /** Edit-day stepper target for one prayer on one day (0..[MAX_BATCH] * 10). */
    suspend fun setDayCount(date: LocalDate, prayer: QadaPrayer, target: Int): Long? {
        require(!date.isAfter(LocalDate.now())) { "Future days cannot be edited" }
        require(target in 0..MAX_BATCH * 10) { "count out of range" }
        return dao.setDayCount(date.toString(), prayer.name, target, System.currentTimeMillis())
    }
    suspend fun countOnUntil(date: LocalDate, until: Long) = dao.countOnUntil(date.toString(), until)

    suspend fun undoLast(): Boolean = dao.deleteLatest() > 0
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun countToday(today: LocalDate = LocalDate.now()) = dao.countOn(today.toString())
    suspend fun currentConfig() = dao.config() ?: QadaConfig()
    suspend fun updateConfig(f: (QadaConfig) -> QadaConfig) { dao.upsertConfig(f(currentConfig())) }
    suspend fun resetAll() = dao.resetAll()

    companion object {
        fun parseDays(rows: List<QadaDayTotal>): Map<LocalDate, Int> = rows.mapNotNull { r ->
            runCatching { LocalDate.parse(r.date) }.getOrNull()?.let { it to r.total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt() }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.sum() }

        const val MAX_BATCH = 1000
        const val MAX_COUNT = 1_000_000
    }
}
