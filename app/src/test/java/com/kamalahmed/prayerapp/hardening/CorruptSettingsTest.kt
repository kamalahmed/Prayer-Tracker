package com.kamalahmed.prayerapp.hardening

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A corrupt settings file used to throw from every settings collector (app start, alarms, widgets). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CorruptSettingsTest {
    @Test fun corruptDataStoreFallsBackToDefaults() = runBlocking {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        File(ctx.filesDir, "datastore").mkdirs()
        File(ctx.filesDir, "datastore/settings.preferences_pb").writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x04))
        assertEquals(AppSettings().calcMethod, SettingsRepository(ctx).current().calcMethod)
    }
}
