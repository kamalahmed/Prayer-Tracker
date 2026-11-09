package com.kamalahmed.prayerapp.phase21

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.safety.AppErrors
import com.kamalahmed.prayerapp.tracker.PrayerLog
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import com.kamalahmed.prayerapp.ui.OnboardedRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

/** Two weeks where Fajr is always late (weakest prayer, coach suggests its alarm); the other prayers on time. */
class SeedFajrLateRule : TestRule {
    override fun apply(base: Statement, d: Description) = object : Statement() {
        override fun evaluate() {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            val db = Room.databaseBuilder(ctx, TrackerDatabase::class.java, "tracker.db").addMigrations(*TrackerDatabase.MIGRATIONS).allowMainThreadQueries().build()
            runBlocking {
                for (i in 1..14) Prayer.obligatory.forEach { p ->
                    db.dao().upsert(PrayerLog(LocalDate.now().minusDays(i.toLong()).toString(), p.name, if (p == Prayer.FAJR) PrayerStatus.LATE else PrayerStatus.ON_TIME))
                }
            }
            db.close()
            base.evaluate()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class Phase21UiTest {
    val compose: AndroidComposeTestRule<*, MainActivity> = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(OnboardedRule()).around(SeedFajrLateRule()).around(compose)

    private fun settle() { compose.waitForIdle(); compose.mainClock.advanceTimeBy(600); compose.waitForIdle() }
    private fun has(t: String, substring: Boolean = true) = compose.onAllNodes(hasText(t, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun awaitText(t: String) = compose.waitUntil(20_000) { has(t) }
    private fun awaitDesc(t: String) = compose.waitUntil(20_000) { compose.onAllNodes(hasContentDescription(t), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        val n = compose.onAllNodes(hasText(text) and hasClickAction()).onFirst()
        runCatching { n.performScrollTo() }
        n.performClick(); settle()
    }
    private fun clickDesc(desc: String) {
        awaitDesc(desc)
        val n = compose.onAllNodes(hasContentDescription(desc) and hasClickAction()).onFirst()
        runCatching { n.performScrollTo() }
        n.performClick(); settle()
    }
    private fun scrollTo(text: String) { compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true)); settle() }
    private fun db() = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), TrackerDatabase::class.java, "tracker.db")
        .addMigrations(*TrackerDatabase.MIGRATIONS).allowMainThreadQueries().build()
    private fun qadaToday(): Int { val d = db(); try { return runBlocking { d.qada().countOn(LocalDate.now().toString()) } } finally { d.close() } }

    @Test fun qadaTapFeedbackDoneTodayUndoBatchAndEditDay() {
        AppErrors.latest.value = null
        settle(); click("Streaks"); click("Qada")
        awaitText("Make up missed prayers"); click("1 yr"); click("Save")
        awaitText("prayers still to go")
        awaitText("Log today"); awaitText("History")
        clickDesc("Add one Fajr")
        awaitText("Fajr +1 · ×1 today")      // snackbar
        awaitDesc("Fajr done today, 1 times") // highlighted row with ×1
        click("UNDO")
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Fajr done today, 1 times"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10_000) { qadaToday() == 0 }
        clickDesc("Add several Asr")
        awaitText("Add several Asr"); click("10"); click("Add 10 Asr")
        awaitDesc("Asr done today, 10 times")
        // The full-day snackbar title is covered in QadaHistoryTest: here the virtual clock runs past its
        // 4.5 s auto-hide while the test waits for the background insert.
        click("+1 full day (5)")
        compose.waitUntil(10_000) { qadaToday() == 15 }
        scrollTo("one row per day")
        awaitText("Today ·")
        click("Edit day")
        clickDesc("One more Isha")
        compose.waitUntil(10_000) { qadaToday() == 16 }
        click("Done")
        assertNull("error: ${AppErrors.latest.value}", AppErrors.latest.value)
    }

    @Test fun insightsCoachDeepLinksToIqamahAlarmAndHideCoachPersists() {
        AppErrors.latest.value = null
        settle(); click("Streaks"); click("Insights")
        awaitText("At a glance"); awaitText("Your coach")
        click("Per prayer"); awaitText("Dots = on time")
        scrollTo("Bring Fajr back on time")
        click("Set Fajr alarm")
        awaitText("Show iqamah times") // Settings › Iqama (before-iqamah alarm per prayer)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; settle()
        awaitText("Your coach")
        scrollTo("Hide coach"); click("Hide coach")
        awaitText("Coach hidden")
        val prefs = ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("insights", android.content.Context.MODE_PRIVATE)
        assertEquals(true, prefs.getBoolean("coach_hidden", false))
        click("Show coach"); awaitText("Your coach")
        assertNull("error: ${AppErrors.latest.value}", AppErrors.latest.value)
    }
}
