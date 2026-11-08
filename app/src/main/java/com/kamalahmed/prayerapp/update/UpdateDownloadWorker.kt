package com.kamalahmed.prayerapp.update

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kamalahmed.prayerapp.R
import com.kamalahmed.prayerapp.core.safety.guard
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Download survives leaving the app/process recreation; never enqueued without explicit acceptance. */
@HiltWorker
class UpdateDownloadWorker @AssistedInject constructor(@Assisted ctx: Context,@Assisted params: WorkerParameters,
    private val manager: UpdateManager) : CoroutineWorker(ctx,params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(null)
    private fun foreground(s: UpdateState.Downloading?): ForegroundInfo {
        UpdateWorker.createChannel(applicationContext)
        val n = NotificationCompat.Builder(applicationContext,UpdateWorker.CHANNEL).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("Downloading prayer app update")
            .setContentText(s?.let { "${(it.fraction*100).toInt()}% · ${it.bytes/1024} / ${it.total/1024} KB" } ?: "Preparing verified download…")
            .setOnlyAlertOnce(true).setOngoing(true).setProgress(100,s?.let { (it.fraction*100).toInt() } ?: 0,s == null || s.total <= 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(7002,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(7002,n)
    }
    override suspend fun doWork(): Result = coroutineScope {
        guard("UpdateDownload","run") {
            if (manager.prefs.acceptedRelease() == null) return@guard Result.success()
            setForeground(foreground(null))
            val progress = launch { manager.state.collect { s ->
                if (s is UpdateState.Downloading) { guard("UpdateDownload","progress") { setForeground(foreground(s)) }; delay(500) }
            } }
            try {
                when (val s = manager.downloadAccepted()) {
                    is UpdateState.Downloaded -> { manager.notifyDownloaded(s); Result.success() }
                    is UpdateState.Error -> { UpdateWorker.notify(applicationContext,s.release ?: return@guard Result.failure())
                        if (s.retryable) Result.retry() else Result.failure() }
                    else -> Result.success()
                }
            } finally { progress.cancelAndJoin() }
        } ?: run {
            manager.downloadFailed("Android couldn't start the background download. Open the app and tap Update to retry.")
            Result.failure()
        }
    }
    companion object {
        const val NAME = "accepted_update_download"
        fun enqueue(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(NAME,ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateDownloadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
