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
        require(count in 0..(if (item.rakaat > 0 || id == "quran") 1 else 10_000))
        require(pages in 0..10_000 && minutes in 0..1440)
        dao.saveLog(NaflLog(date.toString(), id, count, if (id == "quran") pages else 0, if (id == "quran") minutes else 0))
    }
    suspend fun saveItem(item: NaflItem) = mutex.withLock {
        val old = dao.items().firstOrNull { it.id == item.id } ?: throw IllegalArgumentException("Unknown worship item")
        require(item.name.trim().isNotEmpty() && item.name.trim().length <= 60)
        // Metadata is immutable: editing labels must never change historical baseline totals.
        dao.saveItem(old.copy(name = item.name.trim(), enabled = item.enabled))
    }
    suspend fun addItem(name: String) = mutex.withLock {
        require(name.trim().isNotEmpty() && name.trim().length <= 60)
        val current = dao.items()
        require(current.size < 62) { "Limit reached (50 custom items)" }
        dao.saveItem(NaflItem(UUID.randomUUID().toString(), name.trim(), position = (current.maxOfOrNull { it.position } ?: 0) + 1))
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
