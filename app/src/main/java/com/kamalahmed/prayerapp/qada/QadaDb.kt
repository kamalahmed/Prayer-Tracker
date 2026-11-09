package com.kamalahmed.prayerapp.qada

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Prayers that can be made up. Witr is optional (Hanafi: wajib). */
enum class QadaPrayer(val displayName: String) {
    FAJR("Fajr"), DHUHR("Dhuhr"), ASR("Asr"), MAGHRIB("Maghrib"), ISHA("Isha"), WITR("Witr");
    companion object {
        val fard = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
        fun of(name: String) = entries.firstOrNull { it.name == name }
    }
}

/** How many of a prayer the user owes in total (the starting debt, editable). */
@Entity(tableName = "qada_debt")
data class QadaDebt(@PrimaryKey val prayer: String, val owed: Int)

/** One logged batch of made-up prayers. A history of entries (not a counter) so it can be undone and charted. */
@Entity(tableName = "qada_entry", indices = [Index("date")])
data class QadaEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prayer: String,
    val count: Int,
    /** ISO local date the prayers were made up. */
    val date: String,
    val at: Long = System.currentTimeMillis(),
)

@Entity(tableName = "qada_config")
data class QadaConfig(
    @PrimaryKey val id: Int = 0,
    val witr: Boolean = false,
    val dailyGoal: Int = 0,
    val reminder: Boolean = false,
    /** Minutes after midnight for the reminder. */
    val reminderMinute: Int = 20 * 60,
    val setupDone: Boolean = false,
)

/** Aggregate rows: the Qada screen never loads every entry (18,000+ prayers stay cheap). */
data class QadaPrayerTotal(val prayer: String, val total: Long)
data class QadaDayTotal(val date: String, val total: Long)
data class QadaDayPrayerTotal(val date: String, val prayer: String, val total: Long)

@Dao
interface QadaDao {
    @Query("SELECT * FROM qada_debt") fun observeDebt(): Flow<List<QadaDebt>>
    @Query("SELECT * FROM qada_entry ORDER BY at DESC, id DESC") fun observeEntries(): Flow<List<QadaEntry>>
    @Query("SELECT * FROM qada_entry ORDER BY at DESC, id DESC") suspend fun entries(): List<QadaEntry>
    @Query("SELECT * FROM qada_config WHERE id = 0") fun observeConfig(): Flow<QadaConfig?>
    @Query("SELECT * FROM qada_config WHERE id = 0") suspend fun config(): QadaConfig?
    @Query("SELECT COALESCE(SUM(count), 0) FROM qada_entry WHERE date = :date") suspend fun countOn(date: String): Int

    @Query("SELECT prayer, COALESCE(SUM(count), 0) AS total FROM qada_entry GROUP BY prayer") fun observeTotals(): Flow<List<QadaPrayerTotal>>
    @Query("SELECT date, COALESCE(SUM(count), 0) AS total FROM qada_entry GROUP BY date") fun observeDayTotals(): Flow<List<QadaDayTotal>>
    @Query("SELECT date, COALESCE(SUM(count), 0) AS total FROM qada_entry GROUP BY date") suspend fun dayTotals(): List<QadaDayTotal>
    @Query("SELECT date, prayer, COALESCE(SUM(count), 0) AS total FROM qada_entry WHERE date >= :from GROUP BY date, prayer") fun observeBreakdown(from: String): Flow<List<QadaDayPrayerTotal>>
    @Query("SELECT date, prayer, COALESCE(SUM(count), 0) AS total FROM qada_entry WHERE date = :date GROUP BY prayer") suspend fun breakdownOn(date: String): List<QadaDayPrayerTotal>
    /** Today's made-up prayers logged up to [until] (epoch ms): lets Insights compare with yesterday at the same time. */
    @Query("SELECT COALESCE(SUM(count), 0) FROM qada_entry WHERE date = :date AND at <= :until") suspend fun countOnUntil(date: String, until: Long): Int
    @Query("SELECT * FROM qada_entry WHERE date = :date AND prayer = :prayer ORDER BY at DESC, id DESC") suspend fun entriesOn(date: String, prayer: String): List<QadaEntry>
    @Query("SELECT COUNT(*) FROM qada_entry") suspend fun entryCount(): Int
    @Query("UPDATE qada_entry SET count = :count WHERE id = :id") suspend fun updateCount(id: Long, count: Int)
    @Query("DELETE FROM qada_entry WHERE id IN (:ids)") suspend fun deleteIds(ids: List<Long>): Int

    @Upsert suspend fun upsertDebt(d: List<QadaDebt>)
    @Upsert suspend fun upsertConfig(c: QadaConfig)
    @Insert suspend fun insert(e: QadaEntry): Long
    @Query("DELETE FROM qada_entry WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM qada_entry WHERE id = (SELECT id FROM qada_entry ORDER BY at DESC, id DESC LIMIT 1)") suspend fun deleteLatest(): Int
    @Query("DELETE FROM qada_entry") suspend fun clearEntries()
    @Query("DELETE FROM qada_debt") suspend fun clearDebt()

    @Transaction suspend fun replaceDebt(d: List<QadaDebt>) { clearDebt(); upsertDebt(d) }
    /**
     * Sets how many [prayer] were made up on [date]. Increases insert one entry for the difference; decreases trim the
     * newest entries of that day first. Other days and prayers are never touched. Returns the inserted id (or null).
     */
    @Transaction suspend fun setDayCount(date: String, prayer: String, target: Int, now: Long): Long? {
        val rows = entriesOn(date, prayer)
        val current = rows.sumOf { it.count.coerceAtLeast(0) }
        if (target > current) return insert(QadaEntry(prayer = prayer, count = target - current, date = date, at = now))
        var excess = current - target
        for (row in rows) {
            if (excess <= 0) break
            val c = row.count.coerceAtLeast(0)
            if (c <= excess) { delete(row.id); excess -= c } else { updateCount(row.id, c - excess); excess = 0 }
        }
        return null
    }

    @Transaction suspend fun resetAll() { clearEntries(); clearDebt(); upsertConfig(QadaConfig()) }
}
