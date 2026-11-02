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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val history: StateFlow<com.kamalahmed.prayerapp.tracker.TrackerData> =
        tracker.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggle(p: Prayer) = viewModelScope.launch { tracker.togglePrayed(state.value?.today ?: LocalDate.now(), p) }
    fun cycle(date: LocalDate, p: Prayer) = viewModelScope.launch { tracker.cycle(date, p) }
}
