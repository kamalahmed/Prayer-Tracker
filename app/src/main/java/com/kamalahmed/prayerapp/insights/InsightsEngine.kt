package com.kamalahmed.prayerapp.insights

import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.nafl.NaflDefaults
import com.kamalahmed.prayerapp.nafl.NaflItem
import com.kamalahmed.prayerapp.nafl.NaflLog
import com.kamalahmed.prayerapp.qada.QadaEntry
import com.kamalahmed.prayerapp.summary.SummaryDay
import com.kamalahmed.prayerapp.summary.WorshipReport
import com.kamalahmed.prayerapp.summary.WorshipSummary
import com.kamalahmed.prayerapp.tracker.Counts
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.Stats
import com.kamalahmed.prayerapp.tracker.TrackerData
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Health { GOOD, ATTENTION, NO_DATA }

/** Where a coach suggestion's button leads. All are in-app; nothing is sent anywhere. */
enum class CoachAction { IQAMAH_ALARM, PRAYER_ALERTS, NAFL, QADA, STATS }

data class GlanceCheck(val id: String, val title: String, val value: String, val detail: String, val delta: Int?, val steady: Boolean, val health: Health)
data class PrayerInsight(val prayer: Prayer, val week: Counts, val prev: Counts, val onTimeByDay: List<Boolean?>) {
    val onTimeDelta: Int get() = week.onTime - prev.onTime
}
/** A "vs yesterday" / "vs last week" row. A null delta means "not comparable yet" (e.g. untimed counts mid-day). */
data class ChangeRow(val label: String, val todayText: String, val vsYesterday: Int?, val yesterdayNote: String?, val weekText: String, val vsWeek: Int?, val steady: Boolean = false)
data class TrendPoint(val label: String, val value: Int)
data class Scorecard(val id: String, val title: String, val value: String, val unit: String, val delta: Int?, val deltaUnit: String, val steady: Boolean, val trend: List<Int>, val health: Health, val note: String)
data class Suggestion(val id: String, val title: String, val body: String, val action: CoachAction?, val actionLabel: String?, val prayer: Prayer? = null)
data class Coach(val praise: String, val suggestions: List<Suggestion>, val quote: String, val quoteSource: String)

data class InsightsReport(
    val today: LocalDate,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val prevStart: LocalDate,
    val prevEnd: LocalDate,
    val hasData: Boolean,
    val glance: List<GlanceCheck>,
    val fardWeek: Counts,
    val fardPrev: Counts,
    val fardToday: Counts,
    val fardYesterdaySameTime: Counts,
    val perPrayer: List<PrayerInsight>,
    val strongest: PrayerInsight?,
    val weakest: PrayerInsight?,
    val dailyOnTime: List<TrendPoint>,
    val dailyQuranPages: List<TrendPoint>,
    val weeklyOnTime: List<TrendPoint>,
    val weeklySunnah: List<TrendPoint>,
    val sunnahPossiblePerWeek: Int,
    val changes: List<ChangeRow>,
    val scorecards: List<Scorecard>,
    val coach: Coach,
)

/** Everything the engine reads. Built from local Room data only. */
data class InsightsInput(
    val tracker: TrackerData,
    val items: List<NaflItem>,
    val logs: List<NaflLog>,
    /** Qada per-day totals (one synthetic entry per day). */
    val qada: List<QadaEntry>,
    val today: LocalDate,
    /** Fard prayers whose start time has passed today (same-time comparison with yesterday). */
    val passed: List<Prayer>,
    val qadaToday: Int = 0,
    val qadaYesterdaySameTime: Int = 0,
    val qadaRemaining: Int = 0,
    val qadaGoal: Int = 0,
    /** Suggestion ids the user marked "Not helpful" that are still muted. */
    val muted: Set<String> = emptySet(),
)

/**
 * On-device, rule-based insights and coach. Pure and deterministic: no network, no AI service, no account.
 * "This week" = the last 7 complete days (yesterday and the 6 before); "last week" = the 7 days before that.
 */
