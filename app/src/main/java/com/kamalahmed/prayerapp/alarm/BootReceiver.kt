package com.kamalahmed.prayerapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import com.kamalahmed.prayerapp.core.safety.guard
import com.kamalahmed.prayerapp.core.safety.launchAsync
import javax.inject.Inject

/**
 * Re-creates alarms after reboot, app update, clock/timezone change and when the user grants
 * the exact-alarm permission.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var hooks: AlarmHooks

    @Inject lateinit var quranReminders: com.kamalahmed.prayerapp.nafl.QuranReminders

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        launchAsync("BootReceiver") {
            guard("BootReceiver", "channels") { Channels.create(context) }
            guard("BootReceiver", "rescheduleAll") { scheduler.rescheduleAll() }
            guard("BootReceiver", "Quran reminders") { quranReminders.reschedule() }
            guard("BootReceiver", "onSystemChange") { hooks.onSystemChange() }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
