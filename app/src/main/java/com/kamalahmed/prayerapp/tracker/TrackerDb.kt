package com.kamalahmed.prayerapp.tracker

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

enum class PrayerStatus { ON_TIME, LATE, MISSED }

@Entity(tableName = "prayer_log", primaryKeys = ["date", "prayer"])
data class PrayerLog(
    /** ISO local date, e.g. 2026-11-02 */
    val date: String,
    /** Prayer enum name */
    val prayer: String,
    val status: PrayerStatus,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface PrayerLogDao {
    @Upsert suspend fun upsert(log: PrayerLog)

    @Query("DELETE FROM prayer_log WHERE date = :date AND prayer = :prayer")
    suspend fun delete(date: String, prayer: String)

    @Query("SELECT * FROM prayer_log WHERE date >= :from AND date <= :to")
    fun observeRange(from: String, to: String): Flow<List<PrayerLog>>

    @Query("SELECT * FROM prayer_log WHERE date >= :from AND date <= :to")
    suspend fun range(from: String, to: String): List<PrayerLog>

    @Query("SELECT * FROM prayer_log")
    fun observeAll(): Flow<List<PrayerLog>>

    @Query("SELECT * FROM prayer_log")
    suspend fun all(): List<PrayerLog>

    @Query("DELETE FROM prayer_log")
    suspend fun clear()
}

@Database(entities = [PrayerLog::class], version = 1, exportSchema = true)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun dao(): PrayerLogDao
}

@Module
@InstallIn(SingletonComponent::class)
object TrackerModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): TrackerDatabase =
        Room.databaseBuilder(ctx, TrackerDatabase::class.java, "tracker.db").build()

    @Provides fun dao(db: TrackerDatabase): PrayerLogDao = db.dao()
}
