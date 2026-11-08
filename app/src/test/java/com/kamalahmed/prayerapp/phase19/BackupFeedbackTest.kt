package com.kamalahmed.prayerapp.phase19

import android.app.Application
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kamalahmed.prayerapp.alarm.AlarmScheduler
import com.kamalahmed.prayerapp.backup.*
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.nafl.QuranReminders
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import com.kamalahmed.prayerapp.update.UpdatePrefs
import com.kamalahmed.prayerapp.widget.WidgetStyleStore
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=Application::class,qualifiers="w411dp-h891dp")
class BackupFeedbackTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var db: TrackerDatabase
    private lateinit var vm: BackupViewModel
    @Before fun setup() {
        val ctx=ApplicationProvider.getApplicationContext<Application>()
        ctx.getSharedPreferences("backup_metadata",0).edit().clear().commit()
        db=Room.inMemoryDatabaseBuilder(ctx,TrackerDatabase::class.java).allowMainThreadQueries().build()
        val s=SettingsRepository(ctx)
        val repo=BackupRepository(ctx,db,s,WidgetStyleStore(ctx),UpdatePrefs(ctx))
        vm=BackupViewModel(ctx,repo,db,AlarmScheduler(ctx,s),QuranReminders(ctx,db.nafl()))
        rule.setContent { PrayerTheme { BackupScreen(vm,{}) } };waitIdle()
    }
    @After fun close() { db.close() }
    private fun waitIdle() { rule.waitUntil(10_000) { Shadows.shadowOf(Looper.getMainLooper()).idle();!vm.state.value.busy } }
    @Test fun takingSnapshotKeepsAPersistentLocationReceiptAtTheAction() {
        rule.onNodeWithText("Take snapshot now").performScrollTo().performClick();waitIdle()
        assertTrue(vm.state.value.message.orEmpty().contains("Saved in 1 location"));assertTrue(vm.state.value.status.localAt>0)
        rule.onAllNodes(hasText("Saved in 1 location",substring=true)).onFirst().assertExists()
        rule.onNodeWithText("Take snapshot now").assertExists()
    }
    @Test fun androidStatusNeverPretendsALocalCopyWasACloudUpload() {
        rule.onNodeWithText("Android Auto Backup · Android managed").performScrollTo().assertExists()
        rule.onNodeWithText("Not reported to this app").assertExists()
        rule.onNodeWithText("Open device backup settings").assertExists()
    }
    @Test fun progressAndFailureStayVisibleNearTheSnapshotButton() {
        rule.runOnIdle { vm.state.value=vm.state.value.copy(busy=true,operation="Saving and verifying snapshot…") }
        rule.onAllNodesWithText("Saving and verifying snapshot…").onFirst().performScrollTo().assertExists()
        rule.runOnIdle { vm.state.value=vm.state.value.copy(busy=false,operation=null,error=true,message="Saved in 1 location · folder unavailable") }
        rule.onAllNodesWithText("Saved in 1 location · folder unavailable").onFirst().assertExists()
    }
}
