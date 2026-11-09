package com.kamalahmed.prayerapp.phase20

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.ui.settings.IqamahSettingsContent
import com.kamalahmed.prayerapp.ui.settings.StartTimeAdjustments
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], qualifiers = "w411dp-h891dp")
class Phase20UiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun startTimeEditorAcceptsLargeOffsetsAndFixedTimes() {
        var state by mutableStateOf(AppSettings())
        rule.setContent { PrayerTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState())) { StartTimeAdjustments(state) { state = it(state) } } } } }
        rule.onNodeWithText("PRAYER START TIME (ADHAN)").assertExists()
        rule.onNode(hasText("STARTS", substring = true)).assertExists()
        rule.onNodeWithText("Dhuhr").performScrollTo().performClick()
        rule.onNodeWithText("Dhuhr start time").assertExists()
        rule.onNodeWithText("+60").performClick(); rule.onNodeWithText("+60").performClick()
        rule.onNodeWithText("+15").performClick(); rule.onNodeWithText("−1").performClick()
        rule.onNode(hasText("Set from the masjid", substring = true)).assertExists()
        repeat(8) { rule.onNodeWithText("+60").performClick() }   // clamps at +360
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        assertEquals(360, state.adjustment(Prayer.DHUHR))
        rule.onNode(hasText("+360 min (6 h 0 min)", substring = true)).assertExists()
        // Fixed time mode is saved separately and reset clears both.
        rule.onNodeWithText("Dhuhr").performScrollTo().performClick()
        rule.onNodeWithText("Fixed clock time every day").performClick()
        rule.onNode(hasText("Starts at", substring = true)).assertExists()
        rule.onNodeWithText("Save").performClick(); rule.waitForIdle()
        assertNotNull(state.startFixed[Prayer.DHUHR])
        rule.onNodeWithText("Dhuhr").performScrollTo().performClick()
        rule.onNodeWithText("Reset").performClick(); rule.waitForIdle()
        assertNull(state.startFixed[Prayer.DHUHR]); assertEquals(0, state.adjustment(Prayer.DHUHR))
        // Sunrise has offset only (no fixed clock option).
        rule.onNodeWithText("Sunrise").performScrollTo().performClick()
        rule.onNodeWithText("Fixed clock time every day").assertDoesNotExist()
    }

    @Test fun beforeIqamahAlarmHasOwnToggleLeadChoicesStyleAndSound() {
        var state by mutableStateOf(AppSettings())
        var preview: Boolean? = null
        rule.setContent { PrayerTheme { Surface { IqamahSettingsContent(state, { state = it(state) }, { p, on ->
            state = if (p == null) state.copy(jumuahNotify = on) else state.copy(iqamaNotifications = if (on) state.iqamaNotifications + p else state.iqamaNotifications - p)
        }, onPreview = { preview = it }) } } }
        rule.onAllNodesWithText("Custom minutes before").assertCountEquals(0)
        rule.onAllNodesWithText("Before-iqamah alarm").onFirst().performScrollTo().performClick()
        assertEquals(setOf(Prayer.FAJR), state.iqamaNotifications)
        rule.onAllNodesWithText("15 min").onFirst().performScrollTo().performClick()
        assertEquals(15, state.iqamaBefore[Prayer.FAJR])
        rule.onAllNodesWithText("Full alarm sound").onFirst().performScrollTo().performClick()
        assertEquals(setOf(Prayer.FAJR), state.iqamaQuiet)
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Gentle chime"))
        rule.onNodeWithText("Gentle chime").performClick()
        assertEquals("chime", state.iqamahSound)
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Preview iqamah alarm"))
        rule.onNodeWithText("Preview iqamah alarm").performClick()
        assertEquals(true, preview)
    }
}
