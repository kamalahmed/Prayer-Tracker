package com.kamalahmed.prayerapp.core.model

/** The five daily prayers plus Sunrise (shown in lists but not a prayer). */
enum class Prayer(val displayName: String, val abbr: String) {
    FAJR("Fajr", "FJR"),
    SUNRISE("Sunrise", "SUN"),
    DHUHR("Dhuhr", "DHR"),
    ASR("Asr", "ASR"),
    MAGHRIB("Maghrib", "MGB"),
    ISHA("Isha", "ISH");

    val isPrayer: Boolean get() = this != SUNRISE

    fun label(abbreviated: Boolean) = if (abbreviated) abbr else displayName

    companion object {
        val obligatory = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
    }
}

/** What happens when a prayer time arrives. */
enum class NotifyMode(val label: String) {
    OFF("Off"),
    SILENT("Silent notification"),
    BEEP("Beep"),
    ADHAN("Adhan / sound"),
}

enum class AsrMethod(val label: String, val shadowFactor: Int) {
    STANDARD("Standard (Shafi'i, Maliki, Hanbali)", 1),
    HANAFI("Hanafi", 2),
}

enum class HighLatitudeRule(val label: String) {
    NONE("None"),
    MIDDLE_OF_NIGHT("Middle of the night"),
    SEVENTH_OF_NIGHT("One seventh of the night"),
    TWILIGHT_ANGLE("Twilight angle"),
}

/** Where the adhan / alarm sound comes from. */
enum class SoundSource { BUNDLED, RINGTONE, CUSTOM_FILE }
