package com.kamalahmed.prayerapp.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Running widget sessions must reload on tracker, settings and explicit refresh changes (beta.6 "not live" fix). */
class WidgetLiveTest {
    @Test fun reloadsOnEveryChangeSource() = runTest(UnconfinedTestDispatcher()) {
        val tracker = MutableStateFlow<Any?>(0); val settings = MutableStateFlow<Any?>("s"); val ticks = MutableStateFlow(0L)
        var loads = 0
        val out = mutableListOf<Int>()
        val job = launch { WidgetLive.changes(tracker, settings, ticks) { ++loads }.toList(out) }
        assertEquals(emptyList<Int>(), out) // initial state already rendered by provideGlance
        tracker.value = 1 // marked from app, widget or notification
        settings.value = "s2"
        ticks.value = 1 // boundary alarm / updateAll
        assertEquals(listOf(1, 2, 3), out)
        job.cancel()
    }

    @Test fun loadFailureDoesNotCrash() = runTest(UnconfinedTestDispatcher()) {
        val tracker = MutableStateFlow<Any?>(0)
        val out = mutableListOf<Int>()
        val job = launch { WidgetLive.changes(tracker, MutableStateFlow(0), MutableStateFlow(0L)) { error("boom") as Int }.toList(out) }
        tracker.value = 2
        assertEquals(emptyList<Int>(), out)
        job.cancel()
    }
}
