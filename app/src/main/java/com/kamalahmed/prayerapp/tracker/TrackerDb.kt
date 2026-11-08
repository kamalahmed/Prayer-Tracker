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

@Database(
    entities = [PrayerLog::class, com.kamalahmed.prayerapp.qada.QadaDebt::class, com.kamalahmed.prayerapp.qada.QadaEntry::class, com.kamalahmed.prayerapp.qada.QadaConfig::class],
    version = 2, exportSchema = true,
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun dao(): PrayerLogDao
    abstract fun qada(): com.kamalahmed.prayerapp.qada.QadaDao

    companion object {
        /** v1 -> v2 (beta.6): adds the Qada (make-up prayer) tables. prayer_log is untouched. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `qada_debt` (`prayer` TEXT NOT NULL, `owed` INTEGER NOT NULL, PRIMARY KEY(`prayer`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `qada_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `prayer` TEXT NOT NULL, `count` INTEGER NOT NULL, `date` TEXT NOT NULL, `at` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_qada_entry_date` ON `qada_entry` (`date`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `qada_config` (`id` INTEGER NOT NULL, `witr` INTEGER NOT NULL, `dailyGoal` INTEGER NOT NULL, `reminder` INTEGER NOT NULL, `reminderMinute` INTEGER NOT NULL, `setupDone` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object TrackerModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): TrackerDatabase =
        Room.databaseBuilder(ctx, TrackerDatabase::class.java, "tracker.db").addMigrations(*TrackerDatabase.MIGRATIONS).build()

    @Provides fun dao(db: TrackerDatabase): PrayerLogDao = db.dao()
    @Provides fun qadaDao(db: TrackerDatabase): com.kamalahmed.prayerapp.qada.QadaDao = db.qada()
}
