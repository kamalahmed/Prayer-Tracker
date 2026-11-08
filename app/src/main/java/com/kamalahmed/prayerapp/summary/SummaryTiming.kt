package com.kamalahmed.prayerapp.summary

import com.kamalahmed.prayerapp.data.settings.AppSettings
import java.time.*
import java.time.temporal.TemporalAdjusters

enum class SummaryKind { DAY, WEEK }
data class SummaryRequest(val kind: SummaryKind,val anchor: LocalDate)

/** Device-local wall times. A Sunday report is explicitly a week-so-far reflection. */
object SummaryTiming {
    fun due(now: ZonedDateTime,settings: AppSettings,delivered: Map<SummaryKind,LocalDate>): List<SummaryRequest> {
        val date=now.toLocalDate();val minute=now.hour*60+now.minute
        val requests=buildList {
            if(settings.summaryDaily && minute>=settings.summaryDailyMinute.coerceIn(0,1439)) add(SummaryRequest(SummaryKind.DAY,date))
            if(settings.summaryWeekly) {
                val at=settings.summaryWeeklyMinute.coerceIn(0,1439)
                var sunday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                var scheduled=sunday.atTime(at/60,at%60).atZone(now.zone)
                if(now<scheduled) { sunday=sunday.minusWeeks(1);scheduled=sunday.atTime(at/60,at%60).atZone(now.zone) }
                // Doze can defer Sunday's job past midnight. Keep the original week, with a bounded grace window.
                if(Duration.between(scheduled.toInstant(),now.toInstant())<=Duration.ofHours(24))
                    add(SummaryRequest(SummaryKind.WEEK,sunday.minusDays(6)))
            }
        }
        return requests.filter { delivered[it.kind]?.let { last -> last<it.anchor } ?: true }
    }
}
