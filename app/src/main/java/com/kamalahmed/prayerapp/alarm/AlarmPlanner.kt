package com.kamalahmed.prayerapp.alarm

import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.settings.AppSettings
import java.time.ZonedDateTime

enum class AlarmType(val code: Int) { PRAYER(0), REMINDER(1), IQAMA(2), SNOOZE(3) }

data class PlannedAlarm(val type: AlarmType, val prayer: Prayer, val at: ZonedDateTime, val prayerTime: ZonedDateTime) {
    val requestCode: Int get() = prayer.ordinal * 10 + type.code
}

/** Pure planning logic (unit-tested): which alarms should exist right now. */
object AlarmPlanner {
    fun plan(s: AppSettings, schedule: PrayerSchedule, now: ZonedDateTime): List<PlannedAlarm> {
        val upcoming = schedule.upcoming(now.minusMinutes(maxOf(s.iqamaMinutes.values.maxOrNull() ?: 0, 0).toLong() + 1), 20, includeSunrise = true)
        val out = ArrayList<PlannedAlarm>()
        for (p in Prayer.entries) {
            val mode = s.mode(p)
            if (mode == NotifyMode.OFF) continue
            val events = upcoming.filter { it.prayer == p }
            events.firstOrNull { it.time.isAfter(now) }?.let { out += PlannedAlarm(AlarmType.PRAYER, p, it.time, it.time) }
            if (s.preReminderMinutes > 0 && p.isPrayer) {
                events.map { it.time.minusMinutes(s.preReminderMinutes.toLong()) to it.time }
                    .firstOrNull { it.first.isAfter(now) }
                    ?.let { out += PlannedAlarm(AlarmType.REMINDER, p, it.first, it.second) }
            }
            if (s.iqamaEnabled && p.isPrayer && s.iqama(p) > 0) {
                events.map { it.time.plusMinutes(s.iqama(p).toLong()) to it.time }
                    .firstOrNull { it.first.isAfter(now) }
                    ?.let { out += PlannedAlarm(AlarmType.IQAMA, p, it.first, it.second) }
            }
        }
        return out.sortedBy { it.at }
    }
}
