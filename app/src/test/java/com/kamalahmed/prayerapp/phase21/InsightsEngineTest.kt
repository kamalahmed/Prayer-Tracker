package com.kamalahmed.prayerapp.phase21

import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.insights.CoachAction
import com.kamalahmed.prayerapp.insights.Health
import com.kamalahmed.prayerapp.insights.InsightsEngine
import com.kamalahmed.prayerapp.insights.InsightsInput
import com.kamalahmed.prayerapp.nafl.NaflDefaults
import com.kamalahmed.prayerapp.nafl.NaflLog
import com.kamalahmed.prayerapp.qada.QadaEntry
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.TrackerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Rule-based insights over the mockup's sample week (Mon 2 – Sun 8 Nov 2026 vs 26 Oct – 1 Nov). */
class InsightsEngineTest {
    private val today = LocalDate.of(2026, 11, 9) // Monday
    private val week = (0 until 7).map { LocalDate.of(2026, 11, 2).plusDays(it.toLong()) }
    private val prev = week.map { it.minusDays(7) }

    private fun pattern(onTime: List<Int>, prayed: List<Int> = List(7) { 1 }) = onTime.zip(prayed).map { (o, p) ->
        when { o == 1 -> PrayerStatus.ON_TIME; p == 1 -> PrayerStatus.LATE; else -> PrayerStatus.MISSED }
    }

    private fun sample(): TrackerData {
        val thisWeek = mapOf(
            Prayer.FAJR to pattern(listOf(1, 1, 0, 0, 1, 0, 1), listOf(1, 1, 1, 0, 1, 1, 1)),
            Prayer.DHUHR to pattern(listOf(1, 1, 1, 1, 1, 0, 1)), Prayer.ASR to pattern(listOf(1, 1, 1, 1, 1, 1, 0)),
            Prayer.MAGHRIB to pattern(List(7) { 1 }), Prayer.ISHA to pattern(listOf(0, 1, 1, 1, 1, 1, 1)),
        )
        val lastWeek = mapOf(
            Prayer.FAJR to pattern(List(7) { 1 }), Prayer.DHUHR to pattern(listOf(1, 1, 1, 0, 1, 1, 1)),
            Prayer.ASR to pattern(listOf(1, 0, 1, 0, 1, 1, 1)), Prayer.MAGHRIB to pattern(List(7) { 1 }), Prayer.ISHA to pattern(listOf(1, 1, 1, 1, 0, 1, 1)),
        )
        val out = mutableMapOf<LocalDate, Map<Prayer, PrayerStatus>>()
        week.forEachIndexed { i, d -> out[d] = thisWeek.mapValues { it.value[i] } }
        prev.forEachIndexed { i, d -> out[d] = lastWeek.mapValues { it.value[i] } }
        out[today] = mapOf(Prayer.FAJR to PrayerStatus.ON_TIME, Prayer.DHUHR to PrayerStatus.ON_TIME)
        return out
    }

    private fun logs(): List<NaflLog> {
        val quran = listOf(6, 5, 7, 0, 0, 0, 6)
        val dhikr = listOf(600, 600, 580, 580, 580, 580, 580)
        val prevDhikr = listOf(610, 610, 610, 610, 610, 600, 600)
        return week.indices.flatMap { i ->
            listOfNotNull(
                NaflLog(week[i].toString(), "quran", if (quran[i] > 0) 1 else 0, quran[i]).takeIf { quran[i] > 0 },
                NaflLog(week[i].toString(), "dhikr", dhikr[i]), NaflLog(prev[i].toString(), "dhikr", prevDhikr[i]),
                NaflLog(prev[i].toString(), "quran", 1, 6).takeIf { i < 6 },
                NaflLog(week[i].toString(), "maghrib_after", 1), NaflLog(prev[i].toString(), "maghrib_after", 1), NaflLog(prev[i].toString(), "fajr_before", 1),
            )
        }
    }

