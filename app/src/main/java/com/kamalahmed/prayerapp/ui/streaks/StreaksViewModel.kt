package com.kamalahmed.prayerapp.ui.streaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.data.prayer.PrayerRepository
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.StreakCalculator
import com.kamalahmed.prayerapp.tracker.StreakStats
import com.kamalahmed.prayerapp.tracker.TrackerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import com.kamalahmed.prayerapp.core.safety.reportErrors
import javax.inject.Inject

data class StreaksUi(
    val today: LocalDate,
    val todayStatus: Map<Prayer, PrayerStatus>,
    val passed: List<Prayer>,
    val stats: StreakStats,
)

@HiltViewModel
class StreaksViewModel @Inject constructor(
    private val tracker: TrackerRepository,
    prayers: PrayerRepository,
) : ViewModel() {

    val state: StateFlow<StreaksUi?> = combine(tracker.all, prayers.schedule) { data, sched ->
        val now = ZonedDateTime.now(sched.zone)
        val today = now.toLocalDate()
        val passed = sched.passedToday(now)
        StreaksUi(today, data[today].orEmpty(), passed, StreakCalculator.compute(data, today, passed))
    }.reportErrors("StreaksViewModel", "Could not load your prayer tracker").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val period = kotlinx.coroutines.flow.MutableStateFlow(com.kamalahmed.prayerapp.tracker.StatsPeriod.WEEK)
    val anchor = kotlinx.coroutines.flow.MutableStateFlow<LocalDate?>(null)

    val report: StateFlow<com.kamalahmed.prayerapp.tracker.PeriodReport?> =
        combine(tracker.all, prayers.schedule, period, anchor) { data, sched, per, anc ->
            val now = ZonedDateTime.now(sched.zone)
            val today = now.toLocalDate()
            com.kamalahmed.prayerapp.tracker.Stats.report(data, per, anc ?: today, today, sched.passedToday(now))
        }.reportErrors("StreaksViewModel", "Could not load your prayer tracker").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val streakHistory: StateFlow<List<com.kamalahmed.prayerapp.tracker.StreakRun>> =
        combine(tracker.all, prayers.schedule) { data, _ -> com.kamalahmed.prayerapp.tracker.Stats.streakHistory(data) }
            .reportErrors("StreaksViewModel", "Could not load your prayer tracker").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPeriod(p: com.kamalahmed.prayerapp.tracker.StatsPeriod) { period.value = p; anchor.value = null }
    fun shift(by: Long) {
        val today = state.value?.today ?: LocalDate.now()
        val next = com.kamalahmed.prayerapp.tracker.Stats.shift(period.value, anchor.value ?: today, by)
        anchor.value = if (next.isAfter(today)) null else next
    }
    fun set(date: LocalDate, p: Prayer, status: PrayerStatus?) = viewModelScope.launch(com.kamalahmed.prayerapp.core.safety.safeHandler("StreaksViewModel")) { tracker.set(date, p, status) }

    val history: StateFlow<com.kamalahmed.prayerapp.tracker.TrackerData> =
        tracker.all.reportErrors("StreaksViewModel", "Could not load your prayer tracker").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggle(p: Prayer) = viewModelScope.launch(com.kamalahmed.prayerapp.core.safety.safeHandler("StreaksViewModel")) { tracker.togglePrayed(state.value?.today ?: LocalDate.now(), p) }
    fun cycle(date: LocalDate, p: Prayer) = viewModelScope.launch(com.kamalahmed.prayerapp.core.safety.safeHandler("StreaksViewModel")) { tracker.cycle(date, p) }
}
