package com.kamalahmed.prayerapp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kamalahmed.prayerapp.alarm.AlarmHooks
import com.kamalahmed.prayerapp.alarm.AlarmType
import com.kamalahmed.prayerapp.core.model.Prayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps widgets fresh without polling: countdowns tick inside the widget (Chronometer), and content is
 * re-rendered only at boundaries (each prayer, Sunrise, Islamic midnight, date change) via one alarm,
 * plus on time/zone changes, settings/tracker changes, and an hourly WorkManager safety net.
 */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private const val WORK = "widget-refresh"
    private const val REQ = 9_001

    suspend fun updateAll(context: Context) {
        if (!WidgetKinds.anyPlaced(context)) return
        // Update each placed instance through its own receiver's widget, by appWidgetId. GlanceAppWidget.updateAll()
        // looks instances up by the GlanceAppWidget class name, which broke when R8 merged the six widget classes
        // (every widget was re-rendered with another kind's layout, e.g. "All prayers" collapsing to "Next prayer").
        val glance = GlanceAppWidgetManager(context)
        WidgetKind.entries.forEach { kind ->
            val widget = WidgetKinds.widget(kind)
            WidgetKinds.ids(context, kind).forEach { appWidgetId ->
                runCatching { widget.update(context, glance.getGlanceIdBy(appWidgetId)) }
                    .onFailure { Log.w(TAG, "update $kind #$appWidgetId", it) }
            }
        }
    }

    /** Re-render everything and arm the next boundary alarm. */
    suspend fun refresh(context: Context) {
        updateAll(context)
        scheduleNextBoundary(context)
    }

    fun ensureScheduled(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (WidgetKinds.anyPlaced(context)) {
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.HOURS).build())
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { refresh(context) }
        } else {
            wm.cancelUniqueWork(WORK)
            cancelBoundary(context)
        }
    }

    private fun boundaryIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQ, Intent(context, WidgetTickReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun cancelBoundary(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(boundaryIntent(context))
    }

    suspend fun scheduleNextBoundary(context: Context) {
        if (!WidgetKinds.anyPlaced(context)) { cancelBoundary(context); return }
        val am = context.getSystemService<AlarmManager>() ?: return
        val at = widgetDeps(context).loadWidgetData().nextBoundary.toInstant().toEpochMilli() + 1_000
        val pi = boundaryIntent(context)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { WidgetUpdater.refresh(context) } finally { pending.finish() }
        }
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refresh(applicationContext)
        return Result.success()
    }
}

/** Alarm/system hooks: refresh widgets whenever a prayer alarm fires or the clock/zone changes. */
@Singleton
class WidgetAlarmHooks @Inject constructor(@ApplicationContext private val context: Context) : AlarmHooks {
    override suspend fun onAlarm(type: AlarmType, prayer: Prayer) = WidgetUpdater.updateAll(context)
    override suspend fun onSystemChange() = WidgetUpdater.refresh(context)
}
