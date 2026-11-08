package com.kamalahmed.prayerapp.nafl

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaflRepository @Inject constructor(private val dao: NaflDao) {
    val items = dao.observeItems()
    val logs = dao.observeLogs()
    val reminders = dao.observeReminders()
    private val mutex = Mutex()
    suspend fun ensureDefaults() = dao.insertDefaults(NaflDefaults.items)
    suspend fun saveLog(date: LocalDate, id: String, count: Int, pages: Int = 0, minutes: Int = 0) = mutex.withLock {
        require(!date.isAfter(LocalDate.now())) { "Future days cannot be logged" }
        val item = dao.items().firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown worship item")
        require(count in 0..(if (item.rakaat > 0 || id == "quran") 1 else if (item.isCounter) 1_000_000 else 10_000))
        require(pages in 0..10_000 && minutes in 0..1440)
        dao.saveLog(NaflLog(date.toString(), id, count, if (id == "quran") pages else 0, if (id == "quran") minutes else 0))
    }
    suspend fun saveItem(item: NaflItem) = mutex.withLock {
        val old = dao.items().firstOrNull { it.id == item.id } ?: throw IllegalArgumentException("Unknown worship item")
        require(item.name.trim().isNotEmpty() && item.name.trim().length <= 60)
        // Metadata is immutable: editing labels must never change historical baseline totals.
        require(item.type in setOf("CHECKBOX", "COUNTER") && item.dailyTarget in 0..1_000_000)
        require((old.rakaat == 0 && old.id != "quran") || item.type == "CHECKBOX")
        dao.saveItem(old.copy(name = item.name.trim(), enabled = item.enabled, type = item.type, dailyTarget = if (item.isCounter) item.dailyTarget else 0))
    }
    suspend fun addItem(name: String, type: String = "CHECKBOX", target: Int = 0) = mutex.withLock {
        require(name.trim().isNotEmpty() && name.trim().length <= 60)
        val current = dao.items()
        require(current.size < NaflDefaults.items.size + 50) { "Limit reached (50 custom items)" }
        require(type in setOf("CHECKBOX", "COUNTER") && target in 0..1_000_000)
        dao.saveItem(NaflItem(UUID.randomUUID().toString(), name.trim(), position = (current.maxOfOrNull { it.position } ?: 0) + 1, type = type, dailyTarget = if (type == "COUNTER") target else 0))
    }
    suspend fun adjustCounter(id: String, delta: Int, date: LocalDate = LocalDate.now()): CounterResult {
        require(!date.isAfter(LocalDate.now()) && delta in -1_000_000..1_000_000)
        return dao.adjustCounter(date.toString(), id, delta)
    }
    suspend fun saveReminder(r: QuranReminder) = mutex.withLock {
        require(r.minute in 0..1439)
        require(r.id >= 0)
        val all = dao.reminders()
        require(r.id != 0L || all.size < 12) { "You can set up to 12 reminders" }
        require(r.id == 0L || all.any { it.id == r.id })
        require(all.none { it.id != r.id && it.minute == r.minute }) { "A reminder already exists at this time" }
        dao.saveReminder(r)
    }
}
