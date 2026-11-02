package com.kamalahmed.prayerapp.core.prayer

import com.kamalahmed.prayerapp.core.model.Prayer
import java.time.LocalDate
import java.time.ZonedDateTime

data class PrayerEvent(val prayer: Prayer, val time: ZonedDateTime)

/** Helpers for "which prayer is next / current" across day boundaries. */
class PrayerSchedule(private val params: CalcParams) {

    private val cache = HashMap<LocalDate, PrayerDay>()

    fun day(date: LocalDate): PrayerDay = cache.getOrPut(date) { PrayerTimesCalculator.calculate(date, params) }

    /** Events (including Sunrise if [includeSunrise]) from yesterday through tomorrow, sorted. */
    private fun around(now: ZonedDateTime, includeSunrise: Boolean): List<PrayerEvent> {
        val d = now.withZoneSameInstant(params.zone).toLocalDate()
        return listOf(d.minusDays(1), d, d.plusDays(1)).flatMap { date ->
            day(date).times.filter { includeSunrise || it.key.isPrayer }.map { PrayerEvent(it.key, it.value) }
        }.sortedBy { it.time }
    }

    fun next(now: ZonedDateTime, includeSunrise: Boolean = false): PrayerEvent =
        around(now, includeSunrise).first { it.time.isAfter(now) }

    fun current(now: ZonedDateTime, includeSunrise: Boolean = false): PrayerEvent =
        around(now, includeSunrise).last { !it.time.isAfter(now) }

    /** Upcoming events strictly after [now], up to [count]. */
    fun upcoming(now: ZonedDateTime, count: Int, includeSunrise: Boolean = true): List<PrayerEvent> {
        val out = ArrayList<PrayerEvent>()
        var date = now.withZoneSameInstant(params.zone).toLocalDate()
        while (out.size < count) {
            day(date).times.forEach { (p, t) ->
                if ((includeSunrise || p.isPrayer) && t.isAfter(now)) out += PrayerEvent(p, t)
            }
            date = date.plusDays(1)
        }
        return out.sortedBy { it.time }.take(count)
    }
}
