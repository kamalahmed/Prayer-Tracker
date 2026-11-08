package com.kamalahmed.prayerapp.nafl

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** canScheduleExactAlarms is API 31+, while the app supports API 26+. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class QuranReminderLegacyTest {
    @Test fun androidNineCanScheduleQuranReminder() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.nafl().saveReminder(QuranReminder(minute = 540, enabled = true))
            val scheduler = QuranReminders(ctx, db.nafl())
            scheduler.reschedule()
            val operation = scheduler.pending(db.nafl().reminders().single().id)
            // The real Application also schedules prayer/widget alarms on legacy Android.
            assertEquals(1, shadowOf(ctx.getSystemService(AlarmManager::class.java)).scheduledAlarms.count { it.operation == operation })
        } finally { db.close() }
    }
}
