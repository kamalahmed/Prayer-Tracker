package com.kamalahmed.prayerapp.phase20

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.alarm.*
import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.SoundSource
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class IqamahAlarmTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val settings = SettingsRepository(ctx)
    private val zone = java.time.ZoneId.of("Asia/Dubai")

    @After fun reset() = runBlocking { settings.update { AppSettings(onboardingDone = true) } }

    private fun json(vararg entries: Pair<String, Any>) = org.json.JSONObject().apply {
        entries.forEach { (k, v) -> put(k, org.json.JSONObject().put("type", when (v) { is Boolean -> "boolean"; is Int -> "int"; else -> "string" }).put("value", v)) }
    }

    @Test fun beta11IqamahSettingsMigrateToFullAlarmWithoutLosingValues() = runBlocking {
        // Exactly the keys a beta.8–beta.11 install wrote: no iq_quiet_* / iqamah_* keys yet.
        settings.backupRestore(json("iq_notify_ASR" to true, "iq_before_ASR" to 7, "iqama_ASR" to 15, "iq_fixed_DHUHR" to 810,
            "jumuah_notify" to true, "jumuah_before" to 12, "adj_DHUHR" to 25, "mode_FAJR" to "ADHAN"), merge = false)
        val s = settings.current()
        assertEquals(setOf(Prayer.ASR), s.iqamaNotifications)
        assertEquals(7, s.iqamaBefore[Prayer.ASR]); assertEquals(15, s.iqama(Prayer.ASR)); assertEquals(810, s.iqamaFixed[Prayer.DHUHR])
        assertTrue(s.jumuahNotify); assertEquals(12, s.jumuahBefore); assertEquals(25, s.adjustment(Prayer.DHUHR))
        assertTrue(s.iqamaQuiet.isEmpty()); assertFalse(s.jumuahQuiet)
        assertEquals(AppSettings.IQAMAH_SYSTEM_ALARM, s.iqamahSound)
        assertTrue(Iqamah.alarm(s, Prayer.ASR, ZonedDateTime.now(zone)))
    }

    @Test fun newSettingsRoundTripAndLargeOffsetsAreAcceptedInBackups() = runBlocking {
        settings.update { it.copy(adjustments = mapOf(Prayer.DHUHR to 114), startFixed = mapOf(Prayer.ISHA to 30), iqamaQuiet = setOf(Prayer.FAJR),
            jumuahQuiet = true, iqamahSound = "chime", iqamahMaxPlaySeconds = 90, iqamahLoop = false, iqamahVolume = 80, iqamahFullScreen = true) }
        val s = settings.current()
        assertEquals(114, s.adjustment(Prayer.DHUHR)); assertEquals(30, s.startFixed[Prayer.ISHA])
        assertEquals(setOf(Prayer.FAJR), s.iqamaQuiet); assertTrue(s.jumuahQuiet)
        assertEquals("chime", s.iqamahSound); assertEquals(90, s.iqamahMaxPlaySeconds); assertFalse(s.iqamahLoop)
        assertEquals(80, s.iqamahVolume); assertTrue(s.iqamahFullScreen)
        val exported = settings.backupExport()
        settings.validateBackup(exported)
        settings.update { AppSettings() }
        settings.backupRestore(exported, merge = false)
        assertEquals(114, settings.current().adjustment(Prayer.DHUHR)); assertEquals(30, settings.current().startFixed[Prayer.ISHA])
        // Out-of-range values in a file are rejected, not silently imported.
        assertThrows(IllegalArgumentException::class.java) { settings.validateBackup(json("adj_DHUHR" to 5000)) }
        assertThrows(IllegalArgumentException::class.java) { settings.validateBackup(json("start_fixed_DHUHR" to 2000)) }
        assertThrows(IllegalArgumentException::class.java) { settings.validateBackup(json("iqamah_volume" to 400)) }
        Unit
    }

    @Test fun fullIqamahAlarmUsesAlarmClockAndQuietStyleDoesNot() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = AlarmScheduler(ctx, settings)
        val am = ctx.getSystemService(AlarmManager::class.java)
        settings.update { it.copy(notifyModes = Prayer.entries.associateWith { NotifyMode.OFF }, iqamaNotifications = setOf(Prayer.ASR), iqamaBefore = mapOf(Prayer.ASR to 10)) }
        scheduler.rescheduleAll()
        val notice = shadowOf(am).scheduledAlarms.single { shadowOf(it.operation).savedIntent.action!!.endsWith("IQAMA_NOTICE") }
        assertNotNull("before-iqamah alarm must be an alarm clock", am.nextAlarmClock)
        assertEquals(notice.triggerAtTime, am.nextAlarmClock!!.triggerTime)
        settings.update { it.copy(iqamaQuiet = setOf(Prayer.ASR)) }
        scheduler.rescheduleAll()
        assertTrue(shadowOf(am).scheduledAlarms.any { shadowOf(it.operation).savedIntent.action!!.endsWith("IQAMA_NOTICE") })
        assertNull(am.nextAlarmClock)
    }

    @Test fun receiverStartsTheAlarmSoundServiceForTheBeforeIqamahAlarm() = runBlocking {
        settings.update { it.copy(iqamaNotifications = setOf(Prayer.ASR), iqamaBefore = mapOf(Prayer.ASR to 10)) }
        val adhan = ZonedDateTime.now(zone).minusMinutes(10)
        val s = settings.current()
        val iq = Iqamah.time(s, Prayer.ASR, adhan)!!
        val intent = Intent(ctx, AlarmReceiver::class.java).setAction(AlarmScheduler.ACTION_PREFIX + "IQAMA_NOTICE")
            .putExtra(AlarmScheduler.EXTRA_PRAYER, "ASR").putExtra(AlarmScheduler.EXTRA_PRAYER_TIME, adhan.toInstant().toEpochMilli())
            .putExtra(AlarmScheduler.EXTRA_AT, System.currentTimeMillis())
        AlarmReceiver().onReceive(ctx, intent)
        val app = shadowOf(ctx as android.app.Application)
        var started: Intent? = null
        val deadline = System.currentTimeMillis() + 10_000
        while (started == null && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            started = app.nextStartedService
            if (started == null) Thread.sleep(20)
        }
        assertNotNull("iqamah alarm must start the sound service", started)
        assertEquals(AdhanService.ACTION_IQAMAH, started!!.action)
        assertEquals("ASR", started.getStringExtra(AdhanService.EXTRA_PRAYER))
        assertEquals(iq.toInstant().toEpochMilli(), started.getLongExtra(AdhanService.EXTRA_IQAMAH, 0))
    }

    @Test fun alarmChannelForIqamahUsesAlarmStreamUnlikeBeepChannel() {
        Channels.create(ctx)
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        val loud = nm.getNotificationChannel(Channels.IQAMAH_ALARM)!!
        assertEquals(android.media.AudioAttributes.USAGE_ALARM, loud.audioAttributes.usage)
        assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, loud.importance)
        // The pre-beta.12 path: notification-event usage = notification volume (the quiet-sound root cause).
        assertEquals(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT, nm.getNotificationChannel(Channels.BEEP)!!.audioAttributes.usage)
    }

    @Test fun iqamahSoundResolvesIndependentlyAndFallsBackSafely() {
        val s = AppSettings(soundSource = SoundSource.BUNDLED, bundledSound = "adhan_full")
        assertNotEquals(Sounds.resolve(ctx, s, Prayer.ASR), Sounds.resolveIqamah(ctx, s.copy(iqamahSound = "chime")))
        assertEquals(Sounds.iqamahFallback(ctx), Sounds.resolveIqamah(ctx, s.copy(iqamahSound = AppSettings.IQAMAH_RINGTONE, iqamahSoundUri = null)))
        assertEquals(Sounds.iqamahFallback(ctx), Sounds.resolveIqamah(ctx, s.copy(iqamahSound = "unknown-id")))
        assertNotNull(Sounds.resolveIqamah(ctx, s))
    }
}