    private fun input(passed: List<Prayer> = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB), muted: Set<String> = emptySet()) = InsightsInput(
        sample(), NaflDefaults.items, logs(), week.map { QadaEntry(prayer = "DAY", count = 3, date = it.toString()) } + QadaEntry(prayer = "DAY", count = 2, date = prev[0].toString()),
        today, passed, qadaToday = 3, qadaYesterdaySameTime = 6, qadaRemaining = 12_265, qadaGoal = 5, muted = muted,
    )

    @Test fun fardCombinedPerPrayerStrongestWeakest() {
        val r = InsightsEngine.build(input())
        assertEquals(week.first(), r.weekStart); assertEquals(week.last(), r.weekEnd)
        assertEquals(29, r.fardWeek.onTime); assertEquals(34, r.fardWeek.prayed); assertEquals(35, r.fardWeek.due); assertEquals(1, r.fardWeek.missed)
        assertEquals(31, r.fardPrev.onTime)
        assertEquals(Prayer.MAGHRIB, r.strongest!!.prayer)
        assertEquals(Prayer.FAJR, r.weakest!!.prayer); assertEquals(-3, r.weakest!!.onTimeDelta)
        assertEquals(listOf(true, true, false, false, true, false, true), r.perPrayer.first { it.prayer == Prayer.FAJR }.onTimeByDay)
        assertEquals(listOf(4, 5, 4, 4, 5, 3, 4), r.dailyOnTime.map { it.value })
        assertEquals(5, r.weeklyOnTime.size); assertEquals(29, r.weeklyOnTime.last().value)
    }

    @Test fun yesterdayComparesOnlyPrayersAlreadyDue() {
        val r = InsightsEngine.build(input(passed = listOf(Prayer.FAJR, Prayer.DHUHR)))
        assertEquals(2, r.fardToday.due); assertEquals(2, r.fardToday.onTime)
        assertEquals(2, r.fardYesterdaySameTime.due) // Sun 8 Nov: Fajr + Dhuhr only, never the evening prayers
        assertEquals(0, r.changes.first().vsYesterday)
        // Untimed counts wait until Isha instead of showing a mid-day "drop".
        assertNull(r.changes.first { it.label == "Dhikr" }.vsYesterday)
        assertEquals(-3, r.changes.first { it.label == "Qada" }.vsYesterday)
        val night = InsightsEngine.build(input(passed = Prayer.obligatory))
        assertEquals(-580, night.changes.first { it.label == "Dhikr" }.vsYesterday)
    }

    @Test fun glanceHealthAndSteadyDhikr() {
        val r = InsightsEngine.build(input())
        val g = r.glance.associateBy { it.id }
        assertEquals("29/35", g.getValue("fard").value)
        assertEquals(-2, g.getValue("fard").delta)
        assertTrue(g.getValue("dhikr").steady) // 4,250 → 4,100 is within ±5%
        assertEquals(Health.GOOD, g.getValue("dhikr").health)
        assertEquals("4/7 days", g.getValue("quran").value)
        assertEquals(Health.ATTENTION, g.getValue("quran").health) // Thu–Sat without reading
        assertEquals("No reading Thu–Sat", r.scorecards.first { it.id == "quran" }.note)
        assertEquals(5, r.scorecards.size)
        assertEquals("Best week in 5", r.scorecards.first { it.id == "qada" }.note)
    }

    @Test fun coachPraisesFirstThenAtMostThreeConcreteSuggestions() {
        val c = InsightsEngine.build(input()).coach
        assertTrue(c.praise, c.praise.startsWith("Alhamdulillah, a strong week. You prayed 34 of 35 fard."))
        assertTrue(c.praise, c.praise.contains("Maghrib was on time all 7 days"))
        assertTrue(c.suggestions.size <= InsightsEngine.MAX_SUGGESTIONS)
        val first = c.suggestions.first()
        assertEquals("ontime:FAJR", first.id); assertEquals(CoachAction.IQAMAH_ALARM, first.action); assertEquals(Prayer.FAJR, first.prayer)
        assertTrue(first.body, first.body.contains("down from 7"))
        assertTrue(c.suggestions.any { it.id == "missed" && it.action == CoachAction.QADA })
        assertTrue(c.suggestions.any { it.id == "quran" && it.body.contains("Maghrib") })
        assertEquals(InsightsEngine.QUOTE_SOURCE, c.quoteSource)
    }

    @Test fun mutedSuggestionsAreSkipped() {
        val c = InsightsEngine.build(input(muted = setOf("ontime:FAJR", "missed"))).coach
        assertFalse(c.suggestions.any { it.id == "ontime:FAJR" || it.id == "missed" })
        assertNotEquals(0, c.suggestions.size)
    }

    @Test fun newUserGetsAWelcomeNotAWarning() {
        val r = InsightsEngine.build(InsightsInput(emptyMap(), emptyList(), emptyList(), emptyList(), today, listOf(Prayer.FAJR)))
        assertFalse(r.hasData)
        assertTrue(r.glance.all { it.health == Health.NO_DATA })
        assertTrue(r.coach.praise.startsWith("Welcome"))
        assertEquals("start_tracking", r.coach.suggestions.first().id)
        assertNull(r.strongest); assertNull(r.weakest)
        assertEquals(0, r.fardWeek.due)
    }
}
