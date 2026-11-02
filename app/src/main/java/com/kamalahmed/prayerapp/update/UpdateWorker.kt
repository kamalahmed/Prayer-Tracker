package com.kamalahmed.prayerapp.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Daily background check; posts one notification per newly found release. */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val manager: UpdateManager,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!manager.prefs.autoCheck.value) return Result.success()
        val s = manager.check()
        val rel = (s as? UpdateState.Available)?.release ?: (s as? UpdateState.Downloaded)?.release
        if (rel != null && manager.prefs.notifiedTag != rel.tag) {
            notify(applicationContext, rel)
            manager.prefs.notifiedTag = rel.tag
        }
        return Result.success() // errors (offline, rate limit) just wait for the next period
    }

    companion object {
        const val CHANNEL = "updates_v1"
        private const val NAME = "update_check"

        fun schedule(ctx: Context, enabled: Boolean, wifiOnly: Boolean, replace: Boolean = false) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS, 2, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun createChannel(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when a new version is available on GitHub"
                })
        }

        fun notify(ctx: Context, rel: ReleaseInfo) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                android.os.Build.VERSION.SDK_INT >= 33) return
            val open = PendingIntent.getActivity(ctx, 7001,
                Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_update)
                .setContentTitle("Update available: ${rel.version}")
                .setContentText("Tap to download and install in the app")
                .setStyle(NotificationCompat.BigTextStyle().bigText(rel.notes.take(400).ifBlank { "Tap to download and install in the app" }))
                .setContentIntent(open).setAutoCancel(true).build()
            NotificationManagerCompat.from(ctx).notify(7001, n)
        }
    }
}
