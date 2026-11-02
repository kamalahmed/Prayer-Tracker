package com.kamalahmed.prayerapp.widget

import com.kamalahmed.prayerapp.R
import com.kamalahmed.prayerapp.core.hijri.HijriCalendar
import com.kamalahmed.prayerapp.core.hijri.HijriDate
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerEvent
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.StreakCalculator
import com.kamalahmed.prayerapp.tracker.TrackerData
import java.time.LocalDate
import java.time.ZonedDateTime

/** One line in a widget list. [prayer] is null for Islamic midnight. */
data class WidgetRow(
    val prayer: Prayer?,
    val name: String,
    val abbr: String,
    val time: ZonedDateTime,
    val icon: Int,
    val isNext: Boolean,
    val passed: Boolean,
)

/** Everything a widget needs, computed once per update. */
data class WidgetData(
    val now: ZonedDateTime,
    val city: String,
    /** The day shown in lists: today, or tomorrow once Isha has started. */
    val listDate: LocalDate,
    val rows: List<WidgetRow>,
    val midnight: WidgetRow,
    val next: PrayerEvent,
    val previous: PrayerEvent,
    val hijri: HijriDate,
    val today: LocalDate,
    val todayTimes: Map<Prayer, ZonedDateTime>,
    val todayStatus: Map<Prayer, PrayerStatus>,
    /** Prayers of today whose time has started (only those can be marked). */
    val markable: Set<Prayer>,
    val streakDays: Int,
    val bestDays: Int,
    val appUse24h: Boolean,
    val appAbbr: Boolean,
) {
    val prayedToday: Int get() = Prayer.obligatory.count { StreakCalculator.prayed(todayStatus[it]) }

    /** 0..1 progress from the previous prayer to the next one. */
    val progress: Float get() {
        val total = next.time.toEpochSecond() - previous.time.toEpochSecond()
        val done = now.toEpochSecond() - previous.time.toEpochSecond()
        return if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Earliest moment after [now] at which the widget content changes on its own. */
    val nextBoundary: ZonedDateTime get() = listOfNotNull(
        rows.map { it.time }.firstOrNull { it.isAfter(now) },
        next.time,
        midnight.time.takeIf { it.isAfter(now) },
        today.plusDays(1).atStartOfDay(now.zone),
    ).min()

    companion object {
        fun icon(p: Prayer?): Int = when (p) {
            Prayer.FAJR -> R.drawable.w_ic_fajr
            Prayer.SUNRISE -> R.drawable.w_ic_sunrise
            Prayer.DHUHR -> R.drawable.w_ic_dhuhr
            Prayer.ASR -> R.drawable.w_ic_asr
            Prayer.MAGHRIB -> R.drawable.w_ic_maghrib
            Prayer.ISHA -> R.drawable.w_ic_isha
            null -> R.drawable.w_ic_midnight
        }

        fun build(schedule: PrayerSchedule, s: AppSettings, tracker: TrackerData, now: ZonedDateTime): WidgetData {
            val zNow = now.withZoneSameInstant(schedule.zone)
            val today = zNow.toLocalDate()
            val todayDay = schedule.day(today)
            val next = schedule.next(zNow)
            val previous = schedule.current(zNow)
            val listDate = if (zNow.isBefore(todayDay[Prayer.ISHA])) today else today.plusDays(1)
            val day = schedule.day(listDate)
            val rows = day.times.entries.sortedBy { it.value }.map { (p, t) ->
                WidgetRow(p, p.displayName, p.abbr, t, icon(p), isNext = p == next.prayer && t == next.time, passed = !t.isAfter(zNow))
            }
            // Midnight belonging to the night we are in / approaching.
            val yMid = schedule.day(today.minusDays(1)).midnight
            val mid = if (zNow.isBefore(yMid)) yMid else todayDay.midnight
            val midnight = WidgetRow(null, "Midnight", "MID", mid, icon(null), isNext = false, passed = !mid.isAfter(zNow))
            val passed = schedule.passedToday(zNow)
            val stats = StreakCalculator.compute(tracker, today, passed, heatmapDays = 1)
            return WidgetData(
                now = zNow,
                city = s.cityName,
                listDate = listDate,
                rows = rows,
                midnight = midnight,
                next = next,
                previous = previous,
                hijri = HijriCalendar.at(zNow, todayDay[Prayer.MAGHRIB], s.hijriAdjust, s.autoIncrementHijri),
                today = today,
                todayTimes = todayDay.times,
                todayStatus = tracker[today] ?: emptyMap(),
                markable = passed.toSet(),
                streakDays = stats.currentDays,
                bestDays = stats.bestDays,
                appUse24h = s.use24h,
                appAbbr = s.abbreviations,
            )
        }
    }
}