object InsightsEngine {
    const val QUOTE = "The most beloved deeds to Allah are those done consistently, even if small."
    const val QUOTE_SOURCE = "Bukhari & Muslim"
    /** Category falls below 80% of its 4-week average ⇒ needs attention. */
    const val DROP_RATIO = 0.8
    /** Changes within ±5% are "steady" (no up/down arrow). */
    const val STEADY_RATIO = 0.05
    const val MAX_SUGGESTIONS = 3

    private val sunnahPrayer = mapOf(
        "fajr_before" to Prayer.FAJR, "dhuhr_before" to Prayer.DHUHR, "dhuhr_after" to Prayer.DHUHR, "asr_before" to Prayer.ASR,
        "maghrib_after" to Prayer.MAGHRIB, "isha_after" to Prayer.ISHA, "witr" to Prayer.ISHA,
    )

    fun build(input: InsightsInput): InsightsReport {
        val today = input.today
        val weekEnd = today.minusDays(1); val weekStart = today.minusDays(7)
        val prevEnd = today.minusDays(8); val prevStart = today.minusDays(14)
        fun report(s: LocalDate, e: LocalDate): WorshipReport =
            WorshipSummary.build(input.tracker, input.items, input.logs, input.qada, s, e, today, input.passed)
        // Five 7-day windows ending yesterday: index 0 = this week, 4 = four weeks ago.
        val weeks = (0 until 5).map { i -> report(weekStart.minusDays(7L * i), weekEnd.minusDays(7L * i)) }
        val week = weeks[0]; val prev = weeks[1]
        val older = weeks.drop(1)
        val first = Stats.firstDay(input.tracker.filterKeys { it <= today }, today)
        val hasFard = input.tracker.keys.any { it <= today }

        // ---- Fard ----
        val perPrayer = Prayer.obligatory.map { p ->
            val wk = Stats.counts(input.tracker, weekStart, weekEnd, today, input.passed, first, p)
            val pv = Stats.counts(input.tracker, prevStart, prevEnd, today, input.passed, first, p)
            val days = (0 until 7).map { i ->
                val d = weekStart.plusDays(i.toLong())
                val c = Stats.dayCounts(input.tracker, d, today, input.passed, first, p)
                if (c.due == 0) null else c.onTime > 0
            }
            PrayerInsight(p, wk, pv, days)
        }
        val tracked = perPrayer.filter { it.week.due > 0 }
        val strongest = tracked.maxWithOrNull(compareBy<PrayerInsight> { it.week.onTime }.thenBy { -it.week.missed })
        val weakest = tracked.filter { it !== strongest }.minWithOrNull(compareBy<PrayerInsight> { it.week.onTime }.thenBy { it.onTimeDelta })
            ?.takeIf { strongest == null || it.week.onTime < strongest.week.onTime }
        val fardToday = Stats.dayCounts(input.tracker, today, today, input.passed, first)
        // Yesterday "by this time": only the prayers already due today are compared.
        val yesterday = today.minusDays(1)
        val fardYesterday = if (yesterday.isBefore(first)) Counts() else sameTime(input.tracker, yesterday, input.passed)

        // ---- Sunnah ----
        val defs = (NaflDefaults.items.associateBy { it.id } + input.items.associateBy { it.id })
        val sunnahDaily = defs.values.filter { it.baseline && it.enabled }.sumOf { it.rakaat }
        val sunnahPossible = sunnahDaily * 7
        fun sunnahAt(date: LocalDate, prayers: Collection<Prayer>): Int = input.logs.filter { it.date == date.toString() && it.count > 0 }
            .mapNotNull { l -> defs[l.itemId]?.takeIf { it.baseline && sunnahPrayer[it.id] in prayers } }.sumOf { it.rakaat }

        // ---- Health ----
        fun avg(f: (WorshipReport) -> Number) = older.map { f(it).toDouble() }.average()
        fun zeroRun(f: (SummaryDay) -> Boolean): Int { var best = 0; var n = 0; week.days.forEach { n = if (f(it)) 0 else n + 1; best = maxOf(best, n) }; return best }
        fun health(value: Double, avg4: Double, anyEver: Boolean, run: Int = 0): Health = when {
            !anyEver -> Health.NO_DATA
            avg4 > 0 && value < avg4 * DROP_RATIO -> Health.ATTENTION
            avg4 > 0 && run >= 3 -> Health.ATTENTION
            else -> Health.GOOD
        }
        fun steady(now: Number, before: Number) = before.toDouble() > 0 && abs(now.toDouble() - before.toDouble()) <= before.toDouble() * STEADY_RATIO
        val fardHealth = when {
            week.fard.due == 0 && weeks.all { it.fard.due == 0 } -> Health.NO_DATA
            week.fard.missed >= 3 -> Health.ATTENTION
            else -> health(week.fard.onTime.toDouble(), avg { it.fard.onTime }, true)
        }
        val sunnahHealth = health(week.sunnah.toDouble(), avg { it.sunnah }, weeks.any { it.sunnah > 0 }, zeroRun { it.sunnah > 0 })
        val dhikrHealth = health(week.dhikr.toDouble(), avg { it.dhikr }, weeks.any { it.dhikr > 0 }, zeroRun { it.dhikr > 0 })
        val quranHealth = health(week.quranDays.toDouble(), avg { it.quranDays }, weeks.any { it.quranDays > 0 }, zeroRun { it.quran })
        val qadaHealth = when {
            weeks.all { it.qada == 0L } && input.qadaRemaining == 0 -> Health.NO_DATA
            else -> health(week.qada.toDouble(), avg { it.qada }, true)
        }

        val glance = listOf(
            GlanceCheck("fard", "On-time fard", if (week.fard.due == 0) "—" else "${week.fard.onTime}/${week.fard.due}",
                if (week.fard.due == 0) "not tracked yet" else "${pct(week.fard.onTime, week.fard.due)}% on time",
                if (prev.fard.due > 0) week.fard.onTime - prev.fard.onTime else null, false, fardHealth),
            GlanceCheck("sunnah", "Sunnah kept", if (sunnahPossible == 0) "—" else "${week.sunnah}/$sunnahPossible", "rakaat",
                if (weeks[1].sunnah > 0 || week.sunnah > 0) week.sunnah - prev.sunnah else null, false, sunnahHealth),
            GlanceCheck("dhikr", "Dhikr steady", fmt(week.dhikr), "${week.days.count { it.dhikr > 0 }}/7 days",
                if (prev.dhikr > 0 || week.dhikr > 0) (week.dhikr - prev.dhikr).toInt() else null, steady(week.dhikr, prev.dhikr), dhikrHealth),
            GlanceCheck("quran", "Quran", "${week.quranDays}/7 days", "${week.quranPages} pages",
                if (prev.quranDays > 0 || week.quranDays > 0) week.quranDays - prev.quranDays else null, false, quranHealth),
        )

        // ---- Changes ----
        val allPassed = input.passed.containsAll(Prayer.obligatory)
        val todaySummary = report(today, today).days.firstOrNull()
        val ySummary = report(yesterday, yesterday).days.firstOrNull()
        val sunnahToday = sunnahAt(today, input.passed); val sunnahY = sunnahAt(yesterday, input.passed)
        val untimedNote = "compared after Isha"
        val changes = listOf(
            ChangeRow("On-time fard", "${fardToday.onTime} vs ${fardYesterday.onTime}", if (input.passed.isEmpty()) null else fardToday.onTime - fardYesterday.onTime, if (input.passed.isEmpty()) "before Fajr" else null,
                "${prev.fard.onTime} → ${week.fard.onTime}", if (prev.fard.due == 0) null else week.fard.onTime - prev.fard.onTime),
            ChangeRow("Sunnah rakaat", "$sunnahToday vs $sunnahY", if (input.passed.isEmpty()) null else sunnahToday - sunnahY, if (input.passed.isEmpty()) "before Fajr" else null,
                "${prev.sunnah} → ${week.sunnah}", week.sunnah - prev.sunnah),
            ChangeRow("Dhikr", "${fmt(todaySummary?.dhikr ?: 0)} vs ${fmt(ySummary?.dhikr ?: 0)}", if (allPassed) ((todaySummary?.dhikr ?: 0) - (ySummary?.dhikr ?: 0)).toInt() else null,
                if (allPassed) null else untimedNote, "${fmt(prev.dhikr)} → ${fmt(week.dhikr)}", (week.dhikr - prev.dhikr).toInt(), steady(week.dhikr, prev.dhikr)),
            ChangeRow("Quran pages", "${todaySummary?.quranPages ?: 0} vs ${ySummary?.quranPages ?: 0}", if (allPassed) ((todaySummary?.quranPages ?: 0) - (ySummary?.quranPages ?: 0)).toInt() else null,
                if (allPassed) null else untimedNote, "${prev.quranPages} → ${week.quranPages}", (week.quranPages - prev.quranPages).toInt()),
            ChangeRow("Qada", "${input.qadaToday} vs ${input.qadaYesterdaySameTime}", input.qadaToday - input.qadaYesterdaySameTime, null,
                "${prev.qada} → ${week.qada}", (week.qada - prev.qada).toInt()),
        )

        // ---- Trends ----
        val dayLabel = { d: LocalDate -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        val dailyOnTime = week.days.map { TrendPoint(dayLabel(it.date), it.fard.onTime) }
        val dailyQuran = week.days.map { TrendPoint(dayLabel(it.date), it.quranPages.toInt().coerceAtLeast(if (it.quran) 1 else 0)) }
        val weekLabel = { r: WorshipReport -> "${r.start.dayOfMonth} ${r.start.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" }
        val weeklyOnTime = weeks.reversed().map { TrendPoint(weekLabel(it), it.fard.onTime) }
        val weeklySunnah = weeks.reversed().map { TrendPoint(weekLabel(it), it.sunnah) }

        // ---- Scorecards ----
        val istighfar = week.counters.firstOrNull { it.item.id == "istighfar" }?.count ?: 0
        val istighfarDays = input.logs.count { it.itemId == "istighfar" && it.count > 0 && runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d in weekStart..weekEnd } == true }
        val mostMissedSunnah = defs.values.filter { it.baseline && it.enabled }.map { item ->
            item to (0 until 7).count { i -> input.logs.none { it.itemId == item.id && it.count > 0 && it.date == weekStart.plusDays(i.toLong()).toString() } }
        }.filter { it.second >= 3 }.maxByOrNull { it.second }
        val gap = longestGap(week.days.map { it.quran })
        val bestQadaWeek = weeks.maxOf { it.qada }
        val scorecards = listOf(
            Scorecard("fard", "Fard prayers", if (week.fard.due == 0) "—" else "${week.fard.prayed}/${week.fard.due}", "prayed · ${week.fard.onTime} on time",
                if (prev.fard.due == 0) null else week.fard.onTime - prev.fard.onTime, "on time", false, weeks.reversed().map { it.fard.onTime }, fardHealth,
                when {
                    week.fard.due == 0 -> "Mark prayers on the Prayers tab to see this."
                    strongest != null && strongest.week.onTime == strongest.week.due && strongest.week.due >= 7 -> "${strongest.prayer.displayName} on time every day"
                    weakest != null -> "${weakest.prayer.displayName} needs the most care (${weakest.week.onTime}/${weakest.week.due} on time)"
                    else -> "Consistent across all five prayers"
                }),
            Scorecard("sunnah", "Sunnah & nafl", if (sunnahPossible == 0) "${week.sunnah}" else "${week.sunnah}/$sunnahPossible",
                "Sunnah rakaat" + if (week.otherPrayers > 0) " · ${week.otherPrayers} other prayers" else "", week.sunnah - prev.sunnah, "rakaat", false,
                weeks.reversed().map { it.sunnah }, sunnahHealth,
                mostMissedSunnah?.let { "${it.first.name} missed ${it.second}×" } ?: if (week.sunnah > 0) "Kept up well this week" else "Log Sunnah on the Nafl tab"),
            Scorecard("dhikr", "Dhikr & istighfar", fmt(week.dhikr), if (istighfar > 0) "recitations · istighfar ${fmt(istighfar)} on $istighfarDays/7 days" else "recitations",
                (week.dhikr - prev.dhikr).toInt(), "", steady(week.dhikr, prev.dhikr), weeks.reversed().map { it.dhikr.toInt() }, dhikrHealth,
                when {
                    steady(week.dhikr, prev.dhikr) -> "Steady (within ±5% of last week)"
                    week.dhikr > prev.dhikr -> "More remembrance than last week"
                    week.dhikr == 0L -> "Try a tasbih counter after one prayer"
                    else -> "A little less than last week"
                }),
            Scorecard("quran", "Quran", "${week.quranPages}", "pages on ${week.quranDays} of 7 days", (week.quranPages - prev.quranPages).toInt(), "pages", false,
                weeks.reversed().map { it.quranPages.toInt() }, quranHealth,
                if (gap.second >= 2) "No reading ${dayLabel(week.days[gap.first].date)}–${dayLabel(week.days[gap.first + gap.second - 1].date)}" else "Read on ${week.quranDays} days"),
            Scorecard("qada", "Qada", "${week.qada}", "made up · ${fmt(input.qadaRemaining)} left", (week.qada - prev.qada).toInt(), "", false,
                weeks.reversed().map { it.qada.toInt() }, qadaHealth,
                if (week.qada > 0 && week.qada == bestQadaWeek && weeks.count { it.qada == bestQadaWeek } == 1) "Best week in 5" else if (week.qada > 0) "Keep the pace" else "Log make-up prayers on the Qada tab"),
        )

        val coach = coach(input, week, prev, weeks, perPrayer, strongest, weakest, mostMissedSunnah?.first, hasFard)
        val hasData = hasFard || input.logs.isNotEmpty() || input.qada.any { it.count > 0 }
        return InsightsReport(today, weekStart, weekEnd, prevStart, prevEnd, hasData, glance, week.fard, prev.fard, fardToday, fardYesterday, perPrayer,
            strongest, weakest, dailyOnTime, dailyQuran, weeklyOnTime, weeklySunnah, sunnahPossible, changes, scorecards, coach)
    }

