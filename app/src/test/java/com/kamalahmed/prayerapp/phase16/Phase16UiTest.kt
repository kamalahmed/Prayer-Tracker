package com.kamalahmed.prayerapp.phase16

import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kamalahmed.prayerapp.ui.nafl.*
import com.kamalahmed.prayerapp.ui.settings.IqamahSettingsContent
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.data.settings.AppSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], qualifiers = "w411dp-h891dp")
class Phase16UiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun fullScreenCounterTapsUndoSelectionAndResetConfirmation() {
        var value = 32; var resets = 0; var chosen = ""
        rule.setContent {
            var count by remember { mutableIntStateOf(32) }
            val items = NaflDefaults.items.filter { it.isCounter }
            PrayerTheme { Surface { TasbihContent(TasbihUi(items,items.first { it.id == "subhanallah" },count,2,LocalDate.now()),
                { chosen = it },{ count++; value = count },{ count--; value = count },{ resets++ },true,{},false,{},{}) } }
        }
        rule.onNode(hasContentDescription("Count SubhanAllah",substring = true)).performScrollTo().performClick()
        assertEquals(33,value)
        rule.onNodeWithText("33 / 33 · Target reached ✓").assertExists()
        rule.onNodeWithText("Undo / −1").performScrollTo().performClick(); assertEquals(32,value)
        rule.onNodeWithText("Reset session").performScrollTo().performClick()
        assertEquals(0,resets)
        rule.onNodeWithText("Confirm reset").performScrollTo().performClick(); assertEquals(1,resets)
        rule.onNodeWithText("Istighfar").performScrollTo().performClick(); assertEquals("istighfar",chosen)
    }
    @Test fun iqamahSettingsRenderOffsetFixedAndIndependentNotifications() {
        rule.setContent {
            var s by remember { mutableStateOf(AppSettings()) }
            PrayerTheme { Surface { IqamahSettingsContent(s,{ s = it(s) },{ _,_ -> }) } }
        }
        rule.onNodeWithText("Show iqamah times").assertExists()
        rule.onAllNodesWithText("Fixed clock time").onFirst().performScrollTo().performClick()
        rule.onAllNodes(hasText("Iqamah at",substring = true)).onFirst().assertExists()
        rule.onAllNodesWithText("Before-iqamah alarm").onFirst().assertExists()
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Jumu'ah override"))
        rule.onNodeWithText("Jumu'ah override").performClick()
        rule.onNode(hasText("Jumu'ah at",substring = true)).assertExists()
    }
    @Test fun quranReminderUsesTwelveHourAmPmControlsAndDisplay() {
        var saved: QuranReminder? = null
        rule.setContent {
            val day = LocalDate.now()
            val items = NaflDefaults.items
            PrayerTheme { Surface { NaflContent(NaflUi(items,emptyList(),listOf(QuranReminder(1,1205)),com.kamalahmed.prayerapp.tracker.StatsPeriod.MONTH,day,day,day,
                NaflStats.report(items,emptyList(),com.kamalahmed.prayerapp.tracker.StatsPeriod.MONTH,day,day)),onReminder = { saved = it }) } }
        }
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("8:05 PM"))
        rule.onNodeWithText("8:05 PM").performClick()
        rule.onNodeWithText("Hour (1–12)").assertExists()
        rule.onNodeWithText("AM").performClick()
        rule.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(485,saved!!.minute)
    }
    @Test fun customWorshipCanBeCreatedAsCounterWithTarget() {
        var type = ""; var target = 0
        rule.setContent {
            val day = LocalDate.now(); val items = NaflDefaults.items
            PrayerTheme { Surface { NaflContent(NaflUi(items,emptyList(),emptyList(),com.kamalahmed.prayerapp.tracker.StatsPeriod.MONTH,day,day,day,
                NaflStats.report(items,emptyList(),com.kamalahmed.prayerapp.tracker.StatsPeriod.MONTH,day,day)),onAddTyped = { _,t,n -> type = t; target = n }) } }
        }
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Customize worship"))
        rule.onNodeWithText("Customize worship").performClick()
        rule.onNodeWithText("Add custom item").performScrollTo().performClick()
        rule.onNodeWithText("Name").performTextInput("My tasbih")
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("Daily target (optional)").performTextInput("33")
        rule.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals("COUNTER",type); assertEquals(33,target)
    }
}
