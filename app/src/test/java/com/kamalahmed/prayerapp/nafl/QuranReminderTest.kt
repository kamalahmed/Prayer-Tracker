package com.kamalahmed.prayerapp.nafl

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class QuranReminderTest {
    @Test fun remindersDefaultOffAndEnabledTimesHaveDistinctPendingIntents() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = NaflRepository(db.nafl())
            val scheduler = QuranReminders(ctx, db.nafl())
            repo.saveReminder(QuranReminder(minute = 540))
            scheduler.reschedule()
            val am = shadowOf(ctx.getSystemService(AlarmManager::class.java))
            assertTrue(am.scheduledAlarms.isEmpty())
            db.nafl().saveReminder(db.nafl().reminders().single().copy(enabled = true))
            repo.saveReminder(QuranReminder(minute = 1000, enabled = true, alarmSound = true))
            scheduler.reschedule()
            assertEquals(2, am.scheduledAlarms.size)
            val all = db.nafl().reminders()
            assertNotEquals(scheduler.pending(all[0].id), scheduler.pending(all[1].id))
            scheduler.reschedule()
            assertEquals(2, am.scheduledAlarms.size)
            db.nafl().saveReminder(all[0].copy(enabled = false))
            scheduler.reschedule()
            assertEquals(1, am.scheduledAlarms.size)
            scheduler.remove(all[1].id)
            assertTrue(am.scheduledAlarms.isEmpty())
            assertEquals(1, db.nafl().reminders().size)
        } finally { db.close() }
    }
    @Test fun notificationModeAndAlarmModeUseSeparateSoundChannels() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).build()
        try {
            QuranReminders(ctx, db.nafl()).createChannels()
            val nm = ctx.getSystemService(NotificationManager::class.java)
            assertEquals(android.media.AudioAttributes.USAGE_ALARM, nm.getNotificationChannel(QuranReminders.SOUND).audioAttributes.usage)
            assertNotEquals(nm.getNotificationChannel(QuranReminders.NORMAL).sound, nm.getNotificationChannel(QuranReminders.SOUND).sound)
        } finally { db.close() }
    }
    @Test fun noExactPermissionFallsBackToIdleAllowedAndDeliveryRearms() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = NaflRepository(db.nafl())
            repo.saveReminder(QuranReminder(minute = 100, enabled = true))
            org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
            val scheduler = QuranReminders(ctx, db.nafl())
            scheduler.reschedule()
            val am = shadowOf(ctx.getSystemService(AlarmManager::class.java))
            assertEquals(1, am.scheduledAlarms.size)
            assertTrue(am.scheduledAlarms.single().allowWhileIdle)
            assertNotEquals(org.robolectric.shadows.ShadowAlarmManager.WINDOW_EXACT, am.scheduledAlarms.single().windowLengthMs)
            val id = db.nafl().reminders().single().id
            scheduler.deliver(id)
            assertEquals(1, am.scheduledAlarms.size)
            assertTrue(am.scheduledAlarms.single().triggerAtTime > System.currentTimeMillis())
            db.nafl().saveReminder(db.nafl().reminders().single().copy(enabled = false))
            scheduler.reschedule()
            assertTrue(am.scheduledAlarms.isEmpty())
        } finally { db.close() }
    }
    @Test fun duplicateReminderTimesAreRejected() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = NaflRepository(db.nafl())
            repo.saveReminder(QuranReminder(minute = 1200))
            try { repo.saveReminder(QuranReminder(minute = 1200)); fail("duplicate accepted") } catch (_: IllegalArgumentException) {}
            try { repo.saveReminder(QuranReminder(minute = -1)); fail("invalid time accepted") } catch (_: IllegalArgumentException) {}
            assertEquals(1, db.nafl().reminders().size)
        } finally { db.close() }
    }
    @Test fun localTimeSurvivesDaylightSavingGap() {
        val now = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, ZoneId.of("Europe/London"))
        val next = QuranReminderTime.next(90, now)
        assertEquals(29, next.dayOfMonth)
        assertTrue(next > now)
        val following = QuranReminderTime.next(90, next)
        assertEquals(1, following.hour)
        assertEquals(30, following.minute)
    }
}
