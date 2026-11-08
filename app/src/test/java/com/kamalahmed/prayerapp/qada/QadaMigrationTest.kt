package com.kamalahmed.prayerapp.qada

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrading from beta.5 (schema v1, created from the exported 1.json) keeps every prayer_log row and adds the
 * Qada tables, which Room then validates against the v2 schema (a mismatch throws on open).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QadaMigrationTest {
    @Test fun v1ToV2KeepsTrackerData() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        val schema = org.json.JSONObject(File("schemas/com.kamalahmed.prayerapp.tracker.TrackerDatabase/1.json").readText()).getJSONObject("database")
        val path = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val ent = schema.getJSONArray("entities").getJSONObject(0)
            db.execSQL(ent.getString("createSql").replace("\${TABLE_NAME}", ent.getString("tableName")))
            for (i in 0 until schema.getJSONArray("setupQueries").length()) db.execSQL(schema.getJSONArray("setupQueries").getString(i))
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.getString("identityHash")}')")
            db.execSQL("INSERT INTO prayer_log VALUES ('2026-11-01','FAJR','ON_TIME',1)")
            db.execSQL("INSERT INTO prayer_log VALUES ('2026-11-01','ISHA','LATE',2)")
            db.execSQL("INSERT INTO prayer_log VALUES ('2026-11-02','ASR','MISSED',3)")
            db.version = 1
        }
        val room = Room.databaseBuilder(ctx, TrackerDatabase::class.java, name).addMigrations(*TrackerDatabase.MIGRATIONS).allowMainThreadQueries().build()
        val logs = room.dao().all().sortedBy { it.date + it.prayer }
        assertEquals(3, logs.size)
        assertEquals(PrayerStatus.ON_TIME, logs.first { it.prayer == "FAJR" }.status)
        assertEquals(PrayerStatus.MISSED, logs.first { it.prayer == "ASR" }.status)
        val repo = QadaRepository(room.qada())
        assertEquals(QadaConfig(), repo.config.first())
        repo.setDebt(mapOf(QadaPrayer.FAJR to 10), witr = false)
        repo.log(QadaPrayer.FAJR, 3)
        assertEquals(1, room.qada().entries().size)
        assertEquals(true, repo.undoLast())
        assertEquals(0, room.qada().entries().size)
        room.close()
    }
}
