package com.kamalahmed.prayerapp.data.prayer

import com.kamalahmed.prayerapp.core.prayer.CalcParams
import com.kamalahmed.prayerapp.core.prayer.CalculationMethod
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

fun AppSettings.toCalcParams(): CalcParams = CalcParams(
    latitude = latitude,
    longitude = longitude,
    zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault()),
    method = CalculationMethod.fromId(calcMethod),
    asr = asrMethod,
    highLatRule = highLatRule,
    adjustments = adjustments,
)

@Singleton
class PrayerRepository @Inject constructor(private val settingsRepo: SettingsRepository) {

    val schedule: Flow<PrayerSchedule> = settingsRepo.settings
        .map { it.toCalcParams() }
        .distinctUntilChanged()
        .map { PrayerSchedule(it) }

    suspend fun currentSchedule(): PrayerSchedule = PrayerSchedule(settingsRepo.current().toCalcParams())
}
