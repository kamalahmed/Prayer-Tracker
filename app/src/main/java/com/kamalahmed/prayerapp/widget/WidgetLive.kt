package com.kamalahmed.prayerapp.widget

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapLatest

data class WidgetSnapshot(val style: WidgetStyle, val data: WidgetData)

/** Live data source for running widget sessions. */
object WidgetLive {
    private val _ticks = MutableStateFlow(0L)
    /** Bumped on every explicit refresh (boundary alarm, time change, style change, updateAll). */
    val ticks: StateFlow<Long> = _ticks

    fun bump() { _ticks.value = _ticks.value + 1 }

    /**
     * Emits a fresh snapshot whenever tracker rows, settings or [ticks] change. The first combined emission is
     * dropped because the caller already rendered an initial snapshot. Errors keep the last good snapshot.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun <T> changes(tracker: Flow<Any?>, settings: Flow<Any?>, ticks: Flow<Long>, load: suspend () -> T): Flow<T> =
        combine(tracker, settings, ticks) { a, b, c -> Triple(a, b, c) }
            .distinctUntilChanged()
            .drop(1)
            .mapLatest { load() }
            .catch { Log.w("WidgetLive", "live update failed", it) }

    fun snapshots(deps: WidgetEntryPoint, appWidgetId: Int, kind: WidgetKind, load: suspend () -> WidgetSnapshot): Flow<WidgetSnapshot> =
        changes(deps.tracker().all, deps.settings().settings, ticks, load)
}
