package com.kamalahmed.prayerapp.core.prayer

import com.kamalahmed.prayerapp.core.model.Prayer

/**
 * Calculation method parameters.
 * @param ishaInterval if > 0, Isha = Maghrib + this many minutes instead of using [ishaAngle].
 * @param methodAdjustments built-in minute offsets (as published by the authority / adhan library).
 */
enum class CalculationMethod(
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaInterval: Int = 0,
    val maghribAngle: Double = 0.0,
    val methodAdjustments: Map<Prayer, Int> = emptyMap(),
) {
    /** IACAD Dubai: 18.2° / 18.2°. Verified against published Sharjah times (no extra offsets). */
    DUBAI("Authority of Dubai (UAE)", 18.2, 18.2),
    MUSLIM_WORLD_LEAGUE("Muslim World League", 18.0, 17.0, methodAdjustments = mapOf(Prayer.DHUHR to 1)),
    UMM_AL_QURA("Umm al-Qura, Makkah", 18.5, 0.0, ishaInterval = 90),
    EGYPTIAN("Egyptian General Authority", 19.5, 17.5, methodAdjustments = mapOf(Prayer.DHUHR to 1)),
    KARACHI("University of Islamic Sciences, Karachi", 18.0, 18.0, methodAdjustments = mapOf(Prayer.DHUHR to 1)),
    NORTH_AMERICA("ISNA (North America)", 15.0, 15.0, methodAdjustments = mapOf(Prayer.DHUHR to 1)),
    KUWAIT("Kuwait", 18.0, 17.5),
    QATAR("Qatar", 18.0, 0.0, ishaInterval = 90),
    GULF("Gulf Region", 19.5, 0.0, ishaInterval = 90),
    SINGAPORE("Singapore / Malaysia", 20.0, 18.0, methodAdjustments = mapOf(Prayer.DHUHR to 1)),
    TURKEY("Diyanet, Turkey", 18.0, 17.0,
        methodAdjustments = mapOf(Prayer.SUNRISE to -7, Prayer.DHUHR to 5, Prayer.ASR to 4, Prayer.MAGHRIB to 7)),
    TEHRAN("Institute of Geophysics, Tehran", 17.7, 14.0, maghribAngle = 4.5),
    MOONSIGHTING("Moonsighting Committee", 18.0, 18.0,
        methodAdjustments = mapOf(Prayer.DHUHR to 5, Prayer.MAGHRIB to 3));

    companion object {
        fun fromId(id: String): CalculationMethod = entries.firstOrNull { it.name == id } ?: DUBAI
    }
}
