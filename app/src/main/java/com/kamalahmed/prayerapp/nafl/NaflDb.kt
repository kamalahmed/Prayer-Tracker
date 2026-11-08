package com.kamalahmed.prayerapp.nafl

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Stable IDs preserve history when an item is renamed or disabled. Never delete definitions. */
@Entity(tableName = "nafl_item")
data class NaflItem(
    @PrimaryKey val id: String,
    val name: String,
    val group: String = "WORSHIP",
    val rakaat: Int = 0,
    val baseline: Boolean = false,
    val enabled: Boolean = true,
    val position: Int = 0,
    @ColumnInfo(defaultValue = "'CHECKBOX'") val type: String = "CHECKBOX",
    @ColumnInfo(defaultValue = "0") val dailyTarget: Int = 0,
) { val isCounter: Boolean get() = type == "COUNTER" }
@Entity(tableName = "nafl_log", primaryKeys = ["date", "itemId"])
data class NaflLog(val date: String, val itemId: String, val count: Int = 0, val pages: Int = 0, val minutes: Int = 0)
@Entity(tableName = "quran_reminder")
data class QuranReminder(@PrimaryKey(autoGenerate = true) val id: Long = 0, val minute: Int, val enabled: Boolean = false, val alarmSound: Boolean = false)

@Dao
interface NaflDao {
    @Query("SELECT * FROM nafl_item ORDER BY position, id") fun observeItems(): Flow<List<NaflItem>>
    @Query("SELECT * FROM nafl_item ORDER BY position, id") suspend fun items(): List<NaflItem>
    @Query("SELECT * FROM nafl_log ORDER BY date") fun observeLogs(): Flow<List<NaflLog>>
    @Query("SELECT * FROM nafl_log ORDER BY date") suspend fun logs(): List<NaflLog>
    @Query("SELECT * FROM quran_reminder ORDER BY minute, id") fun observeReminders(): Flow<List<QuranReminder>>
    @Query("SELECT * FROM quran_reminder ORDER BY minute, id") suspend fun reminders(): List<QuranReminder>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDefaults(items: List<NaflItem>)
    @Upsert suspend fun saveItem(item: NaflItem)
    @Upsert suspend fun saveLog(log: NaflLog)
    @Query("SELECT * FROM nafl_item WHERE id = :id") suspend fun item(id: String): NaflItem?
    @Query("SELECT * FROM nafl_log WHERE date = :date AND itemId = :id") suspend fun log(date: String, id: String): NaflLog?
    /** Room transaction serializes app and widget writes, including separate repository instances. */
    @Transaction suspend fun adjustCounter(date: String, id: String, delta: Int): CounterResult {
        val item = item(id) ?: throw IllegalArgumentException("Counter not found")
        require(item.isCounter && item.enabled) { "This counter is hidden or unavailable" }
        val row = log(date, id) ?: NaflLog(date, id)
        val old = row.count.coerceIn(0, 1_000_000)
        val next = (old.toLong() + delta).coerceIn(0L, 1_000_000L).toInt()
        saveLog(row.copy(count = next))
        return CounterResult(old, next, item.dailyTarget > 0 && next > old && next / item.dailyTarget > old / item.dailyTarget)
    }
    @Upsert suspend fun saveReminder(reminder: QuranReminder): Long
    @Query("DELETE FROM quran_reminder WHERE id = :id") suspend fun deleteReminder(id: Long)
}

object NaflDefaults {
    val items = listOf(
        NaflItem("fajr_before", "Before Fajr", "SUNNAH", 2, true, position = 0),
        NaflItem("dhuhr_before", "Before Dhuhr", "SUNNAH", 4, true, position = 1),
        NaflItem("dhuhr_after", "After Dhuhr", "SUNNAH", 2, true, position = 2),
        NaflItem("maghrib_after", "After Maghrib", "SUNNAH", 2, true, position = 3),
        NaflItem("isha_after", "After Isha", "SUNNAH", 2, true, position = 4),
        NaflItem("asr_before", "Before Asr", "SUNNAH", 4, enabled = false, position = 5),
        NaflItem("witr", "Witr", "SUNNAH", 3, enabled = false, position = 6),
        NaflItem("quran", "Quran reading", position = 7),
        NaflItem("tahajjud", "Tahajjud", position = 8),
        NaflItem("dhikr", "Tasbih / dhikr", position = 9, type = "COUNTER"),
        NaflItem("ruqyah", "Ruqyah", position = 10),
        NaflItem("duha", "Duha", position = 11),
        NaflItem("istighfar", "Istighfar", position = 12, type = "COUNTER", dailyTarget = 100),
        NaflItem("salawat", "Durud / Salawat", position = 13, type = "COUNTER", dailyTarget = 100),
        NaflItem("subhanallah", "SubhanAllah", position = 14, type = "COUNTER", dailyTarget = 33),
        NaflItem("alhamdulillah", "Alhamdulillah", position = 15, type = "COUNTER", dailyTarget = 33),
        NaflItem("allahuakbar", "Allahu Akbar", position = 16, type = "COUNTER", dailyTarget = 33),
        NaflItem("lailaha", "La ilaha illallah", position = 17, type = "COUNTER", dailyTarget = 100),
    )
}

data class CounterResult(val before: Int, val after: Int, val targetReached: Boolean)
