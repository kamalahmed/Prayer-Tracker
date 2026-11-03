package com.kamalahmed.prayerapp.core.prayer

import com.kamalahmed.prayerapp.core.model.AsrMethod
import com.kamalahmed.prayerapp.core.model.HighLatitudeRule
import com.kamalahmed.prayerapp.core.model.Prayer
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

data class CalcParams(
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
    val method: CalculationMethod = CalculationMethod.DUBAI,
    val asr: AsrMethod = AsrMethod.STANDARD,
    val highLatRule: HighLatitudeRule = HighLatitudeRule.NONE,
    /** User minute offsets per prayer, applied after method adjustments. */
    val adjustments: Map<Prayer, Int> = emptyMap(),
)

/** Prayer times for one civil day, rounded to the minute. */
data class PrayerDay(
    val date: LocalDate,
    val times: Map<Prayer, ZonedDateTime>,
    /** Islamic midnight: half way between Maghrib and next Fajr. */
    val midnight: ZonedDateTime,
    /** Start of the last third of the night. */
    val lastThird: ZonedDateTime,
) {
    operator fun get(p: Prayer): ZonedDateTime = times.getValue(p)
}

/**
 * Offline astronomical prayer time calculator (PrayTimes / adhan style algorithm).
 * Pure Kotlin + java.time so it is fully unit-testable on the JVM.
 */
object PrayerTimesCalculator {

    fun calculate(date: LocalDate, p: CalcParams): PrayerDay {
        val today = rawTimes(date, p)
        val tomorrow = rawTimes(date.plusDays(1), p)
        val maghrib = today.getValue(Prayer.MAGHRIB)
        val nextFajr = tomorrow.getValue(Prayer.FAJR)
        val night = Duration.between(maghrib, nextFajr)
        return PrayerDay(
            date = date,
            times = today,
            midnight = roundToMinute(maghrib.plus(night.dividedBy(2))),
            lastThird = roundToMinute(maghrib.plus(night.multipliedBy(2).dividedBy(3))),
        )
    }

    /** fajr, sunrise, dhuhr, asr, sunset, maghrib, isha in UTC hours; entries may be NaN at extreme latitudes. */
    private fun hours(date: LocalDate, p: CalcParams, lat: Double): DoubleArray {
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - p.longitude / (15.0 * 24.0)

        // Two iterations: first with default day portions, then refined with computed times.
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0, 18.0) // fajr,sunrise,dhuhr,asr,sunset,maghrib,isha
        repeat(2) {
            val portion = t.map { it / 24.0 }
            t = doubleArrayOf(
                sunAngleTime(jd, p.method.fajrAngle, portion[0], lat, ccw = true),
                sunAngleTime(jd, RISE_SET_ANGLE, portion[1], lat, ccw = true),
                midDay(jd, portion[2]),
                asrTime(jd, p.asr.shadowFactor.toDouble(), portion[3], lat),
                sunAngleTime(jd, RISE_SET_ANGLE, portion[4], lat, ccw = false),
                sunAngleTime(jd, p.method.maghribAngle.takeIf { it > 0 } ?: RISE_SET_ANGLE, portion[5], lat, ccw = false),
                if (p.method.ishaInterval > 0) 0.0 else sunAngleTime(jd, p.method.ishaAngle, portion[6], lat, ccw = false),
            )
        }
        // convert from local solar to UTC hours
        for (i in t.indices) t[i] = t[i] - p.longitude / 15.0
        val sunrise = t[1]
        val sunset = t[4]
        if (p.method.ishaInterval > 0) t[6] = t[5] + p.method.ishaInterval / 60.0
        if (p.method.maghribAngle <= 0) t[5] = sunset

