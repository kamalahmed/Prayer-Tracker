package com.kamalahmed.prayerapp.data.settings

import com.kamalahmed.prayerapp.core.model.AsrMethod
import com.kamalahmed.prayerapp.core.model.HighLatitudeRule
import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.SoundSource

/** Immutable snapshot of all user settings. Defaults target Sharjah, UAE. */
data class AppSettings(
    // Prayer / calculation
    val calcMethod: String = "DUBAI",
    val asrMethod: AsrMethod = AsrMethod.STANDARD,
    val highLatRule: HighLatitudeRule = HighLatitudeRule.NONE,
    val adjustments: Map<Prayer, Int> = emptyMap(),
    // Location
    val autoLocation: Boolean = false,
    val latitude: Double = 25.3463,
    val longitude: Double = 55.4209,
    val cityName: String = "Sharjah",
    val timeZoneId: String = "Asia/Dubai",
    val geofenceKm: Int = 10,
    // Time
    val use24h: Boolean = false,
    val hijriAdjust: Int = 0,
    val autoIncrementHijri: Boolean = false,
    val abbreviations: Boolean = false,
    val countUpMinutes: Int = 0,
    // Iqama
    val iqamaEnabled: Boolean = false,
    val iqamaMinutes: Map<Prayer, Int> = mapOf(
        Prayer.FAJR to 25, Prayer.DHUHR to 20, Prayer.ASR to 20, Prayer.MAGHRIB to 5, Prayer.ISHA to 20,
    ),
    val iqamaFixed: Map<Prayer, Int> = emptyMap(),
    val iqamaNotifications: Set<Prayer> = emptySet(),
    val iqamaBefore: Map<Prayer, Int> = emptyMap(),
    val jumuahEnabled: Boolean = false,
    val jumuahFixed: Boolean = true,
    val jumuahOffset: Int = 20,
    val jumuahMinute: Int = 13 * 60,
    val jumuahNotify: Boolean = false,
    val jumuahBefore: Int = 0,
    // Notifications
    val notifyModes: Map<Prayer, NotifyMode> = Prayer.entries.associateWith {
        if (it == Prayer.SUNRISE) NotifyMode.OFF else NotifyMode.ADHAN
    },
    val soundSource: SoundSource = SoundSource.BUNDLED,
    val bundledSound: String = "adhan_full",
    val fajrBundledSound: String = "adhan_full",
    val soundUri: String? = null,
    val soundName: String = "Adhan (full)",
    val maxPlaySeconds: Int = 300,
    val loopSound: Boolean = false,
    val useAlarmStream: Boolean = true,
    val volumePercent: Int = 100,
    val snoozeMinutes: Int = 5,
    val fullScreenAlarm: Boolean = false,
    val preReminderMinutes: Int = 0,
    val adhanDua: Boolean = true,
    val vibrate: Boolean = true,
    // App
    val onboardingDone: Boolean = false,
    // Private reflections: opt in independently; local clock times, approximate Android delivery.
    val summaryDaily: Boolean = false,
    val summaryDailyMinute: Int = 21*60+30,
    val summaryWeekly: Boolean = false,
    val summaryWeeklyMinute: Int = 20*60,
) {
    fun adjustment(p: Prayer) = adjustments[p] ?: 0
    fun mode(p: Prayer) = notifyModes[p] ?: NotifyMode.OFF
    fun iqama(p: Prayer) = iqamaMinutes[p] ?: 0
}
