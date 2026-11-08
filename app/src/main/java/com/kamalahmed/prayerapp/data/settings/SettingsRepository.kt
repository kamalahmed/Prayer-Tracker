package com.kamalahmed.prayerapp.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kamalahmed.prayerapp.core.model.AsrMethod
import com.kamalahmed.prayerapp.core.model.HighLatitudeRule
import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.SoundSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val calcMethod = stringPreferencesKey("calc_method")
        val asr = stringPreferencesKey("asr_method")
        val highLat = stringPreferencesKey("high_lat")
        fun adj(p: Prayer) = intPreferencesKey("adj_${p.name}")
        val autoLoc = booleanPreferencesKey("auto_location")
        val lat = doublePreferencesKey("lat")
        val lng = doublePreferencesKey("lng")
        val city = stringPreferencesKey("city")
        val tz = stringPreferencesKey("tz")
        val geofence = intPreferencesKey("geofence_km")
        val use24h = booleanPreferencesKey("use_24h")
        val hijriAdj = intPreferencesKey("hijri_adjust")
        val hijriAuto = booleanPreferencesKey("hijri_auto_inc")
        val abbr = booleanPreferencesKey("abbreviations")
        val countUp = intPreferencesKey("count_up_min")
        val iqamaOn = booleanPreferencesKey("iqama_enabled")
        fun iqama(p: Prayer) = intPreferencesKey("iqama_${p.name}")
        fun iqFixed(p: Prayer) = intPreferencesKey("iq_fixed_${p.name}")
        fun iqNotify(p: Prayer) = booleanPreferencesKey("iq_notify_${p.name}")
        fun iqBefore(p: Prayer) = intPreferencesKey("iq_before_${p.name}")
        val jumOn = booleanPreferencesKey("jumuah_on")
        val jumFixed = booleanPreferencesKey("jumuah_fixed")
        val jumOffset = intPreferencesKey("jumuah_offset")
        val jumTime = intPreferencesKey("jumuah_time")
        val jumNotify = booleanPreferencesKey("jumuah_notify")
        val jumBefore = intPreferencesKey("jumuah_before")
        fun mode(p: Prayer) = stringPreferencesKey("mode_${p.name}")
        val soundSource = stringPreferencesKey("sound_source")
        val bundled = stringPreferencesKey("bundled_sound")
        val fajrBundled = stringPreferencesKey("fajr_bundled_sound")
        val soundUri = stringPreferencesKey("sound_uri")
        val soundName = stringPreferencesKey("sound_name")
        val maxPlay = intPreferencesKey("max_play_sec")
        val loop = booleanPreferencesKey("loop_sound")
        val alarmStream = booleanPreferencesKey("alarm_stream")
        val volume = intPreferencesKey("volume_pct")
        val snooze = intPreferencesKey("snooze_min")
        val fullScreen = booleanPreferencesKey("full_screen")
        val preRem = intPreferencesKey("pre_reminder_min")
        val dua = booleanPreferencesKey("adhan_dua")
        val vibrate = booleanPreferencesKey("vibrate")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    /** A read error (corrupt file, disk I/O) falls back to defaults instead of crashing every collector. */
    val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { e ->
            if (e is java.io.IOException) {
                com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal("SettingsRepository", "Settings read failed, using defaults", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else throw e
        }
        .map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            calcMethod = this[K.calcMethod] ?: d.calcMethod,
            asrMethod = this[K.asr].toEnum(d.asrMethod),
            highLatRule = this[K.highLat].toEnum(d.highLatRule),
            adjustments = Prayer.entries.associateWith { this[K.adj(it)] ?: 0 },
            autoLocation = this[K.autoLoc] ?: d.autoLocation,
            latitude = this[K.lat] ?: d.latitude,
            longitude = this[K.lng] ?: d.longitude,
            cityName = this[K.city] ?: d.cityName,
            timeZoneId = this[K.tz] ?: d.timeZoneId,
            geofenceKm = this[K.geofence] ?: d.geofenceKm,
            use24h = this[K.use24h] ?: d.use24h,
            hijriAdjust = this[K.hijriAdj] ?: d.hijriAdjust,
            autoIncrementHijri = this[K.hijriAuto] ?: d.autoIncrementHijri,
            abbreviations = this[K.abbr] ?: d.abbreviations,
            countUpMinutes = this[K.countUp] ?: d.countUpMinutes,
            iqamaEnabled = this[K.iqamaOn] ?: d.iqamaEnabled,
            iqamaMinutes = Prayer.obligatory.associateWith { this[K.iqama(it)] ?: d.iqama(it) },
            iqamaFixed = Prayer.obligatory.mapNotNull { p -> this[K.iqFixed(p)]?.takeIf { it in 0..1439 }?.let { p to it } }.toMap(),
            iqamaNotifications = Prayer.obligatory.filter { this[K.iqNotify(it)] == true }.toSet(),
            iqamaBefore = Prayer.obligatory.associateWith { (this[K.iqBefore(it)] ?: 0).coerceIn(0, 180) },
            jumuahEnabled = this[K.jumOn] ?: false,
            jumuahFixed = this[K.jumFixed] ?: true,
            jumuahOffset = (this[K.jumOffset] ?: 20).coerceIn(0,180),
            jumuahMinute = (this[K.jumTime] ?: d.jumuahMinute).coerceIn(0, 1439),
            jumuahNotify = this[K.jumNotify] ?: false,
            jumuahBefore = (this[K.jumBefore] ?: 0).coerceIn(0, 180),
            notifyModes = Prayer.entries.associateWith { this[K.mode(it)].toEnum(d.mode(it)) },
            soundSource = this[K.soundSource].toEnum(d.soundSource),
            bundledSound = this[K.bundled] ?: d.bundledSound,
            fajrBundledSound = this[K.fajrBundled] ?: d.fajrBundledSound,
            soundUri = this[K.soundUri],
            soundName = this[K.soundName] ?: d.soundName,
            maxPlaySeconds = this[K.maxPlay] ?: d.maxPlaySeconds,
            loopSound = this[K.loop] ?: d.loopSound,
            useAlarmStream = this[K.alarmStream] ?: d.useAlarmStream,
            volumePercent = this[K.volume] ?: d.volumePercent,
            snoozeMinutes = this[K.snooze] ?: d.snoozeMinutes,
            fullScreenAlarm = this[K.fullScreen] ?: d.fullScreenAlarm,
            preReminderMinutes = this[K.preRem] ?: d.preReminderMinutes,
            adhanDua = this[K.dua] ?: d.adhanDua,
            vibrate = this[K.vibrate] ?: d.vibrate,
            onboardingDone = this[K.onboarding] ?: d.onboardingDone,
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[K.calcMethod] = s.calcMethod
        this[K.asr] = s.asrMethod.name
        this[K.highLat] = s.highLatRule.name
        Prayer.entries.forEach { this[K.adj(it)] = s.adjustment(it) }
        this[K.autoLoc] = s.autoLocation
        this[K.lat] = s.latitude
        this[K.lng] = s.longitude
        this[K.city] = s.cityName
        this[K.tz] = s.timeZoneId
        this[K.geofence] = s.geofenceKm
        this[K.use24h] = s.use24h
        this[K.hijriAdj] = s.hijriAdjust
        this[K.hijriAuto] = s.autoIncrementHijri
        this[K.abbr] = s.abbreviations
        this[K.countUp] = s.countUpMinutes
        this[K.iqamaOn] = s.iqamaEnabled
        Prayer.obligatory.forEach { this[K.iqama(it)] = s.iqama(it) }
        Prayer.obligatory.forEach { p ->
            s.iqamaFixed[p]?.let { this[K.iqFixed(p)] = it.coerceIn(0,1439) } ?: remove(K.iqFixed(p))
            this[K.iqNotify(p)] = p in s.iqamaNotifications
            this[K.iqBefore(p)] = (s.iqamaBefore[p] ?: 0).coerceIn(0,180)
        }
        this[K.jumOn] = s.jumuahEnabled
        this[K.jumFixed] = s.jumuahFixed
        this[K.jumOffset] = s.jumuahOffset.coerceIn(0,180)
        this[K.jumTime] = s.jumuahMinute.coerceIn(0,1439)
        this[K.jumNotify] = s.jumuahNotify
        this[K.jumBefore] = s.jumuahBefore.coerceIn(0,180)
        Prayer.entries.forEach { this[K.mode(it)] = s.mode(it).name }
        this[K.soundSource] = s.soundSource.name
        this[K.bundled] = s.bundledSound
        this[K.fajrBundled] = s.fajrBundledSound
        if (s.soundUri != null) this[K.soundUri] = s.soundUri else remove(K.soundUri)
        this[K.soundName] = s.soundName
        this[K.maxPlay] = s.maxPlaySeconds
        this[K.loop] = s.loopSound
        this[K.alarmStream] = s.useAlarmStream
        this[K.volume] = s.volumePercent
        this[K.snooze] = s.snoozeMinutes
        this[K.fullScreen] = s.fullScreenAlarm
        this[K.preRem] = s.preReminderMinutes
        this[K.dua] = s.adhanDua
        this[K.vibrate] = s.vibrate
        this[K.onboarding] = s.onboardingDone
    }
}
