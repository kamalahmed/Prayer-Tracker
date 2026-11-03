package com.kamalahmed.prayerapp.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.core.safety.AppErrors
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Finishes onboarding before the activity starts, so the real app opens on the main tabs. */
class OnboardedRule : TestRule {
    override fun apply(base: Statement, d: Description) = object : Statement() {
        override fun evaluate() {
            runBlocking { SettingsRepository(ApplicationProvider.getApplicationContext()).update { it.copy(onboardingDone = true) } }
            base.evaluate()
        }
    }
}

/**
 * Opens every main tab and every Settings page of the real MainActivity (real Hilt graph, ViewModels, DataStore,
 * Room, assets). Any exception thrown on the main thread or in a ViewModel fails the test.
 */
abstract class AllScreensSmoke(private val listDetail: Boolean) {
    val compose: AndroidComposeTestRule<*, MainActivity> = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(OnboardedRule()).around(compose)

    private val tabs = listOf("Prayers", "Times", "Streaks", "Qibla", "Settings")
    /** Settings row -> text that only appears once that page has really rendered. */
    private val pages = linkedMapOf(
        "Prayer alerts & adhan" to "Notifications & Adhan", "Adhan sound" to "Adhan sound", "Calculation" to "Calculation",
        "Location" to "Dhaka", "Time" to "Time", "Iqama" to "Iqama", "System" to "System", "App" to "Copy diagnostics",
        "Widget studio" to "Widget studio",
    )

    private fun awaitText(t: String) = compose.waitUntil(20_000) {
        compose.onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun settle() { compose.waitForIdle(); compose.mainClock.advanceTimeBy(1_500); compose.waitForIdle() }

    private fun clickFirst(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
        settle()
    }

    @Test fun everyTabAndSettingsPageOpensWithoutCrashing() {
        AppErrors.latest.value = null
        settle()
        tabs.forEach { clickFirst(it); if (it == "Qibla") awaitText("from true north"); assertNull("error on tab $it: ${AppErrors.latest.value}", AppErrors.latest.value) }
        for ((p, marker) in pages) {
            clickFirst("Settings")
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(p) and hasClickAction())
            clickFirst(p)
            if (p == "App") compose.onAllNodes(hasScrollAction() and androidx.compose.ui.test.hasAnyDescendant(hasText("Adhan recordings"))).onFirst().performScrollToNode(hasText("Copy diagnostics"))
            awaitText(marker)
            assertNull("error on $p: ${AppErrors.latest.value}", AppErrors.latest.value)
            if (!listDetail) compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            settle()
        }
    }
}
