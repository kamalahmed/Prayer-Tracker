package com.kamalahmed.prayerapp.core.model

import java.time.Duration
import java.time.LocalTime
import java.time.temporal.TemporalAccessor
import java.util.Locale

object TimeFormat {
    /** "5:08 am" / "17:08" */
    fun time(t: TemporalAccessor, use24h: Boolean, showAmPm: Boolean = true): String {
        val lt = LocalTime.from(t)
        return if (use24h) String.format(Locale.US, "%02d:%02d", lt.hour, lt.minute)
        else {
            val h = if (lt.hour % 12 == 0) 12 else lt.hour % 12
            val base = String.format(Locale.US, "%d:%02d", h, lt.minute)
            if (showAmPm) "$base ${if (lt.hour < 12) "am" else "pm"}" else base
        }
    }

    fun amPm(t: TemporalAccessor): String = if (LocalTime.from(t).hour < 12) "am" else "pm"

    /** "01:05:52" */
    fun countdown(d: Duration): String {
        val s = d.seconds.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** "1h 05m" / "12m" */
    fun shortCountdown(d: Duration): String {
        val m = (d.seconds.coerceAtLeast(0) + 59) / 60
        return if (m >= 60) String.format(Locale.US, "%dh %02dm", m / 60, m % 60) else "${m}m"
    }
}
