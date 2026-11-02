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
import javax.inject.Inject

@HiltAndroidApp
class PrayerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var hooks: AlarmHooks

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Channels.create(this)
        RescheduleWorker.enqueue(this)
        // Any settings change (method, location, modes, reminders…) re-arms alarms and refreshes widgets.
        appScope.launch {
            settings.settings.distinctUntilChanged().debounce(400).collect {
                scheduler.rescheduleAll()
                hooks.onSystemChange()
            }
        }
    }
}
