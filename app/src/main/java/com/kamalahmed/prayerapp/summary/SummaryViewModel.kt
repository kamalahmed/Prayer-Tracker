package com.kamalahmed.prayerapp.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.safety.*
import com.kamalahmed.prayerapp.data.prayer.PrayerRepository
import com.kamalahmed.prayerapp.data.settings.*
import com.kamalahmed.prayerapp.tracker.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import javax.inject.Inject

data class SummaryUi(val kind: SummaryKind,val today: LocalDate,val report: WorshipReport,val previous: WorshipReport?,val statuses: Map<Prayer,PrayerStatus>,val settings: AppSettings)

@HiltViewModel
class SummaryViewModel @Inject constructor(source: SummarySource,prayers: PrayerRepository,private val settings: SettingsRepository,private val saved: SavedStateHandle): ViewModel() {
    private val kind=saved.getStateFlow("summaryKind",SummaryKind.DAY.name)
    private val date=saved.getStateFlow<String?>("summaryDate",null)
    private val selection=combine(kind,date) { k,d -> (SummaryKind.entries.firstOrNull { it.name==k } ?: SummaryKind.DAY) to d }
    private val clock=flow { while(true) { emit(ZonedDateTime.now());delay(30_000) } }
    private val reload=MutableStateFlow(0)
    val error=MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SummaryUi?> = reload.flatMapLatest {
        error.value=null
        combine(source.history,prayers.schedule,settings.settings,selection,clock) { history,schedule,s,(kind,chosen),now ->
            val today=now.toLocalDate()
            val default=if(kind==SummaryKind.DAY) today else Stats.range(StatsPeriod.WEEK,today).first.minusWeeks(1)
            val anchor=(chosen?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: default).coerceAtMost(today)
            val (start,end)=if(kind==SummaryKind.DAY) anchor to anchor else Stats.range(StatsPeriod.WEEK,anchor)
            val prayerDay=schedule.day(today)
            val passed=Prayer.obligatory.filter { prayerDay[it].toInstant()<=now.toInstant() }
            val report=history.report(start,end,today,passed)
            val shift=if(kind==SummaryKind.DAY) 1L else 7L
            val previous=if(end<today) history.report(start.minusDays(shift),end.minusDays(shift),today,passed).takeIf { r ->
                r.fard.due>0 || r.days.any { it.sunnah>0 || it.otherPrayers>0 || it.otherWorship>0 || it.dhikr>0 || it.quran || it.quranPages>0 || it.qada>0 }
            } else null
            SummaryUi(kind,today,report,previous,history.tracker[start].orEmpty(),s) as SummaryUi?
        }.catch { e ->
            if(e is CancellationException) throw e
            CrashLog.nonFatal("Summaries","Read failed",e);error.value="Could not load summaries. Your saved logs are unchanged.";emit(null)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    fun retry() { reload.value+=1 }
    fun open(request: SummaryRequest) { saved["summaryKind"]=request.kind.name;saved["summaryDate"]=request.anchor.toString() }
    fun kind(value: SummaryKind) { saved["summaryKind"]=value.name;saved["summaryDate"]=null }
    fun shift(delta: Long) { state.value?.let { val days=if(it.kind==SummaryKind.DAY) delta else delta*7;open(SummaryRequest(it.kind,it.report.start.plusDays(days).coerceAtMost(it.today))) } }
    fun today() { state.value?.let { open(SummaryRequest(it.kind,it.today)) } }
    fun notify(kind: SummaryKind,enabled: Boolean)=viewModelScope.launch(safeHandler("Summary settings")) { settings.update { if(kind==SummaryKind.DAY) it.copy(summaryDaily=enabled) else it.copy(summaryWeekly=enabled) } }
    fun time(kind: SummaryKind,minute: Int)=viewModelScope.launch(safeHandler("Summary time")) { settings.update { if(kind==SummaryKind.DAY) it.copy(summaryDailyMinute=minute.coerceIn(0,1439)) else it.copy(summaryWeeklyMinute=minute.coerceIn(0,1439)) } }
}
