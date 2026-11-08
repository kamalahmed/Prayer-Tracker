package com.kamalahmed.prayerapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kamalahmed.prayerapp.alarm.AlarmHooks
import com.kamalahmed.prayerapp.alarm.AlarmScheduler
import com.kamalahmed.prayerapp.alarm.Channels
import com.kamalahmed.prayerapp.alarm.RescheduleWorker
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.kamalahmed.prayerapp.core.safety.CrashLog
import com.kamalahmed.prayerapp.core.safety.guard
import javax.inject.Inject

@HiltAndroidApp
class PrayerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var hooks: AlarmHooks
    @Inject lateinit var updateManager: com.kamalahmed.prayerapp.update.UpdateManager
    @Inject lateinit var updatePrefs: com.kamalahmed.prayerapp.update.UpdatePrefs
    @Inject lateinit var tracker: com.kamalahmed.prayerapp.tracker.TrackerRepository

    @Inject lateinit var nafl: com.kamalahmed.prayerapp.nafl.NaflRepository
    @Inject lateinit var quranReminders: com.kamalahmed.prayerapp.nafl.QuranReminders
    @Inject lateinit var backups: com.kamalahmed.prayerapp.backup.BackupRepository

    val appScope = com.kamalahmed.prayerapp.core.safety.safeScope("PrayerApp")

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // First: record any later crash to local storage (Settings > App > Diagnostics). No network, no PII.
        CrashLog.install(this, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        guard(TAG, "channels") { Channels.create(this) }
        guard(TAG, "update channel") { com.kamalahmed.prayerapp.update.UpdateWorker.createChannel(this) }
        guard(TAG, "update worker") { com.kamalahmed.prayerapp.update.UpdateWorker.schedule(this, updatePrefs.autoCheck.value, updatePrefs.wifiOnly.value) }
        appScope.launch { guard(TAG, "startup backup") { backups.ensureTodaySnapshot() } }
        guard(TAG, "daily backup") { com.kamalahmed.prayerapp.backup.LocalBackupWorker.schedule(this) }
        guard(TAG, "reschedule worker") { RescheduleWorker.enqueue(this) }
        appScope.launch { guard(TAG, "Quran reminders") { quranReminders.reschedule() } }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                appScope.launch { guard(TAG,"resume update check") { updateManager.checkAutomatic() } }
                appScope.launch { guard(TAG,"resume summaries") { val s=settings.current();if(s.summaryDaily || s.summaryWeekly) com.kamalahmed.prayerapp.summary.SummaryWorker.checkOnResume(this@PrayerApp) } }
            }
            override fun onActivityCreated(a: android.app.Activity,b: android.os.Bundle?) = Unit
            override fun onActivityStarted(a: android.app.Activity) = Unit
            override fun onActivityPaused(a: android.app.Activity) = Unit
            override fun onActivityStopped(a: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(a: android.app.Activity,b: android.os.Bundle) = Unit
            override fun onActivityDestroyed(a: android.app.Activity) = Unit
        })
        // Any settings change (method, location, modes, reminders…) re-arms alarms and refreshes widgets.
        appScope.launch {
            settings.settings.distinctUntilChanged().debounce(400).collect { s ->
                guard(TAG,"summary schedule") { com.kamalahmed.prayerapp.summary.SummaryWorker.schedule(this@PrayerApp,s) }
                // Each step guarded so one failure doesn't end the collector (no more re-arming for the session).
                guard(TAG, "rescheduleAll") { scheduler.rescheduleAll() }
                guard(TAG, "onSystemChange") { hooks.onSystemChange() }
            }
        }
        // Marking prayers in the app updates the streak widgets.
        appScope.launch {
            tracker.changes.debounce(300).collect { guard(TAG, "widgets") { com.kamalahmed.prayerapp.widget.WidgetUpdater.updateAll(this@PrayerApp) } }
        }
        appScope.launch {
            kotlinx.coroutines.flow.combine(nafl.items,nafl.logs) { i,l -> i to l }.debounce(300).collect {
                guard(TAG,"counter widgets") { com.kamalahmed.prayerapp.widget.WidgetUpdater.updateAll(this@PrayerApp) }
            }
        }
        guard(TAG, "widget schedule") { com.kamalahmed.prayerapp.widget.WidgetUpdater.ensureScheduled(this) }
    }

    private companion object { const val TAG = "PrayerApp" }
}
