package com.kamalahmed.prayerapp.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.safety.AppErrors
import com.kamalahmed.prayerapp.tracker.PrayerLog
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Seeds ~14 months of mixed tracker data so every chart has real content (incl. a gap before tracking started). */
class SeedTrackerRule : TestRule {
    override fun apply(base: Statement, d: Description) = object : Statement() {
        override fun evaluate() {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            val db = Room.databaseBuilder(ctx, TrackerDatabase::class.java, "tracker.db").addMigrations(*TrackerDatabase.MIGRATIONS).allowMainThreadQueries().build()
            runBlocking {
                val today = LocalDate.now()
                for (i in 1..420) {
                    val day = today.minusDays(i.toLong())
                    Prayer.obligatory.forEachIndexed { k, p ->
                        val st = when ((i + k) % 7) { 0 -> PrayerStatus.MISSED; 1, 2 -> PrayerStatus.LATE; 3 -> null; else -> PrayerStatus.ON_TIME }
                        if (st != null) db.dao().upsert(PrayerLog(day.toString(), p.name, st))
                    }
                }
            }
            db.close()
            base.evaluate()
        }
    }
}

/** Streaks tab: Week / Month / Year dashboards and the Qada sub-tab (setup, logging, undo, charts) render without errors. */
abstract class StreaksScreensSmoke {
    val compose: AndroidComposeTestRule<*, MainActivity> = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(OnboardedRule()).around(SeedTrackerRule()).around(compose)

    private fun settle() { compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle() }
    private fun awaitText(t: String) = compose.waitUntil(20_000) {
        compose.onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun click(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        val n = compose.onAllNodes(hasText(text) and hasClickAction()).onFirst()
        runCatching { n.performScrollTo() } // controls may be scrolled off screen; a click there would miss
        n.performClick(); settle()
    }
    private fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true)); settle()
    }
    private fun noError(where: String) = assertNull("error on $where: ${AppErrors.latest.value}", AppErrors.latest.value)

    @Test fun statsPeriodsAndQadaRender() {
        AppErrors.latest.value = null
        settle()
        click("Streaks")
        awaitText("Current streak")
        val month = LocalDate.now().month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        for ((p, marker) in listOf("Month" to month, "Year" to "Prayers per month", "Week" to "Prayers per day")) {
            click(p)
            awaitText(marker); scrollTo("Streak history"); noError(p)
        }
        click("Year"); awaitText("Year at a glance")
        click("Qada")
        awaitText("Make up missed prayers")
        click("1 yr")
        click("Save")
        awaitText("prayers made up")
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Add one Fajr")).onFirst().performClick(); settle()
        click("+1 full day (5)")
        awaitText("Fajr × 1")
        click("Undo")
        scrollTo("Goal & reminder")
        click("Months")
        noError("Qada")
    }
}

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], qualifiers = "w411dp-h891dp")
class StreaksPhoneTest : StreaksScreensSmoke()

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], qualifiers = "w840dp-h700dp")
class StreaksFoldTest : StreaksScreensSmoke()

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class StreaksTabletTest : StreaksScreensSmoke()
