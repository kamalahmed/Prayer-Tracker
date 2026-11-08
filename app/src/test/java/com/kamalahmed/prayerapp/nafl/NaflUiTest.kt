package com.kamalahmed.prayerapp.nafl

import androidx.compose.ui.test.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import com.kamalahmed.prayerapp.ui.adaptive.AdaptiveSpec
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import com.kamalahmed.prayerapp.ui.nafl.NaflContent
import com.kamalahmed.prayerapp.ui.nafl.NaflUi
import com.kamalahmed.prayerapp.tracker.StatsPeriod
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

abstract class NaflUiSmoke {
    @get:Rule val rule = createComposeRule()
    private fun scroll(t: String) = rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(t))
    @Test fun dashboardLoggingConfigurationAndReminderFormsRender() {
        val d = LocalDate.now()
        var saved = -1
        rule.setContent {
            val logs = listOf(NaflLog(d.toString(), "quran", 1, 3, 10))
            val config = LocalConfiguration.current
            CompositionLocalProvider(LocalAdaptive provides AdaptiveSpec(config.screenWidthDp.toFloat(), config.screenHeightDp.toFloat())) {
            PrayerTheme { Surface { NaflContent(NaflUi(NaflDefaults.items, logs, emptyList(), StatsPeriod.MONTH, d, d, d,
                NaflStats.report(NaflDefaults.items, logs, StatsPeriod.MONTH, d, d)),
                onLog = { _, n, _, _ -> saved = n }) } }
            }
        }
        rule.onNodeWithText("Daily Sunnah").assertExists()
        rule.onNodeWithContentDescription("Mark Before Fajr").performScrollTo().performClick()
        assertEquals(1, saved)
        scroll("Other worship")
        rule.onNodeWithContentDescription("Edit Quran reading").performScrollTo().performClick()
        rule.onNodeWithText("Pages (optional)").assertExists()
        rule.onNodeWithText("Minutes (optional)").assertExists()
        rule.onNodeWithText("Cancel").performScrollTo().performClick()
        scroll("Customize worship")
        rule.onNodeWithText("Customize worship").performClick()
        rule.onNodeWithText("Before Asr").assertExists()
        rule.onNodeWithText("Done").performScrollTo().performClick()
        scroll("Add reminder")
        rule.onNodeWithText("Add reminder").performClick()
        rule.onNodeWithText("Use alarm sound").assertExists()
        rule.onNodeWithText("Cancel").performScrollTo().performClick()
        scroll("Calendar")
        rule.onNodeWithText("Calendar").assertExists()
    }
}
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NaflPhoneUiTest : NaflUiSmoke()
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], qualifiers = "w840dp-h700dp")
class NaflWideUiTest : NaflUiSmoke()
