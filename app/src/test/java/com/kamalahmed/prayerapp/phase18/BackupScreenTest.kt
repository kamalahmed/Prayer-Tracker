package com.kamalahmed.prayerapp.phase18

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kamalahmed.prayerapp.backup.*
import com.kamalahmed.prayerapp.alarm.AlarmScheduler
import com.kamalahmed.prayerapp.nafl.QuranReminders
import com.kamalahmed.prayerapp.tracker.*
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.update.UpdatePrefs
import com.kamalahmed.prayerapp.widget.WidgetStyleStore
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=Application::class,qualifiers="w411dp-h891dp")
class BackupScreenTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var db: TrackerDatabase
    private lateinit var vm: BackupViewModel
    private lateinit var repo: BackupRepository
    private val pauseRestore=java.util.concurrent.atomic.AtomicBoolean(false)
    private val restoreEntered=java.util.concurrent.CountDownLatch(1)
    private val releaseRestore=java.util.concurrent.CountDownLatch(1)
    @Before fun setup() {
        val ctx=ApplicationProvider.getApplicationContext<Application>()
        db=Room.inMemoryDatabaseBuilder(ctx,TrackerDatabase::class.java).allowMainThreadQueries()
            .setQueryCallback({ sql,_ ->
                if(sql.startsWith("DELETE FROM") && pauseRestore.compareAndSet(true,false)) {
                    restoreEntered.countDown()
                    check(releaseRestore.await(10,java.util.concurrent.TimeUnit.SECONDS))
                }
            },java.util.concurrent.Executor { it.run() }).build()
        val settings=SettingsRepository(ctx)
        repo=BackupRepository(ctx,db,settings,WidgetStyleStore(ctx),UpdatePrefs(ctx))
        vm=BackupViewModel(ctx,repo,db,AlarmScheduler(ctx,settings),QuranReminders(ctx,db.nafl()))
        rule.setContent { PrayerTheme { BackupScreen(vm,{}) } }
        rule.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.state.value.busy }
    }
    @After fun close() { releaseRestore.countDown(); db.close() }
    @Test fun acceptedRestoreFinishesReminderRefreshEvenIfTheScreenIsClosed() {
        val file=runBlocking {
            db.dao().upsert(PrayerLog("2026-11-08","FAJR",PrayerStatus.ON_TIME,123))
            repo.dailySnapshot()
        }
        rule.runOnIdle { vm.openSnapshot(file) }
        rule.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.state.value.busy }
        pauseRestore.set(true)
        rule.runOnIdle { vm.restore(RestoreMode.REPLACE) }
        rule.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); restoreEntered.count==0L }
        rule.runOnIdle { vm.viewModelScope.cancel() }
        releaseRestore.countDown()
        rule.waitUntil(15_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.state.value.busy }
        assertNull("Accepted restore must finish its post-commit work after navigation",vm.state.value.preview)
        assertTrue(vm.state.value.message.orEmpty(),vm.state.value.message.orEmpty().startsWith("Data restored"))
        assertEquals(1,runBlocking { db.dao().all().size })
    }
    @Test fun settingsExposeExportImportAutomaticFolderAndSnapshotControls() {
        rule.onNodeWithText("Backup & restore").assertExists()
        rule.onNodeWithText("Export backup").assertExists()
        rule.onNodeWithText("Import backup").assertExists()
        rule.onNodeWithText("Choose automatic export folder").performScrollTo().assertExists()
        rule.onNodeWithText("Take snapshot now").performScrollTo().assertExists()
        rule.onNodeWithText("Restore from snapshot").performScrollTo().assertExists()
    }
    @Test fun snapshotPreviewRequiresExplicitMergeOrReplaceConfirmation() {
        val file=runBlocking {
            db.dao().upsert(PrayerLog("2026-11-08","FAJR",PrayerStatus.ON_TIME,123))
            repo.dailySnapshot()
        }
        rule.runOnIdle { vm.openSnapshot(file) }
        rule.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.state.value.busy }
        assertNotNull(vm.state.value.message,vm.state.value.preview)
        rule.onNodeWithText("Review backup").assertExists()
        rule.onNodeWithText("Merge").assertExists()
        rule.onNodeWithText("Replace").performScrollTo().performClick()
        rule.onNode(hasText("Replace removes current data",substring=true)).assertExists()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(1,runBlocking { db.dao().all().size })
        assertNull(vm.state.value.preview)
    }
}
