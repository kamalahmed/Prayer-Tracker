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

    suspend fun undoLast(): Boolean = dao.deleteLatest() > 0
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun countToday(today: LocalDate = LocalDate.now()) = dao.countOn(today.toString())
    suspend fun currentConfig() = dao.config() ?: QadaConfig()
    suspend fun updateConfig(f: (QadaConfig) -> QadaConfig) { dao.upsertConfig(f(currentConfig())) }
    suspend fun resetAll() = dao.resetAll()

    companion object {
        const val MAX_BATCH = 1000
        const val MAX_COUNT = 1_000_000
    }
}