    /** Yesterday's counts for exactly the prayers already due today (a half-finished day never reads as a drop). */
    fun sameTime(tracker: TrackerData, day: LocalDate, passed: List<Prayer>): Counts {
        val marks = tracker[day].orEmpty()
        var on = 0; var late = 0; var missed = 0
        passed.filter { it in Prayer.obligatory }.forEach { p ->
            when (marks[p]) { PrayerStatus.ON_TIME -> on++; PrayerStatus.LATE -> late++; else -> missed++ }
        }
        return Counts(on, late, missed, on + late + missed)
    }

    private fun coach(
        input: InsightsInput, week: WorshipReport, prev: WorshipReport, weeks: List<WorshipReport>, perPrayer: List<PrayerInsight>,
        strongest: PrayerInsight?, weakest: PrayerInsight?, missedSunnah: NaflItem?, hasFard: Boolean,
    ): Coach {
        val praise = buildString {
            when {
                !hasFard && input.logs.isEmpty() -> append("Welcome. Mark your prayers each day and your coach will reflect on your week here, privately on this phone.")
                week.fard.due == 0 -> append("May Allah make it easy. Keep marking your prayers; your weekly reflection appears after a few days.")
                week.fard.prayed == week.fard.due -> append("Alhamdulillah, every fard prayer was prayed this week (${week.fard.prayed} of ${week.fard.due}).")
                week.fard.prayed >= week.fard.due - 1 -> append("Alhamdulillah, a strong week. You prayed ${week.fard.prayed} of ${week.fard.due} fard.")
                else -> append("May Allah make it easy. You prayed ${week.fard.prayed} of ${week.fard.due} fard this week, and every prayer counts.")
            }
            val perfect = perPrayer.firstOrNull { it.week.due >= 7 && it.week.onTime == it.week.due }
            if (perfect != null && week.fard.due > 0) append(" ${perfect.prayer.displayName} was on time all 7 days.")
            val bests = listOfNotNull(
                "qada pace".takeIf { week.qada > 0 && weeks.drop(1).all { it.qada < week.qada } },
                "Quran reading".takeIf { week.quranPages > 0 && weeks.drop(1).all { it.quranPages < week.quranPages } },
                "Sunnah".takeIf { week.sunnah > 0 && weeks.drop(1).all { it.sunnah < week.sunnah } },
            )
            if (bests.isNotEmpty()) append(" Your ${bests.first()} was your best in 5 weeks.")
        }
        val out = mutableListOf<Suggestion>()
        fun add(s: Suggestion) { if (out.size < MAX_SUGGESTIONS && s.id !in input.muted && out.none { it.id == s.id }) out += s }
        if (!hasFard) add(Suggestion("start_tracking", "Mark today's prayers", "Tap each prayer once you've prayed it. A few days are enough for real insights.", CoachAction.STATS, "Open today's tracker"))
        weakest?.let { w ->
            val dropped = w.prev.due > 0 && w.onTimeDelta <= -2
            val low = w.week.due >= 5 && w.week.onTime * 10 < w.week.due * 6
            if (dropped || low) add(Suggestion("ontime:${w.prayer.name}", "Bring ${w.prayer.displayName} back on time",
                (if (dropped) "On time on ${w.week.onTime} days, down from ${w.prev.onTime}. " else "On time on ${w.week.onTime} of ${w.week.due} days. ") +
                    "An alarm before ${w.prayer.displayName}'s iqamah often helps.",
                CoachAction.IQAMAH_ALARM, "Set ${w.prayer.displayName} alarm", w.prayer))
        }
        if (week.fard.missed > 0) add(Suggestion("missed", "Make up ${if (week.fard.missed == 1) "the missed prayer" else "${week.fard.missed} missed prayers"}",
            "Log ${if (week.fard.missed == 1) "it" else "them"} as qada once prayed, so nothing is left behind.", CoachAction.QADA, "Open Qada"))
        val quranBefore = weeks.drop(1).any { it.quranDays > 0 }
        if ((quranBefore || week.quranDays > 0) && week.quranDays < 5) {
            val anchor = strongest?.prayer?.displayName
            add(Suggestion("quran", "One page after ${anchor ?: "a prayer"}",
                (if (prev.quranDays > week.quranDays) "Quran on ${week.quranDays} days, down from ${prev.quranDays}. " else "Quran on ${week.quranDays} of 7 days. ") +
                    (if (anchor != null) "You're steady at $anchor, so read one page right after it." else "Pair one page with a prayer you never miss."),
                CoachAction.NAFL, "Quran reminders"))
        }
        if (prev.sunnah > 0 && week.sunnah < prev.sunnah * DROP_RATIO) {
            val linked = missedSunnah?.let { sunnahPrayer[it.id] }
            add(Suggestion("sunnah", if (missedSunnah != null) "Return to the ${missedSunnah.name.lowercase()} Sunnah" else "Return to your Sunnah",
                "Sunnah rakaat fell from ${prev.sunnah} to ${week.sunnah}. " +
                    if (linked != null && linked == weakest?.prayer) "It was missed on the same days as late ${linked.displayName}. Fix one and you fix both." else "Pray it right beside the fard, before you get up.",
                CoachAction.NAFL, "Open Nafl"))
        }
        if (weeks.drop(1).any { it.dhikr > 0 } && week.days.count { it.dhikr > 0 } <= 3) add(Suggestion("dhikr", "Keep a small daily dhikr",
            "Even 33 SubhanAllah after one prayer keeps the habit alive.", CoachAction.NAFL, "Open counters"))
        if (input.qadaRemaining > 0 && input.qadaGoal == 0) add(Suggestion("qada_goal", "Set a small daily qada goal",
            "A goal of a few prayers a day turns ${fmt(input.qadaRemaining)} into a steady, reachable plan.", CoachAction.QADA, "Open Qada"))
        return Coach(praise, out, QUOTE, QUOTE_SOURCE)
    }

    /** (start index, length) of the longest run of false values. */
    fun longestGap(days: List<Boolean>): Pair<Int, Int> {
        var best = 0 to 0; var start = -1
        days.forEachIndexed { i, v ->
            if (!v) { if (start < 0) start = i; if (i - start + 1 > best.second) best = start to (i - start + 1) } else start = -1
        }
        return best
    }

    fun pct(n: Int, d: Int) = if (d == 0) 0 else (n * 100.0 / d).roundToInt()
    fun fmt(n: Long) = "%,d".format(Locale.getDefault(), n)
    fun fmt(n: Int) = "%,d".format(Locale.getDefault(), n)
}
