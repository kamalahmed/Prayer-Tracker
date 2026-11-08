package com.kamalahmed.prayerapp.phase19

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.qada.QadaEntry
import com.kamalahmed.prayerapp.summary.*
import com.kamalahmed.prayerapp.tracker.*
import com.kamalahmed.prayerapp.ui.nafl.*
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.time.LocalDate

abstract class SummaryUiSmoke {
    @get:Rule val rule=createComposeRule()
    private val sunday=LocalDate.of(2026,11,8)
    private val monday=sunday.minusDays(6)
    private val tracker=(0L..6L).associate { monday.plusDays(it) to Prayer.obligatory.take(if(it==6L) 3 else 5).associateWith { PrayerStatus.ON_TIME } }
    private val logs=(0L..6L).flatMap { n -> listOf(NaflLog(monday.plusDays(n).toString(),"istighfar",100),NaflLog(monday.plusDays(n).toString(),"quran",1,5,15),NaflLog(monday.plusDays(n).toString(),"fajr_before",1)) }
    private fun scroll(text: String)=rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text,substring=true))
    @Test fun manyCustomCountersCannotPushTheTapRingOffScreen() {
        val items=NaflDefaults.items.filter { it.isCounter }+(1..50).map { NaflItem("extra_$it","My custom counter number $it",type="COUNTER") }
        val chosen=items.first { it.id=="istighfar" }
        rule.setContent { PrayerTheme { Surface { TasbihContent(TasbihUi(items,chosen,3,3,sunday),{},{},{},{},true,{},false,{},{}) } } }
        rule.onNode(hasContentDescription("Count Istighfar",substring=true)).assertIsDisplayed()
    }
    @Test fun dailyCardsKeepUnitsSeparateAndCounterDetailsAreProgressive() {
        val r=WorshipSummary.build(tracker,NaflDefaults.items,logs,listOf(QadaEntry(prayer="FAJR",count=3,date=sunday.toString())),sunday,sunday,sunday.plusDays(1),emptyList())
        rule.setContent { PrayerTheme { Surface { SummaryContent(SummaryUi(SummaryKind.DAY,sunday.plusDays(1),r,null,tracker[sunday].orEmpty(),AppSettings())) } } }
        rule.onNodeWithText("Your day").assertExists()
        scroll("Time with the Quran");rule.onNodeWithText("Reading completed").assertExists()
        scroll("View all counter totals");rule.onNodeWithText("View all counter totals  +").performClick();rule.onNodeWithText("Istighfar").assertExists()
        scroll("Qada recovered");rule.onNodeWithText("3 make-up prayers").assertExists()
        scroll("Daily summary notification");rule.onNodeWithContentDescription("Enable daily summary notification").assertIsOff()
        rule.onNode(hasText("9:30 PM",substring=true)).assertExists()
    }
    @Test fun weeklyCardsShowComparisonsBestDayAndClearlyScopedStreaks() {
        val r=WorshipSummary.build(tracker,NaflDefaults.items,logs,emptyList(),monday,sunday,sunday.plusDays(1),emptyList())
        val previous=r.copy(fard=Counts(onTime=29,due=35),dhikr=500)
        rule.setContent { PrayerTheme { Surface { SummaryContent(SummaryUi(SummaryKind.WEEK,sunday.plusDays(1),r,previous,emptyMap(),AppSettings())) } } }
        rule.onNodeWithText("Your week").assertExists();rule.onNodeWithText("You kept showing up.").assertExists()
        scroll("A day to remember");rule.onNodeWithText("Most fard recorded; earliest day if tied").assertExists()
        scroll("Your consistent moments");rule.onNodeWithText("Longest 5-fard run: 6 days").assertExists()
        scroll("Weekly summary notification");rule.onNodeWithContentDescription("Enable weekly summary notification").assertIsOff()
    }
    @Test fun overviewGroupsManyItemsAndDrillsIntoOneWithoutDeletingLogging() {
        val items=NaflDefaults.items+(1..10).map { NaflItem("custom_$it","My counter $it",type="COUNTER") }
        val s=NaflUi(items,logs,emptyList(),StatsPeriod.WEEK,monday,sunday,sunday,NaflStats.report(items,logs,StatsPeriod.WEEK,monday,sunday))
        rule.setContent { PrayerTheme { Surface { NaflContent(s) } } }
        rule.onNodeWithText("Daily Sunnah").assertExists();rule.onNodeWithText("Overview").performClick()
        rule.onNodeWithText("Voluntary worship").assertExists()
        scroll("Dhikr counters");rule.onNodeWithText("Show 15 more dhikr counters").performScrollTo().performClick()
        rule.onNodeWithText("My counter 10").assertExists()
        rule.onNodeWithText("Istighfar").performScrollTo().performClick()
        rule.onNodeWithText("Show daily history").assertExists()
        rule.onNodeWithText("Done").performScrollTo().performClick()
    }
}
@GraphicsMode(GraphicsMode.Mode.NATIVE) @RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=Application::class,qualifiers="w411dp-h891dp")
class SummaryPhoneUiTest: SummaryUiSmoke()
@GraphicsMode(GraphicsMode.Mode.NATIVE) @RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=Application::class,qualifiers="w840dp-h700dp")
class SummaryFoldUiTest: SummaryUiSmoke()
