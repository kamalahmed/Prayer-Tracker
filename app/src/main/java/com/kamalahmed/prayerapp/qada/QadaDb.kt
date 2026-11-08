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

@Dao
interface QadaDao {
    @Query("SELECT * FROM qada_debt") fun observeDebt(): Flow<List<QadaDebt>>
    @Query("SELECT * FROM qada_entry ORDER BY at DESC, id DESC") fun observeEntries(): Flow<List<QadaEntry>>
    @Query("SELECT * FROM qada_entry ORDER BY at DESC, id DESC") suspend fun entries(): List<QadaEntry>
    @Query("SELECT * FROM qada_config WHERE id = 0") fun observeConfig(): Flow<QadaConfig?>
    @Query("SELECT * FROM qada_config WHERE id = 0") suspend fun config(): QadaConfig?
    @Query("SELECT COALESCE(SUM(count), 0) FROM qada_entry WHERE date = :date") suspend fun countOn(date: String): Int

    @Upsert suspend fun upsertDebt(d: List<QadaDebt>)
    @Upsert suspend fun upsertConfig(c: QadaConfig)
    @Insert suspend fun insert(e: QadaEntry): Long
    @Query("DELETE FROM qada_entry WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM qada_entry WHERE id = (SELECT id FROM qada_entry ORDER BY at DESC, id DESC LIMIT 1)") suspend fun deleteLatest(): Int
    @Query("DELETE FROM qada_entry") suspend fun clearEntries()
    @Query("DELETE FROM qada_debt") suspend fun clearDebt()

    @Transaction suspend fun replaceDebt(d: List<QadaDebt>) { clearDebt(); upsertDebt(d) }
    @Transaction suspend fun resetAll() { clearEntries(); clearDebt(); upsertConfig(QadaConfig()) }
}
