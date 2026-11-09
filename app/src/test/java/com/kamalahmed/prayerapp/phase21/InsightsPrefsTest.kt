package com.kamalahmed.prayerapp.phase21

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.backup.BackupRepository
import com.kamalahmed.prayerapp.backup.RestoreMode
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.insights.InsightsPrefs
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import com.kamalahmed.prayerapp.update.UpdatePrefs
import com.kamalahmed.prayerapp.widget.WidgetStyleStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InsightsPrefsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun hideAndNotHelpfulPersistAndExpire() {
        val today = LocalDate.of(2026, 11, 9)
        InsightsPrefs(ctx).apply { setHidden(true); mute(listOf("ontime:FAJR", "quran"), today) }
        val again = InsightsPrefs(ctx).coach.value
        assertTrue(again.hidden)
        assertEquals(setOf("ontime:FAJR", "quran"), again.muted(today.plusDays(27)))
        assertTrue(again.muted(today.plusDays(InsightsPrefs.MUTE_DAYS + 1)).isEmpty())
        InsightsPrefs(ctx).setHidden(false)
        assertFalse(InsightsPrefs(ctx).coach.value.hidden)
    }

    @Test fun hideCoachIsBackedUpAndRestored() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = BackupRepository(ctx, db, SettingsRepository(ctx), WidgetStyleStore(ctx), UpdatePrefs(ctx))
            val prefs = InsightsPrefs(ctx)
            prefs.setHidden(true)
            val json = repo.exportJson()
            assertTrue(json.contains("coach_hidden"))
            prefs.setHidden(false)
            repo.restore(json, RestoreMode.REPLACE)
            assertTrue(InsightsPrefs(ctx).coach.value.hidden)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() // listeners are delivered on the main thread
            assertTrue("listener keeps the live copy in sync", prefs.coach.value.hidden)
            // A tampered value is rejected before anything is written.
            val bad = JSONObject(json).also { o -> o.findAndBreak() }.toString()
            assertTrue(runCatching { repo.preview(bad) }.isFailure || runCatching { repo.restore(bad, RestoreMode.REPLACE) }.isFailure)
        } finally { db.close() }
    }

    private fun JSONObject.findAndBreak() {
        keys().asSequence().toList().forEach { k ->
            val v = opt(k)
            if (k == "insights" && v is JSONObject) put(k, JSONObject().put("coach_hidden", "yes"))
            else if (v is JSONObject) v.findAndBreak()
        }
    }
}
