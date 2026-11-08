package com.kamalahmed.prayerapp.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LocalBackupWorker @AssistedInject constructor(@Assisted context: Context,@Assisted params: WorkerParameters,private val backups: BackupRepository) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try {
        backups.dailySnapshot(); Result.success()
    } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch(e: Exception) {
        com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal("LocalBackup","Snapshot failed",e)
        applicationContext.getSharedPreferences("backup_metadata",Context.MODE_PRIVATE).edit().putString("last_error",e.message ?: "Backup failed").apply()
        Result.retry()
    }
    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("local-daily-backup",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<LocalBackupWorker>(24,TimeUnit.HOURS).build())
        }
    }
}
