package com.kamalahmed.prayerapp.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.R

object Channels {
    const val ADHAN = "adhan_v1"
    const val BEEP = "prayer_beep_v1"
    const val SILENT = "prayer_silent_v1"
    const val REMINDER = "reminder_v1"
    const val IQAMA = "iqama_v1"

    fun create(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(ADHAN, "Adhan (playing)", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Shown while the adhan plays. Sound is played by the app, not the channel."
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(BEEP, "Prayer time (beep)", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), alarmAttrs)
                },
                NotificationChannel(SILENT, "Prayer time (silent)", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(REMINDER, "Pre-prayer reminders", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(IQAMA, "Iqama countdown", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                },
            )
        )
    }

    fun isBlocked(ctx: Context, id: String): Boolean {
        val ch = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(id) ?: return false
        return ch.importance == NotificationManager.IMPORTANCE_NONE
    }
}

object NotificationIds {
    const val ADHAN_PLAYING = 1000
    fun prayer(ordinal: Int) = 1100 + ordinal
    fun reminder(ordinal: Int) = 1200 + ordinal
    fun iqama(ordinal: Int) = 1300 + ordinal
    const val DUA = 1400
}

fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
    ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

@android.annotation.SuppressLint("MissingPermission")
fun Context.notifySafely(id: Int, builder: NotificationCompat.Builder) {
    val nm = NotificationManagerCompat.from(this)
    if (nm.areNotificationsEnabled()) runCatching { nm.notify(id, builder.build()) }
}

fun baseNotification(ctx: Context, channel: String, title: String, text: String): NotificationCompat.Builder =
    NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_stat_prayer)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(openAppIntent(ctx))
        .setAutoCancel(true)
