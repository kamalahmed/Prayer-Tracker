package com.kamalahmed.prayerapp.core.hijri

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

data class HijriDate(val year: Int, val month: Int, val day: Int) {
    val monthName: String get() = MONTHS[month - 1]
    fun format(): String = "$day $monthName $year AH"

    companion object {
        val MONTHS = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
        )
    }
}

object HijriCalendar {
    /** Umm al-Qura (java.time HijrahDate) with manual day adjustment. */
    fun of(date: LocalDate, adjustDays: Int = 0): HijriDate {
        val h = HijrahDate.from(date.plusDays(adjustDays.toLong()))
        return HijriDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    }

    /**
     * Hijri date for a moment. With [autoIncrementAfterMaghrib], the Islamic day rolls over at Maghrib.
     */
    fun at(now: ZonedDateTime, maghrib: ZonedDateTime, adjustDays: Int, autoIncrementAfterMaghrib: Boolean): HijriDate {
        val extra = if (autoIncrementAfterMaghrib && !now.isBefore(maghrib)) 1 else 0
        return of(now.toLocalDate(), adjustDays + extra)
    }

    /** Gregorian date for a hijri date (inverse of [of]); null if outside the supported range. */
    fun toGregorian(year: Int, month: Int, day: Int, adjustDays: Int = 0): LocalDate? = runCatching {
        LocalDate.from(HijrahDate.of(year, month, day)).minusDays(adjustDays.toLong())
    }.getOrNull()
}

data class IslamicHoliday(val name: String, val hijriMonth: Int, val hijriDay: Int)

data class HolidayOccurrence(val holiday: IslamicHoliday, val date: LocalDate, val hijri: HijriDate)

object IslamicHolidays {
    val all = listOf(
        IslamicHoliday("Islamic New Year", 1, 1),
        IslamicHoliday("Ashura", 1, 10),
        IslamicHoliday("Mawlid an-Nabi", 3, 12),
        IslamicHoliday("Isra and Mi'raj", 7, 27),
        IslamicHoliday("Mid-Sha'ban", 8, 15),
        IslamicHoliday("First day of Ramadan", 9, 1),
        IslamicHoliday("Laylat al-Qadr (27th)", 9, 27),
        IslamicHoliday("Eid al-Fitr", 10, 1),
        IslamicHoliday("Day of Arafah", 12, 9),
        IslamicHoliday("Eid al-Adha", 12, 10),
    )

    /** Upcoming holidays on or after [from], sorted, spanning roughly a year. */
    fun upcoming(from: LocalDate, adjustDays: Int = 0, count: Int = 10): List<HolidayOccurrence> {
        val h = HijriCalendar.of(from, adjustDays)
        return listOf(h.year, h.year + 1).flatMap { y ->
            all.mapNotNull { hol ->
                HijriCalendar.toGregorian(y, hol.hijriMonth, hol.hijriDay, adjustDays)?.let {
                    HolidayOccurrence(hol, it, HijriDate(y, hol.hijriMonth, hol.hijriDay))
                }
            }
        }.filter { !it.date.isBefore(from) }.sortedBy { it.date }.take(count)
    }
}
