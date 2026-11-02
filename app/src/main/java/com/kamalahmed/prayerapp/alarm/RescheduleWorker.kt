package com.kamalahmed.prayerapp.alarm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Safety net: periodically re-arms alarms in case an OEM killed them. Cheap and battery friendly. */
@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: AlarmScheduler,
    private val hooks: AlarmHooks,
    private val settings: com.kamalahmed.prayerapp.data.settings.SettingsRepository,
    private val location: com.kamalahmed.prayerapp.core.location.LocationRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // Automatic location: only accepted when moved beyond the geofence (settings change re-arms alarms).
        if (settings.current().autoLocation) runCatching { location.refresh(force = false) }
        scheduler.rescheduleAll()
        hooks.onSystemChange()
        return Result.success()
    }

    companion object {
        fun enqueue(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "reschedule", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RescheduleWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}
