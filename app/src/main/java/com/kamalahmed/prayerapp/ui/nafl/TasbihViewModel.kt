package com.kamalahmed.prayerapp.ui.nafl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.core.safety.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

data class TasbihUi(val items: List<NaflItem>,val item: NaflItem?,val count: Int,val session: Int,val day: LocalDate)
@HiltViewModel
class TasbihViewModel @Inject constructor(private val repo: NaflRepository, savedState: androidx.lifecycle.SavedStateHandle) : ViewModel() {
    private val saved = savedState
    private val selected = saved.getStateFlow("counterId","istighfar")
    private val ledger = CounterSession()
    private val revision = MutableStateFlow(0)
    private val lock = Mutex()
    private val dates = flow { while (true) { emit(LocalDate.now()); delay(1000) } }.distinctUntilChanged()
    private val choice = combine(selected,dates,revision) { id,date,_ -> id to date }
    private val _feedback = MutableSharedFlow<Boolean>(extraBufferCapacity = 64)
    val feedback = _feedback.asSharedFlow()
    val state = combine(repo.items,repo.logs,choice) { items,logs,(id,date) ->
        val counters = items.filter { it.isCounter && it.enabled }
        val item = counters.firstOrNull { it.id == id } ?: counters.firstOrNull()
        TasbihUi(counters,item,logs.firstOrNull { it.date == date.toString() && it.itemId == item?.id }?.count ?: 0,
            item?.let { ledger.count(date.toString(),it.id) } ?: 0,date)
    }.catch { e ->
        if (e is CancellationException) throw e
        CrashLog.nonFatal("Tasbih","read counters",e)
        AppErrors.report("Could not load counters. Reopen this screen to retry; saved counts are unchanged.")
        emit(TasbihUi(emptyList(),null,0,0,LocalDate.now()))
    }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    init { viewModelScope.launch(safeHandler("Tasbih")) { repo.ensureDefaults() } }
    fun select(id: String?) { if (!id.isNullOrBlank()) saved["counterId"] = id }
    fun change(delta: Int, reset: Boolean = false) {
        val id = state.value?.item?.id ?: return
        viewModelScope.launch(safeHandler("Tasbih")) {
            lock.withLock {
                val date = LocalDate.now()
                val day = date.toString()
                val amount = if (reset) -ledger.count(day,id) else delta.coerceIn(-1,1)
                val result = repo.adjustCounter(id,amount,date)
                if (reset) ledger.clear(day,id) else ledger.record(day,id,result.after - result.before)
                revision.value += 1
                if (result.after > result.before) _feedback.emit(result.targetReached)
            }
        }
    }
}
