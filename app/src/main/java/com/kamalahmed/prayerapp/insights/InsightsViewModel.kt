package com.kamalahmed.prayerapp.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.safety.CrashLog
import com.kamalahmed.prayerapp.data.prayer.PrayerRepository
import com.kamalahmed.prayerapp.qada.QadaRepository
import com.kamalahmed.prayerapp.qada.QadaStats
import com.kamalahmed.prayerapp.summary.SummarySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime
import javax.inject.Inject

data class InsightsUi(val report: InsightsReport?, val coachHidden: Boolean, val error: String? = null)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    source: SummarySource,
    prayers: PrayerRepository,
    private val qada: QadaRepository,
    private val prefs: InsightsPrefs,
) : ViewModel() {
    private val clock = flow { while (true) { emit(ZonedDateTime.now()); delay(60_000) } }
    private val qadaState = combine(qada.debt, qada.totals, qada.dayTotals, qada.config) { d, t, days, c -> QadaStats.report(d, t, days, c, java.time.LocalDate.now()) }

    val state: StateFlow<InsightsUi?> = combine(source.history, prayers.schedule, clock, prefs.coach, qadaState) { history, schedule, now, coach, q ->
        val today = now.toLocalDate()
        val day = schedule.day(today)
        val passed = Prayer.obligatory.filter { day[it].toInstant() <= now.toInstant() }
        val nowMs = now.toInstant().toEpochMilli()
        val input = InsightsInput(
            history.tracker, history.items, history.logs, history.qada, today, passed,
            qadaToday = q.today, qadaYesterdaySameTime = qada.countOnUntil(today.minusDays(1), nowMs - 86_400_000L),
            qadaRemaining = q.remaining, qadaGoal = q.goal, muted = coach.muted(today),
        )
        InsightsUi(InsightsEngine.build(input), coach.hidden) as InsightsUi?
    }.catch { e ->
        if (e is CancellationException) throw e
        CrashLog.nonFatal("Insights", "Build failed", e)
        emit(InsightsUi(null, false, "Could not build insights. Your saved logs are unchanged."))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun hideCoach(hidden: Boolean) = prefs.setHidden(hidden)
    fun notHelpful(ids: List<String>) = prefs.mute(ids)
}
