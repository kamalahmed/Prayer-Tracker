package com.kamalahmed.prayerapp.ui.qada

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.core.safety.AppErrors
import com.kamalahmed.prayerapp.core.safety.reportErrors
import com.kamalahmed.prayerapp.core.safety.safeHandler
import com.kamalahmed.prayerapp.qada.QadaConfig
import com.kamalahmed.prayerapp.qada.QadaFeedback
import com.kamalahmed.prayerapp.qada.QadaHistory
import com.kamalahmed.prayerapp.qada.QadaHistoryRow
import com.kamalahmed.prayerapp.qada.QadaPrayer
import com.kamalahmed.prayerapp.qada.QadaReminderWorker
import com.kamalahmed.prayerapp.qada.QadaReport
import com.kamalahmed.prayerapp.qada.QadaRepository
import com.kamalahmed.prayerapp.qada.QadaStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Everything the Qada page shows, built from aggregate queries only (per-prayer totals, per-day totals and a
 * 14-day per-prayer breakdown), so 18,000+ logged prayers never load row by row.
 */
data class QadaUi(
    val config: QadaConfig,
    val report: QadaReport,
    val history: List<QadaHistoryRow>,
    /** Made-up prayers logged today, per prayer (drives the "done today ×N" highlight). */
    val todayByPrayer: Map<QadaPrayer, Int>,
    val today: LocalDate,
    val canUndo: Boolean,
    val haptics: Boolean,
)

/** The latest logging action, so the snackbar's Undo removes exactly what that tap added. */
data class QadaAction(val ids: List<Long>, val label: String)

@HiltViewModel
class QadaViewModel @Inject constructor(
    private val repo: QadaRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val startDay = LocalDate.now()
    private val lastAction = MutableStateFlow<QadaAction?>(null)
    private val haptics = MutableStateFlow(QadaFeedback.enabled(context))
    val action: StateFlow<QadaAction?> = lastAction.asStateFlow()

    private val parts = combine(repo.debt, repo.totals, repo.dayTotals, repo.config) { d, t, days, c -> Parts(d, t, days, c) }

    val state: StateFlow<QadaUi?> = combine(parts, repo.breakdown(QadaHistory.dayWindowStart(startDay).minusDays(1)), lastAction, haptics) { p, b, a, h ->
        val today = LocalDate.now()
        QadaUi(
            p.config, QadaStats.report(p.debt, p.totals, p.days, p.config, today), QadaHistory.build(p.days, b, today),
            b[today].orEmpty(), today, a != null || p.days.values.any { it > 0 }, h,
        )
    }.flowOn(Dispatchers.Default).reportErrors("QadaViewModel", "Could not load your qada tracker")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private data class Parts(val debt: List<com.kamalahmed.prayerapp.qada.QadaDebt>, val totals: Map<QadaPrayer, Int>, val days: Map<LocalDate, Int>, val config: QadaConfig)

    /** Serialises writes so a fast Undo never races the insert it is undoing. */
    private val writes = Mutex()
    private fun io(block: suspend () -> Unit) = viewModelScope.launch(safeHandler("QadaViewModel")) { writes.withLock { block() } }

    fun log(p: QadaPrayer, n: Int) = io {
        val count = n.coerceIn(1, QadaRepository.MAX_BATCH)
        lastAction.value = QadaAction(listOf(repo.log(p, count)), "${p.displayName} +$count")
    }
    /** One of each fard prayer: a full day made up (one Undo removes all five). */
    fun logDay() = io { lastAction.value = QadaAction(repo.logAll(QadaPrayer.fard.map { it to 1 }), "Full day") }
    /** Undo the latest action (snackbar / "Undo last"); falls back to the newest entry after a restart. */
    fun undo() = io {
        val a = lastAction.value
        lastAction.value = null
        val removed = if (a != null) repo.deleteIds(a.ids) > 0 else repo.undoLast()
        if (!removed) AppErrors.report("Nothing to undo")
    }
    fun setDayCount(date: LocalDate, p: QadaPrayer, target: Int) = io {
        repo.setDayCount(date, p, target.coerceAtLeast(0))
        lastAction.value = null // edits are explicit; the snackbar Undo must not reach into an edited day
    }
    fun setHaptics(on: Boolean) { QadaFeedback.setEnabled(context, on); haptics.value = on }
    fun saveDebt(counts: Map<QadaPrayer, Int>, witr: Boolean) = io { repo.setDebt(counts, witr) }
    fun reset() = io { repo.resetAll(); lastAction.value = null; QadaReminderWorker.schedule(context, QadaConfig()) }
    fun setGoal(goal: Int) = updateConfig { it.copy(dailyGoal = goal.coerceIn(0, 100)) }
    fun setReminder(on: Boolean) = updateConfig { it.copy(reminder = on) }
    fun setReminderMinute(m: Int) = updateConfig { it.copy(reminderMinute = m.coerceIn(0, 24 * 60 - 1)) }

    private fun updateConfig(f: (QadaConfig) -> QadaConfig) = io {
        repo.updateConfig(f)
        QadaReminderWorker.schedule(context, repo.currentConfig())
    }
}
