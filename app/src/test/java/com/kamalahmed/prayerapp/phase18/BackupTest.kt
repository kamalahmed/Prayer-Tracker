package com.kamalahmed.prayerapp.phase18

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.backup.*
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.qada.*
import com.kamalahmed.prayerapp.tracker.*
import com.kamalahmed.prayerapp.widget.*
import com.kamalahmed.prayerapp.update.UpdatePrefs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], application = Application::class)
class BackupTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TrackerDatabase
    private lateinit var repo: BackupRepository
    private val settings = SettingsRepository(ctx)
    private val styles = WidgetStyleStore(ctx)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build(); repo = BackupRepository(ctx,db,settings,styles,UpdatePrefs(ctx)) }
    @After fun close() { db.close() }
    private suspend fun seed() {
        db.dao().upsert(PrayerLog("2026-11-08","FAJR",PrayerStatus.ON_TIME,123))
        db.qada().upsertDebt(listOf(QadaDebt("FAJR",50)))
        db.qada().insert(QadaEntry(prayer="FAJR", count=2, date="2026-11-08", at=456))
        db.qada().upsertConfig(QadaConfig(dailyGoal=4, setupDone=true))
        db.nafl().saveItem(NaflItem("custom_a","My dhikr",type="COUNTER", dailyTarget=100))
        db.nafl().saveLog(NaflLog("2026-11-08","custom_a",77,2,3))
        db.nafl().saveReminder(QuranReminder(minute=600,enabled=true))
        settings.update { com.kamalahmed.prayerapp.data.settings.AppSettings(cityName="Dubai", iqamaEnabled=true) }
        styles.save(99,WidgetStyle(contentScale=1.2f,outerMargin=8))
    }
    @Test fun roundTripPreservesAllTablesSettingsAndCustomCounts() = runBlocking {
        seed(); val text=repo.exportJson(); val preview=repo.preview(text)
        assertTrue(preview.contains("prayer_log: 1")); assertTrue(preview.contains("2026-11-08"))
        db.dao().clear(); db.nafl().saveLog(NaflLog("2026-11-08","custom_a",1))
        settings.update { it.copy(cityName="London") }; styles.delete(intArrayOf(99))
        repo.restore(text, RestoreMode.REPLACE)
        assertEquals(PrayerStatus.ON_TIME,db.dao().all().single().status)
        assertEquals(77,db.nafl().logs().single().count)
        assertEquals(100,db.nafl().items().first { it.id=="custom_a" }.dailyTarget)
        assertEquals(2,db.qada().entries().single().count)
        assertEquals(4,db.qada().config()!!.dailyGoal)
        assertEquals("Dubai",settings.current().cityName)
        assertEquals(8,styles.get(99,WidgetKind.TASBIH).outerMargin)
        assertTrue(repo.snapshots().any { it.name.startsWith("before-import-") })
    }
    @Test fun corruptOrFutureFilesNeverChangeExistingData() = runBlocking {
        seed(); val before=repo.exportJson()
        for (bad in listOf("{bad",JSONObject(before).put("formatVersion",999).toString(), JSONObject(before).apply { getJSONObject("tables").getJSONArray("nafl_log").getJSONObject(0).put("count",-1) }.toString())) {
            assertTrue(runCatching { repo.restore(bad,RestoreMode.REPLACE) }.isFailure)
            assertEquals(77,db.nafl().logs().single().count); assertEquals(1,db.dao().all().size)
        }
    }
    @Test fun olderDatabaseSnapshotImportsWithMissingCounterColumnsDefaulted() = runBlocking {
        seed(); val old=JSONObject(repo.exportJson()).put("databaseVersion",3)
        val item=old.getJSONObject("tables").getJSONArray("nafl_item").getJSONObject(0)
        item.remove("type"); item.remove("dailyTarget")
        repo.restore(old.toString(),RestoreMode.REPLACE)
        assertEquals(77,db.nafl().logs().single().count)
        assertEquals("CHECKBOX",db.nafl().items().first { it.id=="custom_a" }.type)
    }
    @Test fun mergeIsIdempotentAndDoesNotOverwriteNewerOrLargerLogs() = runBlocking {
        seed(); val backup=repo.exportJson()
        db.nafl().saveLog(NaflLog("2026-11-08","custom_a",90))
        db.dao().upsert(PrayerLog("2026-11-08","FAJR",PrayerStatus.LATE,999))
        repeat(2) { repo.restore(backup,RestoreMode.MERGE) }
        assertEquals(90,db.nafl().logs().single().count)
        assertEquals(PrayerStatus.LATE,db.dao().all().single().status)
        assertEquals(1,db.qada().entries().size)
    }
    @Test fun rollingDailySnapshotsKeepSevenAndAreRestorable() = runBlocking {
        seed(); repeat(10) { repo.dailySnapshot(java.time.LocalDate.of(2026,10,1).plusDays(it.toLong())) }
        val files=repo.snapshots().filter { it.name.startsWith("daily-") }
        assertEquals(7,files.size)
        assertTrue(files.none { it.name.contains("20261001") })
        repo.restore(files.first().readText(),RestoreMode.REPLACE)
        assertEquals(77,db.nafl().logs().single().count)
    }
    @Test fun midTransactionFailureRollsBackDeletesAndEarlierInserts() = runBlocking {
        seed(); val text=repo.exportJson()
        db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER reject_backup BEFORE INSERT ON nafl_log BEGIN SELECT RAISE(ABORT,'injected restore failure'); END")
        assertTrue(runCatching { repo.restore(text,RestoreMode.REPLACE) }.isFailure)
        assertEquals(77,db.nafl().logs().single().count)
        assertEquals(PrayerStatus.ON_TIME,db.dao().all().single().status)
        assertTrue(repo.snapshots().any { it.name.startsWith("before-import-") })
    }
    @Test fun settingsTypeConfusionIsRejectedBeforeAnyWrite() = runBlocking {
        seed(); val json=JSONObject(repo.exportJson())
        json.getJSONObject("preferences").getJSONObject("settings").put("volume_pct",JSONObject().put("type","string").put("value","bad"))
        assertTrue(runCatching { repo.restore(json.toString(),RestoreMode.REPLACE) }.isFailure)
        assertEquals(77,db.nafl().logs().single().count)
    }
    @Test fun customSoundBytesRoundTripAndAreRebasedToPrivateStorage() = runBlocking {
        seed()
        val file=java.io.File(ctx.filesDir,"sounds/custom.mp3").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1,2,3,4)) }
        settings.update { it.copy(soundSource=com.kamalahmed.prayerapp.core.model.SoundSource.CUSTOM_FILE,soundUri=file.absolutePath) }
        val text=repo.exportJson(); file.writeBytes(byteArrayOf(9))
        repo.restore(text,RestoreMode.REPLACE)
        assertArrayEquals(byteArrayOf(1,2,3,4),file.readBytes())
        assertEquals(file.absolutePath,settings.current().soundUri)
    }

    @Test fun malformedTableOrPreferenceContainerCannotBeTreatedAsEmptyData() = runBlocking {
        seed(); val backup=repo.exportJson()
        val malformed=listOf(
            JSONObject(backup).apply { getJSONObject("tables").put("nafl_log","broken") },
            JSONObject(backup).apply { getJSONObject("preferences").put("settings","broken") },
        )
        malformed.forEach { bad ->
            assertTrue(runCatching { repo.restore(bad.toString(),RestoreMode.REPLACE) }.isFailure)
            assertEquals(77,db.nafl().logs().single().count)
        }
    }

    @Test fun allPrayerEnumLogsArePreservedIncludingSunrise() = runBlocking {
        seed(); db.dao().upsert(PrayerLog("2026-11-08","SUNRISE",PrayerStatus.ON_TIME,124))
        val text=repo.exportJson(); repo.restore(text,RestoreMode.REPLACE)
        assertEquals(setOf("FAJR","SUNRISE"),db.dao().all().map { it.prayer }.toSet())
    }
    @Test fun safetySnapshotFailureAbortsBeforeDeletingAnyData() = runBlocking {
        seed(); val text=repo.exportJson()
        val folder=java.io.File(ctx.filesDir,"backups"); val saved=java.io.File(ctx.cacheDir,"saved-snapshot-test")
        if(folder.exists()) check(folder.renameTo(saved))
        folder.writeText("injected unavailable snapshot directory")
        try {
            assertTrue(runCatching { repo.restore(text,RestoreMode.REPLACE) }.isFailure)
            assertEquals(77,db.nafl().logs().single().count)
            assertEquals(1,db.dao().all().size)
        } finally { folder.delete(); if(saved.exists()) check(saved.renameTo(folder)) }
    }

}
