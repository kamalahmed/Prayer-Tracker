package com.kamalahmed.prayerapp.ui.qada

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.core.safety.AppErrors
import com.kamalahmed.prayerapp.core.safety.reportErrors
import com.kamalahmed.prayerapp.core.safety.safeHandler
import com.kamalahmed.prayerapp.qada.QadaConfig
import com.kamalahmed.prayerapp.qada.QadaEntry
import com.kamalahmed.prayerapp.qada.QadaPrayer
import com.kamalahmed.prayerapp.qada.QadaReminderWorker
import com.kamalahmed.prayerapp.qada.QadaReport
import com.kamalahmed.prayerapp.qada.QadaRepository
import com.kamalahmed.prayerapp.qada.QadaStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class QadaUi(val config: QadaConfig, val report: QadaReport, val entries: List<QadaEntry>)

@HiltViewModel
class QadaViewModel @Inject constructor(
    private val repo: QadaRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val state: StateFlow<QadaUi?> = combine(repo.debt, repo.entries, repo.config) { d, e, c ->
        QadaUi(c, QadaStats.report(d, e, c, LocalDate.now()), e)
    }.reportErrors("QadaViewModel", "Could not load your qada tracker").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun io(block: suspend () -> Unit) = viewModelScope.launch(safeHandler("QadaViewModel")) { block() }

    fun log(p: QadaPrayer, n: Int) = io { repo.log(p, n.coerceIn(1, QadaRepository.MAX_BATCH)) }
    /** One of each fard prayer: a full day made up. */
    fun logDay() = io { QadaPrayer.fard.forEach { repo.log(it, 1) } }
    fun undo() = io { if (!repo.undoLast()) AppErrors.report("Nothing to undo") }
    fun delete(id: Long) = io { repo.delete(id) }
    fun saveDebt(counts: Map<QadaPrayer, Int>, witr: Boolean) = io { repo.setDebt(counts, witr) }
    fun reset() = io { repo.resetAll(); QadaReminderWorker.schedule(context, QadaConfig()) }
    fun setGoal(goal: Int) = updateConfig { it.copy(dailyGoal = goal.coerceIn(0, 100)) }
    fun setReminder(on: Boolean) = updateConfig { it.copy(reminder = on) }
    fun setReminderMinute(m: Int) = updateConfig { it.copy(reminderMinute = m.coerceIn(0, 24 * 60 - 1)) }

    private fun updateConfig(f: (QadaConfig) -> QadaConfig) = io {
        repo.updateConfig(f)
        QadaReminderWorker.schedule(context, repo.currentConfig())
    }
}
