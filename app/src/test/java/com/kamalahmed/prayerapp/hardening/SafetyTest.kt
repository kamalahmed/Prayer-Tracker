package com.kamalahmed.prayerapp.hardening

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.core.location.CityDatabase
import com.kamalahmed.prayerapp.core.safety.CrashLog
import com.kamalahmed.prayerapp.core.safety.guard
import com.kamalahmed.prayerapp.core.safety.reportErrors
import com.kamalahmed.prayerapp.core.safety.AppErrors
import com.kamalahmed.prayerapp.core.safety.safeScope
import com.kamalahmed.prayerapp.tracker.Stats
import com.kamalahmed.prayerapp.update.UpdateManager
import com.kamalahmed.prayerapp.update.UpdatePrefs
import com.kamalahmed.prayerapp.update.UpdateState
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException

/** Failure injection for the hardened paths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafetyTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun uncaughtHandlerRecordsCrashThenDelegates() {
        CrashLog.clear(ctx)
        var delegated = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegated = true }
        CrashLog.install(ctx, "test", 1)
        val t = Thread { throw IllegalStateException("boom") }
        t.start(); t.join()
        val log = CrashLog.lastCrash(ctx)
        assertNotNull(log); assertTrue(log!!.contains("IllegalStateException")); assertTrue(log.startsWith("CRASH at"))
        assertTrue("system handler still runs so the process dies normally", delegated)
        assertTrue(CrashLog.diagnostics(ctx).contains("boom"))
        CrashLog.clear(ctx); assertNull(CrashLog.lastCrash(ctx))
    }

    @Test fun safeScopeAndGuardRecordInsteadOfThrowing() = runBlocking {
        CrashLog.install(ctx, "test", 1)
        safeScope("T").launch { error("child failure") }.join()
        assertEquals(null, guard("T", "x") { throw java.io.IOException("io") })
        val errors = CrashLog.recentErrors(ctx) ?: ""
        assertTrue(errors, errors.contains("child failure") && errors.contains("io"))
    }

    @Test fun uiFlowErrorsBecomeAMessage() = runBlocking {
        AppErrors.latest.value = null
        val got = flow { emit(1); throw ArithmeticException("x") }.reportErrors("T", "Could not calculate").toList()
        assertEquals(listOf(1), got)
        assertEquals("Could not calculate", AppErrors.latest.value)
    }

    @Test fun malformedCityRowsAreSkipped() {
        val tsv = "#tz\tAsia/Dhaka\n#cc\tBD=Bangladesh\nSylhet\tSylhet\t\tBD\tSylhet\t24.9\t91.87\t0\t500000\n" +
            "Bad\tBad\t\tBD\tX\tnot-a-number\t1\t0\t1\nShort\trow\nBadTz\tBadTz\t\tBD\tX\t1\t1\t99\t1\n"
        val db = CityDatabase.load(tsv.byteInputStream())
        assertEquals(1, db.size)
        assertEquals("Sylhet", db.search("sylhet").single().name)
    }

    @Test(expected = java.io.IOException::class) fun emptyCityFileIsAnError() { CityDatabase.load("".byteInputStream()) }

    @Test fun statsInsightsWithNoRanksDoNotThrow() {
        val out = Stats.insights(com.kamalahmed.prayerapp.tracker.StatsPeriod.WEEK, com.kamalahmed.prayerapp.tracker.Counts(onTime = 1, due = 1), com.kamalahmed.prayerapp.tracker.Counts(), emptyList())
        assertEquals(emptyList<String>(), out)
    }

    @Test fun updaterOfflineAndMalformedJsonBecomeErrorStates() = runBlocking {
        val m = UpdateManager(ctx, UpdatePrefs(ctx))
        m.releasesSource = { throw UnknownHostException("api.github.com") }
        assertTrue((m.check() as UpdateState.Error).message.contains("offline"))
        m.releasesSource = { "{ definitely not json" }
        assertEquals("Couldn't read release info.", (m.check() as UpdateState.Error).message)
        m.releasesSource = { "[]" }
        assertEquals(UpdateState.UpToDate, m.check())
    }
}
