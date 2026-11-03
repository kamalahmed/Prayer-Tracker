package com.kamalahmed.prayerapp.ui.times

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.core.hijri.HijriCalendar
import com.kamalahmed.prayerapp.core.hijri.HolidayOccurrence
import com.kamalahmed.prayerapp.core.hijri.IslamicHolidays
import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerDay
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.PrayerRepository
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.ui.prayers.zoneOrDefault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class TimesUiState(
    val settings: AppSettings,
    val date: LocalDate,
    val day: PrayerDay,
    val hijri: String,
    val schedule: PrayerSchedule,
    val holidays: List<HolidayOccurrence>,
)

@HiltViewModel
class TimesViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    prayerRepo: PrayerRepository,
) : ViewModel() {

    private val selected = MutableStateFlow<LocalDate?>(null)
    val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<TimesUiState?> = combine(settingsRepo.settings, prayerRepo.schedule, selected) { s, sched, sel ->
        val date = sel ?: LocalDate.now(s.zoneOrDefault())
        val h = HijriCalendar.of(date, s.hijriAdjust)
        TimesUiState(s, date, sched.day(date), h.format(), sched, IslamicHolidays.upcoming(LocalDate.now(), s.hijriAdjust, 12))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun shiftDay(delta: Long) {
        val cur = state.value?.date ?: LocalDate.now()
        selected.value = cur.plusDays(delta)
    }

    fun today() { selected.value = null }

    /** Cycle OFF -> SILENT -> BEEP -> ADHAN -> OFF. */
    fun cycleMode(p: Prayer) = viewModelScope.launch {
        settingsRepo.update { s ->
            val next = NotifyMode.entries[(s.mode(p).ordinal + 1) % NotifyMode.entries.size]
            s.copy(notifyModes = s.notifyModes + (p to next))
        }
    }
}
