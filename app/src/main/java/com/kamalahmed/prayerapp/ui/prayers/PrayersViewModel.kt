package com.kamalahmed.prayerapp.ui.prayers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.core.hijri.HijriCalendar
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerEvent
import com.kamalahmed.prayerapp.data.prayer.PrayerRepository
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.ui.common.secondTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject

data class PrayersUiState(
    val settings: AppSettings,
    val now: ZonedDateTime,
    val hijri: String,
    val next: PrayerEvent,
    val previous: PrayerEvent,
    /** 0..1 progress from previous to next prayer. */
    val progress: Float,
    val remaining: Duration,
    /** Non-null when within the count-up window after a prayer started. */
    val countUp: Duration?,
    val iqamaRemaining: Duration?,
    val others: List<PrayerEvent>,
)

@HiltViewModel
class PrayersViewModel @Inject constructor(
    settingsRepo: SettingsRepository,
    prayerRepo: PrayerRepository,
) : ViewModel() {

    val state: StateFlow<PrayersUiState?> = combine(settingsRepo.settings, prayerRepo.schedule, secondTicker()) { s, sched, ms ->
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), s.zoneOrDefault())
        val next = sched.next(now)
        val prev = sched.current(now)
        val total = Duration.between(prev.time, next.time).seconds.coerceAtLeast(1)
        val elapsed = Duration.between(prev.time, now)
        val today = sched.day(now.toLocalDate())
        val hijri = HijriCalendar.at(now, today[Prayer.MAGHRIB], s.hijriAdjust, s.autoIncrementHijri)
        val countUp = elapsed.takeIf { s.countUpMinutes > 0 && it.toMinutes() < s.countUpMinutes }
        val iqama = if (s.iqamaEnabled) {
            val end = prev.time.plusMinutes(s.iqama(prev.prayer).toLong())
            Duration.between(now, end).takeIf { !it.isNegative && !it.isZero }
        } else null
        PrayersUiState(
            settings = s,
            now = now,
            hijri = "${hijri.day} ${hijri.monthName} ${hijri.year}",
            next = next,
            previous = prev,
            progress = (elapsed.seconds.toFloat() / total).coerceIn(0f, 1f),
            remaining = Duration.between(now, next.time),
            countUp = countUp,
            iqamaRemaining = iqama,
            others = Prayer.obligatory.filter { it != next.prayer }.map { PrayerEvent(it, today[it]) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

fun AppSettings.zoneOrDefault(): java.time.ZoneId =
    runCatching { java.time.ZoneId.of(timeZoneId) }.getOrDefault(java.time.ZoneId.systemDefault())
