package com.kamalahmed.prayerapp.alarm

import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.data.settings.AppSettings
import java.time.DayOfWeek
import java.time.ZonedDateTime

object Iqamah {
    fun friday(s: AppSettings, p: Prayer, adhan: ZonedDateTime) = s.jumuahEnabled && p == Prayer.DHUHR && adhan.dayOfWeek == DayOfWeek.FRIDAY
    fun enabled(s: AppSettings, p: Prayer, adhan: ZonedDateTime) = if (friday(s,p,adhan)) s.jumuahNotify else p in s.iqamaNotifications
    fun lead(s: AppSettings, p: Prayer, adhan: ZonedDateTime) = (if (friday(s,p,adhan)) s.jumuahBefore else s.iqamaBefore[p] ?: 0).coerceIn(0,180)
    fun label(s: AppSettings, p: Prayer, adhan: ZonedDateTime) = if (friday(s,p,adhan)) "Jumu'ah" else p.displayName
    fun time(s: AppSettings, p: Prayer, adhan: ZonedDateTime): ZonedDateTime? {
        if (!p.isPrayer) return null
        if (friday(s,p,adhan) && !s.jumuahFixed) return adhan.plusMinutes(s.jumuahOffset.coerceIn(0,180).toLong())
        val minute = if (friday(s,p,adhan)) s.jumuahMinute else s.iqamaFixed[p]
        if (minute == null) return adhan.plusMinutes(s.iqama(p).coerceIn(0,180).toLong())
        if (minute !in 0..1439) return null
        var at = adhan.toLocalDate().atTime(minute / 60, minute % 60).atZone(adhan.zone)
        if (at < adhan && p == Prayer.ISHA) at = at.plusDays(1)
        return at.takeIf { it >= adhan }
    }
}
