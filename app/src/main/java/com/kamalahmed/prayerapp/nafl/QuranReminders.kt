package com.kamalahmed.prayerapp.nafl

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.kamalahmed.prayerapp.alarm.baseNotification
import com.kamalahmed.prayerapp.alarm.notifySafely
import com.kamalahmed.prayerapp.core.safety.AppErrors
import com.kamalahmed.prayerapp.core.safety.guard
import com.kamalahmed.prayerapp.core.safety.launchAsync
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

object QuranReminderTime {
    fun next(minute: Int, now: ZonedDateTime): ZonedDateTime {
        require(minute in 0..1439)
        val at = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        return if (at > now) at else now.toLocalDate().plusDays(1).atTime(minute / 60, minute % 60).atZone(now.zone)
    }
}

/** One-shot local-time alarms avoid 24-hour drift at daylight-saving and timezone changes. */
@Singleton
class QuranReminders @Inject constructor(@param:ApplicationContext private val context: Context, private val dao: NaflDao) {
    private val mutex = Mutex()
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    fun pending(id: Long): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, QuranReminderReceiver::class.java).setAction(ACTION).setData(Uri.parse("prayerapp://quran-reminder/$id")).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun reschedule() = mutex.withLock {
        dao.reminders().forEach { r ->
            guard("QuranReminders", "schedule") {
                alarms.cancel(pending(r.id))
                if (r.enabled && r.minute in 0..1439) {
                    val at = QuranReminderTime.next(r.minute, ZonedDateTime.now()).toInstant().toEpochMilli()
                    try {
                        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(r.id))
                        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(r.id))
                    } catch (_: SecurityException) {
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(r.id))
                    }
                }
            } ?: AppErrors.report("Could not schedule a Quran reminder. Check notification and alarm permissions.")
        }
    }
    suspend fun remove(id: Long) = mutex.withLock {
        // Cancel before deleting so a scheduling error cannot leave an orphaned repeating reminder.
        alarms.cancel(pending(id))
        dao.deleteReminder(id)
    }
    suspend fun deliver(id: Long) {
        val r = dao.reminders().firstOrNull { it.id == id && it.enabled } ?: return
        try {
            createChannels()
            val channel = if (r.alarmSound) SOUND else NORMAL
            context.notifySafely(REMINDER_NOTIFICATION, baseNotification(context, channel, "Time to read Quran", "Take a moment to read, then log it in Streaks → Nafl.")
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER))
        } finally { reschedule() }
    }
    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(NORMAL, "Quran reading reminders", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(SOUND, "Quran reminders (alarm sound)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            },
        ))
    }
    companion object {
        const val ACTION = "com.kamalahmed.prayerapp.QURAN_REMINDER"
        const val NORMAL = "quran_reading_v1"
        const val SOUND = "quran_reading_alarm_v1"
        const val REMINDER_NOTIFICATION = 7100
    }
}

@AndroidEntryPoint
class QuranReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: QuranReminders
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != QuranReminders.ACTION) return
        val id = intent.getLongExtra("id", -1)
        if (id <= 0) return
        launchAsync("QuranReminderReceiver") { reminders.deliver(id) }
    }
}
