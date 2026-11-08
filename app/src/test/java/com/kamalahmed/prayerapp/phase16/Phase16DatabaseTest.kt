package com.kamalahmed.prayerapp.phase16

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.core.model.Prayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = android.app.Application::class)
class Phase16DatabaseTest {
    @Test fun v3MigrationKeepsEveryTableRenameHiddenFlagReadingAndReminder() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "phase16-upgrade.db"
        ctx.deleteDatabase(name)
        val schema = org.json.JSONObject(File("schemas/com.kamalahmed.prayerapp.tracker.TrackerDatabase/3.json").readText()).getJSONObject("database")
        val path = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path,null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val ent = entities.getJSONObject(i)
                db.execSQL(ent.getString("createSql").replace("\${TABLE_NAME}",ent.getString("tableName")))
                val indices = ent.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",ent.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO prayer_log VALUES ('2026-11-01','FAJR','ON_TIME',1)")
            db.execSQL("INSERT INTO qada_debt VALUES ('FAJR',500)")
            db.execSQL("INSERT INTO qada_entry VALUES (1,'FAJR',3,'2026-11-01',1)")
            db.execSQL("INSERT INTO qada_config VALUES (0,1,5,1,1200,1)")
            db.execSQL("INSERT INTO nafl_item VALUES ('dhikr','My renamed dhikr','WORSHIP',0,0,0,9)")
            db.execSQL("INSERT INTO nafl_item VALUES ('custom-old','Custom worship','WORSHIP',0,0,1,15)")
            db.execSQL("INSERT INTO nafl_log VALUES ('2026-11-01','dhikr',345,0,0)")
            db.execSQL("INSERT INTO nafl_log VALUES ('2026-11-01','quran',1,9,22)")
            db.execSQL("INSERT INTO quran_reminder VALUES (6,1205,1,1)")
            db.version = 3
        }
        val db = Room.databaseBuilder(ctx,TrackerDatabase::class.java,name).addMigrations(*TrackerDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val old = db.nafl().item("dhikr")!!
            assertEquals("My renamed dhikr",old.name); assertFalse(old.enabled); assertTrue(old.isCounter); assertEquals(0,old.dailyTarget)
            assertEquals("CHECKBOX",db.nafl().item("custom-old")!!.type)
            assertEquals(345,db.nafl().log("2026-11-01","dhikr")!!.count)
            assertEquals(9,db.nafl().log("2026-11-01","quran")!!.pages)
            assertEquals(22,db.nafl().log("2026-11-01","quran")!!.minutes)
            assertEquals(QuranReminder(6,1205,true,true),db.nafl().reminders().single())
            assertEquals(1,db.dao().all().size); assertEquals(500,db.qada().observeDebt().first().single().owed)
            assertEquals(3,db.qada().entries().single().count); assertTrue(db.qada().config()!!.witr)
            val repo = NaflRepository(db.nafl()); repo.ensureDefaults()
            assertFalse(db.nafl().item("dhikr")!!.enabled); assertEquals("My renamed dhikr",db.nafl().item("dhikr")!!.name)
            assertEquals(100,db.nafl().item("istighfar")!!.dailyTarget)
        } finally { db.close(); ctx.deleteDatabase(name) }
    }
    @Test fun concurrentAppAndWidgetRepositoriesDoNotLoseTaps() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx,TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val app = NaflRepository(db.nafl()); val widget = NaflRepository(db.nafl()); app.ensureDefaults()
            coroutineScope { repeat(150) { i -> launch(Dispatchers.Default) { (if (i%2 == 0) app else widget).adjustCounter("istighfar",1) } } }
            assertEquals(150,db.nafl().logs().single().count)
            assertEquals(1,db.nafl().logs().size)
        } finally { db.close() }
    }
    @Test fun counterBoundsTargetsDatesAndInvalidSelections() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx,TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = NaflRepository(db.nafl()); repo.ensureDefaults()
            assertEquals(0,repo.adjustCounter("salawat",-1).after)
            assertFalse(repo.adjustCounter("salawat",99).targetReached)
            assertTrue(repo.adjustCounter("salawat",1).targetReached)
            assertFalse(repo.adjustCounter("salawat",1).targetReached)
            assertEquals(1_000_000,repo.adjustCounter("salawat",1_000_000).after)
            assertEquals(1_000_000,repo.adjustCounter("salawat",1).after)
            assertEquals(1,repo.adjustCounter("salawat",1,LocalDate.now().minusDays(1)).after)
            for (id in listOf("quran","unknown")) {
                try { repo.adjustCounter(id,1); fail("Invalid counter accepted") } catch (_: IllegalArgumentException) {}
            }
            repo.saveItem(db.nafl().item("salawat")!!.copy(enabled = false))
            try { repo.adjustCounter("salawat",1); fail("Hidden counter accepted") } catch (_: IllegalArgumentException) {}
            try { repo.adjustCounter("istighfar",1,LocalDate.now().plusDays(1)); fail("Future day accepted") } catch (_: IllegalArgumentException) {}
        } finally { db.close() }
    }
    @Test fun customTypeAndTargetRenameKeepsStableHistory() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "phase16-persist.db"; ctx.deleteDatabase(name)
        fun open() = Room.databaseBuilder(ctx,TrackerDatabase::class.java,name).allowMainThreadQueries().build()
        val db = open()
        var id = ""
        try {
            val repo = NaflRepository(db.nafl()); repo.ensureDefaults(); repo.addItem("My durud","COUNTER",33)
            val item = db.nafl().items().first { it.name == "My durud" }; id = item.id
            repo.adjustCounter(id,20)
            repo.saveItem(item.copy(name = "Renamed durud",dailyTarget = 100))
            assertEquals(20,db.nafl().log(LocalDate.now().toString(),id)!!.count)
        } finally { db.close() }
        val reopened = open()
        try {
            assertEquals("Renamed durud",reopened.nafl().item(id)!!.name)
            assertEquals(100,reopened.nafl().item(id)!!.dailyTarget)
            assertEquals(20,reopened.nafl().logs().single().count)
        } finally { reopened.close(); ctx.deleteDatabase(name) }
    }
    @Test fun iqamahPreferencesRoundTripAndDefaultsRemainOff() = runBlocking {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext())
        repo.update { it.copy(iqamaFixed = mapOf(Prayer.FAJR to 355),iqamaNotifications = setOf(Prayer.FAJR),iqamaBefore = mapOf(Prayer.FAJR to 5),jumuahEnabled = true,jumuahNotify = true,jumuahMinute = 810,jumuahBefore = 10) }
        try {
            val s = repo.current()
            assertEquals(355,s.iqamaFixed[Prayer.FAJR]); assertEquals(setOf(Prayer.FAJR),s.iqamaNotifications)
            assertEquals(5,s.iqamaBefore[Prayer.FAJR]); assertTrue(s.jumuahEnabled); assertTrue(s.jumuahNotify)
            assertEquals(810,s.jumuahMinute); assertEquals(10,s.jumuahBefore)
            repo.update { it.copy(iqamaFixed = emptyMap(),iqamaNotifications = emptySet(),jumuahEnabled = false,jumuahNotify = false) }
            assertTrue(repo.current().iqamaFixed.isEmpty()); assertTrue(repo.current().iqamaNotifications.isEmpty())
        } finally { repo.update { com.kamalahmed.prayerapp.data.settings.AppSettings() } }
    }
    @Test fun iqamahAlarmIsScheduledIndependentlyAndCancelledWhenDisabled() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsRepository(ctx)
        val scheduler = com.kamalahmed.prayerapp.alarm.AlarmScheduler(ctx,settings)
        val am = ctx.getSystemService(android.app.AlarmManager::class.java)
        val shadow = org.robolectric.Shadows.shadowOf(am)
        settings.update { it.copy(notifyModes = Prayer.entries.associateWith { com.kamalahmed.prayerapp.core.model.NotifyMode.OFF },iqamaNotifications = setOf(Prayer.ASR),iqamaBefore = mapOf(Prayer.ASR to 5)) }
        try {
            scheduler.rescheduleAll()
            val notices = shadow.scheduledAlarms.filter { org.robolectric.Shadows.shadowOf(it.operation).savedIntent.action?.endsWith("IQAMA_NOTICE") == true }
            assertEquals(1,notices.size)
            val intent = org.robolectric.Shadows.shadowOf(notices.single().operation).savedIntent
            assertEquals("ASR",intent.getStringExtra(com.kamalahmed.prayerapp.alarm.AlarmScheduler.EXTRA_PRAYER))
            assertTrue(intent.getLongExtra(com.kamalahmed.prayerapp.alarm.AlarmScheduler.EXTRA_AT,0) > System.currentTimeMillis())
            settings.update { it.copy(iqamaNotifications = emptySet()) }
            scheduler.rescheduleAll()
            assertFalse(shadow.scheduledAlarms.any { org.robolectric.Shadows.shadowOf(it.operation).savedIntent.action?.endsWith("IQAMA_NOTICE") == true })
        } finally { settings.update { com.kamalahmed.prayerapp.data.settings.AppSettings() } }
    }
}
