package com.kamalahmed.prayerapp.phase19

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.summary.*
import com.kamalahmed.prayerapp.ui.OnboardedRule
import com.kamalahmed.prayerapp.ui.SeedTrackerRule
import org.junit.*
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],qualifiers="w411dp-h891dp")
class SummaryNavigationTest {
    private val rule=createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain=RuleChain.outerRule(OnboardedRule()).around(SeedTrackerRule()).around(rule)
    private fun await(text: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() };rule.waitForIdle() }
    private fun click(text: String) { await(text);val node=rule.onAllNodes(hasText(text) and hasClickAction()).onFirst();runCatching { node.performScrollTo() };node.performClick();rule.mainClock.advanceTimeBy(500);rule.waitForIdle() }
    @Test fun repeatedNotificationDeepLinkOverridesAChangedSummaryTab() {
        click("Streaks");click("Your summaries");await("Your day")
        val request=SummaryRequest(SummaryKind.DAY,LocalDate.now())
        rule.runOnIdle { SummaryNav.openRequested.value=request };await("Your day")
        click("Weekly");await("Your week")
        rule.runOnIdle { SummaryNav.openRequested.value=request };await("Your day")
    }
}
