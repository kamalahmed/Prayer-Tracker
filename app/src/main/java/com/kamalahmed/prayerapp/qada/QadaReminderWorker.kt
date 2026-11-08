package com.kamalahmed.prayerapp.qada

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kamalahmed.prayerapp.alarm.Channels
import com.kamalahmed.prayerapp.alarm.baseNotification
import com.kamalahmed.prayerapp.alarm.notifySafely
import com.kamalahmed.prayerapp.core.safety.guard
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Optional daily nudge (off by default): fires once a day at the chosen time if today's qada goal isn't met. */
@HiltWorker
class QadaReminderWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: QadaRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        guard(TAG, "remind") {
            val c = repo.currentConfig()
            if (!c.reminder || c.dailyGoal <= 0) return@guard
            val done = repo.countToday(LocalDate.now())
            if (done < c.dailyGoal) {
                val left = c.dailyGoal - done
                applicationContext.notifySafely(
                    NOTIF_ID,
                    baseNotification(applicationContext, Channels.REMINDER, "Qada goal",
                        "$left more make-up ${if (left == 1) "prayer" else "prayers"} to reach today's goal of ${c.dailyGoal}."),
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "QadaReminder"
        private const val WORK = "qada-reminder"
        const val NOTIF_ID = 1500

        fun initialDelay(now: LocalDateTime, minuteOfDay: Int): Duration {
            val m = minuteOfDay.coerceIn(0, 24 * 60 - 1)
            var at = now.toLocalDate().atTime(m / 60, m % 60)
            if (!at.isAfter(now)) at = at.plusDays(1)
            return Duration.between(now, at)
        }

        fun schedule(context: Context, config: QadaConfig) {
            val wm = WorkManager.getInstance(context)
            if (!config.reminder || config.dailyGoal <= 0) { wm.cancelUniqueWork(WORK); return }
            val req = PeriodicWorkRequestBuilder<QadaReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelay(LocalDateTime.now(), config.reminderMinute).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
