package com.kamalahmed.prayerapp.core.prayer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.asin
import kotlin.math.sqrt

object Qibla {
    const val KAABA_LAT = 21.4225
    const val KAABA_LNG = 39.8262

    /** Initial great-circle bearing from the given point to the Kaaba, degrees clockwise from true north. */
    fun bearing(lat: Double, lng: Double): Double {
        val phi = Math.toRadians(lat)
        val dLambda = Math.toRadians(KAABA_LNG - lng)
        val y = sin(dLambda)
        val x = cos(phi) * tan(Math.toRadians(KAABA_LAT)) - sin(phi) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun distanceKm(lat: Double, lng: Double): Double = Geo.distanceKm(lat, lng, KAABA_LAT, KAABA_LNG)
}

object Geo {
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(a))
    }
}
