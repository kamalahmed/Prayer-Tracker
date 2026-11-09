package com.kamalahmed.prayerapp.tracker

import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.kamalahmed.prayerapp.core.model.Prayer
import dagger.hilt.android.AndroidEntryPoint
import com.kamalahmed.prayerapp.core.safety.launchAsync
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** "Prayed ✓" action on prayer notifications. */
@AndroidEntryPoint
class MarkPrayedReceiver : BroadcastReceiver() {
    @Inject lateinit var tracker: TrackerRepository
    @Inject lateinit var settings: com.kamalahmed.prayerapp.data.settings.SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val prayer = Prayer.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PRAYER) } ?: return
        val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        val notifId = intent.getIntExtra(EXTRA_NOTIF, 0)
        launchAsync("MarkPrayed") {
            // Use the prayer-time zone from settings (same as the schedule), not the device zone: they differ
            // when travelling with a manually chosen city, which marked the wrong day near midnight.
            val current = settings.current()
            val zone = runCatching { ZoneId.of(current.timeZoneId) }.getOrDefault(ZoneId.systemDefault())
            val at = Instant.ofEpochMilli(time).atZone(zone)
            // An Isha shifted past midnight belongs to the previous day's log.
            val date = runCatching { com.kamalahmed.prayerapp.core.prayer.PrayerSchedule(current.toCalcParams()).dateOf(prayer, at) }
                .getOrDefault(at.toLocalDate())
            tracker.set(date, prayer, PrayerStatus.ON_TIME)
            if (notifId != 0) NotificationManagerCompat.from(context).cancel(notifId)
        }
    }

    companion object {
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_TIME = "time"
        private const val EXTRA_NOTIF = "notif"
        fun pendingIntent(ctx: Context, prayer: Prayer, time: Long, notifId: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, 500 + prayer.ordinal,
            Intent(ctx, MarkPrayedReceiver::class.java).putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_TIME, time).putExtra(EXTRA_NOTIF, notifId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
