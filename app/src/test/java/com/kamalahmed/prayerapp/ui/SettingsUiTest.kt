package com.kamalahmed.prayerapp.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.ui.settings.CitySearch
import com.kamalahmed.prayerapp.ui.settings.SettingsContent
import com.kamalahmed.prayerapp.ui.settings.SettingsDest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w900dp-h700dp")
class SettingsUiTest {
    @get:Rule val rule = createComposeRule()

    private fun rowOf(title: String) = rule.onNode(hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected), useUnmergedTree = false)
    private val isSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    @Test fun listDetailHighlightsDefaultThenFollowsSelection() {
        rule.setContent {
            var sel by remember { mutableStateOf(SettingsDest.NOTIFICATIONS) }
            SettingsContent(AppSettings(), false, open = { sel = it }, available = SettingsDest.entries.toSet(), selected = sel)
        }
        rowOf("Prayer alerts & adhan").assert(isSelected)
        rule.onNodeWithText("Location").performClick()
        rowOf("Location").assert(isSelected)
        rule.onAllNodes(isSelected).assertCountEquals(1)
    }

    @Test fun singlePaneHasNoHighlight() {
        rule.setContent { SettingsContent(AppSettings(), false, open = {}, available = SettingsDest.entries.toSet(), selected = null) }
        rule.onAllNodes(isSelected).assertCountEquals(0)
    }

    /** Beta.3 crash: the Location page's city search opened an asset name that is not in the packaged app. */
    @Test fun citySearchLoadsPackagedAssetWithoutCrashing() {
        rule.setContent { androidx.compose.foundation.layout.Column { CitySearch(selectedLabel = null, onPick = {}) } }
        rule.waitUntil(20_000) { rule.onAllNodes(hasText("Dhaka", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasText("City list unavailable", substring = true)).assertCountEquals(0)
    }
}