        // High latitude adjustments
        if (p.highLatRule != HighLatitudeRule.NONE) {
            val nightLen = timeDiff(sunset, sunrise)
            t[0] = adjustHighLat(t[0], sunrise, p.method.fajrAngle, nightLen, ccw = true, rule = p.highLatRule)
            if (p.method.ishaInterval == 0) {
                t[6] = adjustHighLat(t[6], sunset, p.method.ishaAngle, nightLen, ccw = false, rule = p.highLatRule)
            }
        }
        return t
    }

    /** Latitude used to fill in times that do not exist (polar day/night); "nearest latitude" fallback. */
    internal const val SAFE_LATITUDE = 48.5
    // Offsets from Dhuhr (hours) used only if even the safe-latitude fallback cannot produce a time.
    private val LAST_RESORT = doubleArrayOf(-6.5, -5.0, 0.0, 3.0, 5.0, 5.0, 6.5)

    private fun rawTimes(date: LocalDate, p: CalcParams): Map<Prayer, ZonedDateTime> {
        val t = hours(date, p, p.latitude)
        val usedFallback = t.any { !it.isFinite() }
        if (usedFallback && t.any { !it.isFinite() }) {
            // Polar day / polar night / twilight that never ends: the astronomical time does not exist.
            // Previously this produced NaN and crashed with "Cannot round NaN value" (London Fajr in June with
            // high-latitude rule NONE, Tromso sunrise in summer, ...). Borrow the time from the nearest safe latitude.
            val fb = hours(date, p, if (p.latitude >= 0) SAFE_LATITUDE else -SAFE_LATITUDE)
            for (i in t.indices) if (!t[i].isFinite()) t[i] = fb[i]
            for (i in t.indices) if (!t[i].isFinite()) t[i] = t[2] + LAST_RESORT[i]
            // Keep the order Fajr <= Sunrise <= Dhuhr <= Asr <= Sunset <= Maghrib <= Isha after mixing sources.
            for (i in 1 until t.size) if (t[i] < t[i - 1]) t[i] = t[i - 1]
        }

        val midnightUtc = date.atStartOfDay(ZoneId.of("UTC"))
        fun toZoned(hours: Double, prayer: Prayer): ZonedDateTime {
            val totalAdj = (p.method.methodAdjustments[prayer] ?: 0) + (p.adjustments[prayer] ?: 0)
            val seconds = (hours * 3600.0).roundToLong() + totalAdj * 60L
            return roundToMinute(midnightUtc.plusSeconds(seconds).withZoneSameInstant(p.zone))
        }
        val out = linkedMapOf(
            Prayer.FAJR to toZoned(t[0], Prayer.FAJR),
            Prayer.SUNRISE to toZoned(t[1], Prayer.SUNRISE),
            Prayer.DHUHR to toZoned(t[2], Prayer.DHUHR),
            Prayer.ASR to toZoned(t[3], Prayer.ASR),
            Prayer.MAGHRIB to toZoned(t[5], Prayer.MAGHRIB),
            Prayer.ISHA to toZoned(t[6], Prayer.ISHA),
        )
        if (usedFallback) {
            // Method offsets (e.g. Turkey's Maghrib +7 / Isha -2) can reorder compressed fallback times.
            var prev: ZonedDateTime? = null
            for (k in out.keys) { val v = out.getValue(k); if (prev != null && v.isBefore(prev)) out[k] = prev; prev = out.getValue(k) }
        }
        return out
    }

    private fun roundToMinute(z: ZonedDateTime): ZonedDateTime {
        val base = z.withSecond(0).withNano(0)
        return if (z.second >= 30) base.plusMinutes(1) else base
    }

    // ---- astronomy ----
    private const val RISE_SET_ANGLE = 0.833

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private class SunPos(val declination: Double, val equation: Double)

    private fun sunPosition(jd: Double): SunPos {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return SunPos(decl, eqt)
    }

    private fun midDay(jd: Double, time: Double): Double {
        val eqt = sunPosition(jd + time).equation
        return fixHour(12 - eqt)
    }

    private fun sunAngleTime(jd: Double, angle: Double, time: Double, lat: Double, ccw: Boolean): Double {
        val decl = sunPosition(jd + time).declination
        val noon = midDay(jd, time)
        val cosT = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
        if (cosT.isNaN() || cosT < -1 || cosT > 1) return Double.NaN
        val t = darccos(cosT) / 15.0
        return noon + if (ccw) -t else t
    }

    private fun asrTime(jd: Double, factor: Double, time: Double, lat: Double): Double {
        val decl = sunPosition(jd + time).declination
        val angle = -darccot(factor + dtan(abs(lat - decl)))
        return sunAngleTime(jd, angle, time, lat, ccw = false)
    }

    private fun adjustHighLat(
        time: Double, base: Double, angle: Double, night: Double, ccw: Boolean, rule: HighLatitudeRule,
    ): Double {
        val portion = when (rule) {
            HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
            HighLatitudeRule.SEVENTH_OF_NIGHT -> 1.0 / 7.0
            HighLatitudeRule.TWILIGHT_ANGLE -> angle / 60.0
            HighLatitudeRule.NONE -> return time
        } * night
        val diff = if (ccw) timeDiff(time, base) else timeDiff(base, time)
        return if (time.isNaN() || diff > portion) base + if (ccw) -portion else portion else time
    }

    private fun timeDiff(a: Double, b: Double) = fixHour(b - a)
    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)
    private fun fix(a: Double, b: Double): Double { val r = a - b * floor(a / b); return if (r < 0) r + b else r }
    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1 / x))
}
