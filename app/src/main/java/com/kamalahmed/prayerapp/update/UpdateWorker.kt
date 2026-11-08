package com.kamalahmed.prayerapp.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.R
import com.kamalahmed.prayerapp.core.safety.guard
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Connected 15-minute safety net; Android may delay work in Doze or under vendor restrictions. */
@HiltWorker
class UpdateWorker @AssistedInject constructor(@Assisted ctx: Context,@Assisted params: WorkerParameters,
    private val manager: UpdateManager) : CoroutineWorker(ctx,params) {
    override suspend fun doWork(): Result {
        return guard("UpdateWorker","check") { manager.checkAutomatic(); Result.success() } ?: Result.retry()
    }
    companion object {
        const val CHANNEL = "updates_v1"
        internal const val NAME = "update_check"
        internal fun request(wifiOnly: Boolean): PeriodicWorkRequest = PeriodicWorkRequestBuilder<UpdateWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()).build()
        fun schedule(ctx: Context,enabled: Boolean,wifiOnly: Boolean,replace: Boolean = false) {
            guard("UpdateWorker","schedule") {
                val wm = WorkManager.getInstance(ctx)
                if (!enabled) wm.cancelUniqueWork(NAME)
                // UPDATE replaces the old daily/one-hour-delay WorkSpec on existing installations too.
                else wm.enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.UPDATE,request(wifiOnly))
            }
        }
        fun createChannel(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL,"App updates",NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Available updates and installation progress"
                })
        }
        internal fun action(ctx: Context,rel: ReleaseInfo): PendingIntent = PendingIntent.getActivity(ctx,7003,
            Intent(ctx,MainActivity::class.java).putExtra(MainActivity.EXTRA_ACCEPT_UPDATE,rel.tag)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        /** True only when posted. Never consume a release's notification key on a permission/channel failure. */
        fun notify(ctx: Context,rel: ReleaseInfo,ready: Boolean = false): Boolean = guard("UpdateWorker","notify") {
            createChannel(ctx)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@guard false
            val nm = NotificationManagerCompat.from(ctx)
            if (!nm.areNotificationsEnabled() || ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return@guard false
            val open = PendingIntent.getActivity(ctx,7001,Intent(ctx,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val text = if (ready) "Download verified. Tap Update to install." else "Tap Update to download and install, or open Today for details."
            nm.notify(7001,NotificationCompat.Builder(ctx,CHANNEL).setSmallIcon(R.drawable.ic_stat_update)
                .setContentTitle(if (ready) "Ready to install: ${rel.version}" else "Update available: ${rel.version}")
                .setContentText(text).setContentIntent(open).setAutoCancel(true)
                .addAction(0,"Update",action(ctx,rel)).build())
            true
        } ?: false
    }
}
