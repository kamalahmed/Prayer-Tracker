package com.kamalahmed.prayerapp.phase17

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kamalahmed.prayerapp.update.*
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34],qualifiers = "w411dp-h891dp")
class TodayUpdateUiTest {
    @get:Rule val rule = createComposeRule()
    private fun release() = ReleaseParser.parseList(javaClass.classLoader!!.getResource("releases_sample.json")!!.readText()).first()
    @Test fun updateLaterAndPermissionActionsAreVisibleOnTodayCard() {
        var update = 0; var later = 0; var permission = 0
        rule.setContent { PrayerTheme { Surface { TodayUpdateCard(UpdateState.Available(release()),{ update++ },{ later++ },false,{ permission++ }) } } }
        rule.onNodeWithText("Update").performClick(); assertEquals(1,update)
        rule.onNodeWithText("Later").performClick(); assertEquals(1,later)
        rule.onNodeWithText("Enable update notifications").performClick(); assertEquals(1,permission)
    }
    @Test fun downloadShowsProgressInsteadOfDuplicateUpdateButton() {
        rule.setContent { PrayerTheme { Surface { TodayUpdateCard(UpdateState.Downloading(release(),50,100),{},{}) } } }
        rule.onNodeWithText("Downloading 50% · you can leave the app").assertExists()
        rule.onNodeWithText("Update").assertDoesNotExist()
    }
}
