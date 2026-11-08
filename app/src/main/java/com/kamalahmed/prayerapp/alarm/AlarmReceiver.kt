package com.kamalahmed.prayerapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import com.kamalahmed.prayerapp.core.safety.CrashLog
import com.kamalahmed.prayerapp.core.safety.guard
import com.kamalahmed.prayerapp.core.safety.launchAsync
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Receives exact alarms for prayers, reminders, iqama and snooze. */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var hooks: AlarmHooks

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.removePrefix(AlarmScheduler.ACTION_PREFIX) ?: return
        val type = AlarmType.entries.firstOrNull { it.name == action } ?: return
        val prayer = Prayer.entries.firstOrNull { it.name == intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER) } ?: return
        val prayerTime = intent.getLongExtra(AlarmScheduler.EXTRA_PRAYER_TIME, System.currentTimeMillis())
        val at = intent.getLongExtra(AlarmScheduler.EXTRA_AT, System.currentTimeMillis())
        launchAsync("AlarmReceiver") {
            try {
                val s = settings.current()
                Channels.create(context)
                // Ignore alarms delivered very late (device was off / time jumped): don't blast the adhan at a wrong time.
                val lateMs = System.currentTimeMillis() - at
                val stale = lateMs > STALE_MS
                val zone = runCatching { ZoneId.of(s.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
                val timeText = TimeFormat.time(Instant.ofEpochMilli(prayerTime).atZone(zone), s.use24h)
                if (!stale) when (type) {
                    AlarmType.PRAYER, AlarmType.SNOOZE -> onPrayer(context, s.mode(prayer), prayer, prayerTime, timeText, s.iqamaEnabled, Iqamah.time(s,prayer,Instant.ofEpochMilli(prayerTime).atZone(zone))?.toInstant()?.toEpochMilli(), type == AlarmType.SNOOZE)
                    AlarmType.REMINDER -> context.notifySafely(
                        NotificationIds.reminder(prayer.ordinal),
                        baseNotification(context, Channels.REMINDER, "${prayer.displayName} in ${s.preReminderMinutes} min", "${prayer.displayName} at $timeText")
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setTimeoutAfter(s.preReminderMinutes * 60_000L),
                    )
                    AlarmType.IQAMA -> Unit // legacy boundary refresh; audible notices are separately opt-in
                    AlarmType.IQAMA_NOTICE -> {
                        val adhan = Instant.ofEpochMilli(prayerTime).atZone(zone)
                        val iq = Iqamah.time(s,prayer,adhan)
                        if (iq != null && Iqamah.enabled(s,prayer,adhan)) {
                            val name = Iqamah.label(s,prayer,adhan)
                            val lead = Iqamah.lead(s,prayer,adhan)
                            context.notifySafely(1500 + prayer.ordinal,
                                baseNotification(context, Channels.BEEP, if (lead == 0) "Iqamah – $name" else "Iqamah – $name in $lead min",
                                    "Iqamah at ${TimeFormat.time(iq, false)}").setCategory(NotificationCompat.CATEGORY_REMINDER).setTimeoutAfter(10 * 60_000L))
                        }
                    }
                }
            } finally {
                // Always re-arm the next alarms, even if showing this one failed; otherwise one bad alarm
                // (e.g. a foreground-service start refused by the OS) would silence every later prayer.
                if (type != AlarmType.SNOOZE) guard("AlarmReceiver", "rescheduleAll") { scheduler.rescheduleAll() }
                guard("AlarmReceiver", "hooks.onAlarm") { hooks.onAlarm(type, prayer) }
            }
        }
    }

    private fun onPrayer(
        ctx: Context, mode: NotifyMode, prayer: Prayer, prayerTime: Long, timeText: String,
        iqamaOn: Boolean, iqamaEnd: Long?, snoozed: Boolean,
    ) {
        val id = NotificationIds.prayer(prayer.ordinal)
        when (if (snoozed) NotifyMode.ADHAN else mode) {
            NotifyMode.OFF -> return
            NotifyMode.SILENT -> ctx.notifySafely(id, prayerNotification(ctx, Channels.SILENT, prayer, prayerTime, timeText))
            NotifyMode.BEEP -> ctx.notifySafely(id, prayerNotification(ctx, Channels.BEEP, prayer, prayerTime, timeText))
            NotifyMode.ADHAN -> try {
                ContextCompat.startForegroundService(ctx, AdhanService.playIntent(ctx, prayer, prayerTime))
            } catch (e: Exception) {
                // Android 12+ can refuse a foreground-service start (ForegroundServiceStartNotAllowedException);
                // still alert the user with a sounding notification instead of failing silently.
                CrashLog.nonFatal("AlarmReceiver", "Adhan service start refused, falling back to notification", e)
                ctx.notifySafely(id, prayerNotification(ctx, Channels.BEEP, prayer, prayerTime, timeText))
            }
        }
        if (iqamaOn && prayer.isPrayer && iqamaEnd != null && iqamaEnd > System.currentTimeMillis() && !snoozed) {
            val end = iqamaEnd
            val iqamaMin = ((end - prayerTime) / 60_000L).coerceAtLeast(0)
            ctx.notifySafely(
                NotificationIds.iqama(prayer.ordinal),
                baseNotification(ctx, Channels.IQAMA, "Iqama countdown – ${prayer.displayName}", "Iqama in $iqamaMin min")
                    .setWhen(end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                    .setOngoing(false).setTimeoutAfter((end - System.currentTimeMillis()).coerceAtLeast(1000)),
            )
        }
    }

    private fun prayerNotification(ctx: Context, channel: String, prayer: Prayer, prayerTime: Long, timeText: String) =
        baseNotification(ctx, channel, "It's time for ${prayer.displayName}", timeText)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(prayerTime).setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                if (prayer.isPrayer) addAction(0, "Prayed ✓",
                    com.kamalahmed.prayerapp.tracker.MarkPrayedReceiver.pendingIntent(ctx, prayer, prayerTime, NotificationIds.prayer(prayer.ordinal)))
            }

    companion object {
        const val STALE_MS = 30 * 60_000L
    }
}

/** Extension point for other features (tracker, widgets) to react to alarms. */
interface AlarmHooks {
    suspend fun onAlarm(type: AlarmType, prayer: Prayer)
    suspend fun onSystemChange()
}
