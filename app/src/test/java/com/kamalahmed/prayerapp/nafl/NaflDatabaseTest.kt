package com.kamalahmed.prayerapp.nafl

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class NaflDatabaseTest {
    @Test fun v2ToV3PreservesAllExistingTablesAndSupportsNewOnes() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "nafl-migrate.db"
        ctx.deleteDatabase(name)
        val schema = org.json.JSONObject(File("schemas/com.kamalahmed.prayerapp.tracker.TrackerDatabase/2.json").readText()).getJSONObject("database")
        val path = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val ent = entities.getJSONObject(i)
                db.execSQL(ent.getString("createSql").replace("\${TABLE_NAME}", ent.getString("tableName")))
                val indices = ent.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", ent.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO prayer_log VALUES ('2026-11-01','FAJR','ON_TIME',1)")
            db.execSQL("INSERT INTO qada_debt VALUES ('FAJR',500)")
            db.execSQL("INSERT INTO qada_entry VALUES (1,'FAJR',3,'2026-11-01',1)")
            db.execSQL("INSERT INTO qada_config VALUES (0,1,5,1,1200,1)")
            db.version = 2
        }
        val room = Room.databaseBuilder(ctx, TrackerDatabase::class.java, name).addMigrations(*TrackerDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            assertEquals(1, room.dao().all().size)
            assertEquals(3, room.qada().entries().single().count)
            assertEquals(500, room.qada().observeDebt().first().single().owed)
            assertEquals(com.kamalahmed.prayerapp.qada.QadaConfig(witr = true, dailyGoal = 5, reminder = true, reminderMinute = 1200, setupDone = true), room.qada().config())
            assertEquals(com.kamalahmed.prayerapp.tracker.PrayerStatus.ON_TIME, room.dao().all().single().status)
            val repo = NaflRepository(room.nafl())
            repo.ensureDefaults()
            assertEquals(NaflDefaults.items.size, room.nafl().items().size)
            assertTrue(room.nafl().reminders().isEmpty())
            repo.saveLog(LocalDate.now(), "quran", 1, 4, 15)
            assertEquals(4, room.nafl().logs().single().pages)
        } finally { room.close(); ctx.deleteDatabase(name) }
    }
    @Test fun reseedingKeepsRenamesAndDisabledChoicesAndLogsAreUpserted() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = NaflRepository(db.nafl())
            repo.ensureDefaults()
            repo.saveItem(NaflDefaults.items.first().copy(name = "My Fajr", enabled = false))
            repo.ensureDefaults()
            assertEquals("My Fajr", db.nafl().items().first { it.id == "fajr_before" }.name)
            assertFalse(db.nafl().items().first { it.id == "fajr_before" }.enabled)
            repo.saveLog(LocalDate.now(), "quran", 1, 5, 10)
            repo.saveLog(LocalDate.now(), "quran", 0, 5, 10)
            assertEquals(1, db.nafl().logs().size)
            assertEquals(0, db.nafl().logs().single().count)
            repo.addItem("My reading")
            assertEquals(1, db.nafl().items().count { it.name == "My reading" })
        } finally { db.close() }
    }
    @Test fun invalidInputCannotCreateRows() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = NaflRepository(db.nafl()); repo.ensureDefaults()
            try { repo.saveLog(LocalDate.now().plusDays(1), "quran", 1); fail("future log accepted") } catch (_: IllegalArgumentException) {}
            try { repo.addItem("  "); fail("blank item accepted") } catch (_: IllegalArgumentException) {}
            try { repo.saveLog(LocalDate.now(), "unknown", 1); fail("unknown item accepted") } catch (_: IllegalArgumentException) {}
            assertTrue(db.nafl().logs().isEmpty())
        } finally { db.close() }
    }
}
