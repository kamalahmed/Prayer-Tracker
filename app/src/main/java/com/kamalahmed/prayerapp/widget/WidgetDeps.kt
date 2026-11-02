package com.kamalahmed.prayerapp.widget

import android.content.Context
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.tracker.TrackerRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.ZonedDateTime

/** Widgets, receivers and action callbacks are created by the system, so they reach Hilt through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun settings(): SettingsRepository
    fun tracker(): TrackerRepository
    fun styles(): WidgetStyleStore
}

fun widgetDeps(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

suspend fun WidgetEntryPoint.loadWidgetData(): WidgetData {
    val s = settings().current()
    val schedule = PrayerSchedule(s.toCalcParams())
    return WidgetData.build(schedule, s, tracker().snapshot(), ZonedDateTime.now(schedule.zone))
}
