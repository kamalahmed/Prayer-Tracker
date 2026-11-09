package com.kamalahmed.prayerapp.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsRepository,
) {
    private val am = ctx.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    /** Cancel and re-create every prayer alarm. Safe to call often (idempotent). */
    suspend fun rescheduleAll() {
        val s = settings.current()
        val params = s.toCalcParams()
        val now = ZonedDateTime.now(params.zone)
        val plan = AlarmPlanner.plan(s, PrayerSchedule(params), now)
        // cancel everything we may have scheduled before (except snoozes)
        for (p in Prayer.entries) for (t in listOf(AlarmType.PRAYER, AlarmType.REMINDER, AlarmType.IQAMA, AlarmType.IQAMA_NOTICE)) {
            pending(t, p, null, 0L, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        }
        plan.forEach { set(it, useAlarmClock = useAlarmClock(s, it)) }
        Log.i(TAG, "Scheduled ${plan.size} alarms; exact=${canScheduleExact()}")
    }

    /** Audible alarms (adhan and the before-iqamah alarm) use setAlarmClock: Doze-exempt and allowed to start the sound service. */
    internal fun useAlarmClock(s: com.kamalahmed.prayerapp.data.settings.AppSettings, a: PlannedAlarm): Boolean = when (a.type) {
        AlarmType.PRAYER -> s.mode(a.prayer) == NotifyMode.ADHAN
        AlarmType.IQAMA_NOTICE -> Iqamah.alarm(s, a.prayer, a.prayerTime)
        else -> false
    }

    fun scheduleSnooze(prayer: Prayer, prayerTimeMillis: Long, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val pi = pending(AlarmType.SNOOZE, prayer, at, prayerTimeMillis, PendingIntent.FLAG_UPDATE_CURRENT)!!
        setExact(at, pi, useAlarmClock = true)
    }

    private fun set(a: PlannedAlarm, useAlarmClock: Boolean) {
        val at = a.at.toInstant().toEpochMilli()
        val pi = pending(a.type, a.prayer, at, a.prayerTime.toInstant().toEpochMilli(), PendingIntent.FLAG_UPDATE_CURRENT)!!
        setExact(at, pi, useAlarmClock)
    }

    private fun setExact(at: Long, pi: PendingIntent, useAlarmClock: Boolean) {
        try {
            when {
                !canScheduleExact() -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                // setAlarmClock is exempt from Doze and app standby: best for the audible adhan.
                useAlarmClock -> am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAppIntent(ctx)), pi)
                else -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(type: AlarmType, prayer: Prayer, at: Long?, prayerTime: Long, flags: Int): PendingIntent? {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_PREFIX + type.name
            putExtra(EXTRA_PRAYER, prayer.name)
            putExtra(EXTRA_PRAYER_TIME, prayerTime)
            if (at != null) putExtra(EXTRA_AT, at)
        }
        return PendingIntent.getBroadcast(ctx, prayer.ordinal * 10 + type.code, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val TAG = "AlarmScheduler"
        const val ACTION_PREFIX = "com.kamalahmed.prayerapp.alarm."
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_PRAYER_TIME = "prayer_time"
        const val EXTRA_AT = "at"
    }
}
