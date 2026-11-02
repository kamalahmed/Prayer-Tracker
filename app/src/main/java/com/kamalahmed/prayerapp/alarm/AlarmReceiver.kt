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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val s = settings.current()
                Channels.create(context)
                // Ignore alarms delivered very late (device was off / time jumped): don't blast the adhan at a wrong time.
                val lateMs = System.currentTimeMillis() - at
                val stale = lateMs > STALE_MS
                val zone = runCatching { ZoneId.of(s.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
                val timeText = TimeFormat.time(Instant.ofEpochMilli(prayerTime).atZone(zone), s.use24h)
                if (!stale) when (type) {
                    AlarmType.PRAYER, AlarmType.SNOOZE -> onPrayer(context, s.mode(prayer), prayer, prayerTime, timeText, s.iqamaEnabled, s.iqama(prayer), type == AlarmType.SNOOZE)
                    AlarmType.REMINDER -> context.notifySafely(
                        NotificationIds.reminder(prayer.ordinal),
                        baseNotification(context, Channels.REMINDER, "${prayer.displayName} in ${s.preReminderMinutes} min", "${prayer.displayName} at $timeText")
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setTimeoutAfter(s.preReminderMinutes * 60_000L),
                    )
                    AlarmType.IQAMA -> context.notifySafely(
                        NotificationIds.iqama(prayer.ordinal),
                        baseNotification(context, Channels.BEEP, "Iqama – ${prayer.displayName}", "Iqama time now")
                            .setTimeoutAfter(10 * 60_000L),
                    )
                }
                if (type != AlarmType.SNOOZE) scheduler.rescheduleAll()
                hooks.onAlarm(type, prayer)
            } finally {
                pending.finish()
            }
        }
    }

    private fun onPrayer(
        ctx: Context, mode: NotifyMode, prayer: Prayer, prayerTime: Long, timeText: String,
        iqamaOn: Boolean, iqamaMin: Int, snoozed: Boolean,
    ) {
        val id = NotificationIds.prayer(prayer.ordinal)
        when (if (snoozed) NotifyMode.ADHAN else mode) {
            NotifyMode.OFF -> return
            NotifyMode.SILENT -> ctx.notifySafely(id, prayerNotification(ctx, Channels.SILENT, prayer, prayerTime, timeText))
            NotifyMode.BEEP -> ctx.notifySafely(id, prayerNotification(ctx, Channels.BEEP, prayer, prayerTime, timeText))
            NotifyMode.ADHAN -> ContextCompat.startForegroundService(ctx, AdhanService.playIntent(ctx, prayer, prayerTime))
        }
        if (iqamaOn && prayer.isPrayer && iqamaMin > 0 && !snoozed) {
            val end = prayerTime + iqamaMin * 60_000L
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

    companion object {
        const val STALE_MS = 30 * 60_000L
    }
}

/** Extension point for other features (tracker, widgets) to react to alarms. */
interface AlarmHooks {
    suspend fun onAlarm(type: AlarmType, prayer: Prayer)
    suspend fun onSystemChange()
}
