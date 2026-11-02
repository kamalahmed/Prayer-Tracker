package com.kamalahmed.prayerapp.tracker

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.kamalahmed.prayerapp.core.model.Prayer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** "Prayed ✓" action on prayer notifications. */
@AndroidEntryPoint
class MarkPrayedReceiver : BroadcastReceiver() {
    @Inject lateinit var tracker: TrackerRepository

    override fun onReceive(context: Context, intent: Intent) {
        val prayer = Prayer.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PRAYER) } ?: return
        val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        val notifId = intent.getIntExtra(EXTRA_NOTIF, 0)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
                tracker.set(date, prayer, PrayerStatus.ON_TIME)
                if (notifId != 0) NotificationManagerCompat.from(context).cancel(notifId)
            } finally { pending.finish() }
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
