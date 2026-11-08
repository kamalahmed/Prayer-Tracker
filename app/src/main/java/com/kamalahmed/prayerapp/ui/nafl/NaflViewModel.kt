package com.kamalahmed.prayerapp.ui.nafl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.core.safety.AppErrors
import com.kamalahmed.prayerapp.core.safety.CrashLog
import com.kamalahmed.prayerapp.core.safety.safeHandler
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.tracker.Stats
import com.kamalahmed.prayerapp.tracker.StatsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

data class NaflUi(val items: List<NaflItem>, val logs: List<NaflLog>, val reminders: List<QuranReminder>,
    val period: StatsPeriod, val anchor: LocalDate, val day: LocalDate, val today: LocalDate, val report: NaflReport)

@HiltViewModel
class NaflViewModel @Inject constructor(private val repo: NaflRepository, private val scheduler: QuranReminders) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.MONTH)
    private val anchor = MutableStateFlow<LocalDate?>(null)
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    private val edits = Mutex()
    private val today = flow { while (true) { emit(LocalDate.now()); delay(30_000) } }.distinctUntilChanged()
    private val data = combine(repo.items, repo.logs, repo.reminders) { i, l, r -> Triple(i, l, r) }
    private val dates = combine(today, selectedDay, anchor, period) { t, d, a, p -> Triple(t, (d ?: t).coerceAtMost(t), p to (a ?: t)) }
    private val reload = MutableStateFlow(0)
    private val failed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = failed.asStateFlow()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<NaflUi?> = reload.flatMapLatest {
        failed.value = false
        combine(data, dates) { (i, l, r), (t, d, pa) ->
            val ui: NaflUi? = NaflUi(i, l, r, pa.first, pa.second, d, t, NaflStats.report(i, l, pa.first, pa.second, t))
            ui
        }.catch { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            CrashLog.nonFatal("NaflViewModel", "Could not load Nafl tracker", e)
            failed.value = true
            AppErrors.report("Could not load your Nafl tracker. Tap Retry; your saved history has not been deleted.")
            emit(null)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun retry() = io { repo.ensureDefaults(); reload.value += 1 }
    init { io { repo.ensureDefaults() } }
    private fun io(block: suspend () -> Unit) = viewModelScope.launch(safeHandler("NaflViewModel")) {
        try { edits.withLock { block() } }
        catch (e: IllegalArgumentException) { AppErrors.report(e.message ?: "Please check the entered value") }
    }
    fun period(p: StatsPeriod) { period.value = p; anchor.value = null }
    fun shift(by: Long) { anchor.value = Stats.shift(period.value, anchor.value ?: LocalDate.now(), by).coerceAtMost(LocalDate.now()) }
    fun day(d: LocalDate) { selectedDay.value = d.coerceAtMost(LocalDate.now()) }
    fun log(item: NaflItem, count: Int, pages: Int, minutes: Int) {
        val date = state.value?.day ?: LocalDate.now()
        io { repo.saveLog(date, item.id, count, pages, minutes) }
    }
    fun item(item: NaflItem) = io { repo.saveItem(item) }
    fun add(name: String) = io { repo.addItem(name) }
    fun reminder(r: QuranReminder) = io { repo.saveReminder(r); scheduler.reschedule() }
    fun removeReminder(id: Long) = io { scheduler.remove(id) }
}
