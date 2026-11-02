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
    val masjidName: String = "",
    val masjidLat: Double? = null,
    val masjidLng: Double? = null,
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
    // Notifications
    val notifyModes: Map<Prayer, NotifyMode> = Prayer.entries.associateWith {
        if (it == Prayer.SUNRISE) NotifyMode.OFF else NotifyMode.ADHAN
    },
    val soundSource: SoundSource = SoundSource.BUNDLED,
    val bundledSound: String = "adhan_makkah",
    val fajrBundledSound: String = "adhan_fajr",
    val soundUri: String? = null,
    val soundName: String = "Adhan (Makkah)",
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
) {
    fun adjustment(p: Prayer) = adjustments[p] ?: 0
    fun mode(p: Prayer) = notifyModes[p] ?: NotifyMode.OFF
    fun iqama(p: Prayer) = iqamaMinutes[p] ?: 0
}
