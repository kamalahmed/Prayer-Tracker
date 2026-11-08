package com.kamalahmed.prayerapp.ui.qibla

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * How the heading is obtained. Beta.5 only used [ROTATION_VECTOR], which is a gyroscope fusion sensor that many
 * budget phones (e.g. Galaxy A15, no gyroscope) do not expose, so the Qibla tab claimed "no compass" on phones
 * that do have a magnetometer.
 */
enum class CompassSource(val label: String) {
    ROTATION_VECTOR("Rotation vector (gyro + magnetometer)"),
    GEOMAGNETIC_ROTATION_VECTOR("Geomagnetic rotation vector"),
    ACCEL_MAGNETOMETER("Accelerometer + magnetometer"),
}

/** Picks the best available source from the sensor types the device reports. Null = no magnetometer at all. */
object CompassSelector {
    fun choose(available: Set<Int>): CompassSource? = when {
        Sensor.TYPE_ROTATION_VECTOR in available -> CompassSource.ROTATION_VECTOR
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR in available -> CompassSource.GEOMAGNETIC_ROTATION_VECTOR
        Sensor.TYPE_MAGNETIC_FIELD in available && Sensor.TYPE_ACCELEROMETER in available -> CompassSource.ACCEL_MAGNETOMETER
        else -> null
    }

    /** Ordered fallbacks after [first] (used when registering a listener fails or delivers nothing). */
    fun chain(available: Set<Int>): List<CompassSource> = CompassSource.entries.filter { src ->
        when (src) {
            CompassSource.ROTATION_VECTOR -> Sensor.TYPE_ROTATION_VECTOR in available
            CompassSource.GEOMAGNETIC_ROTATION_VECTOR -> Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR in available
            CompassSource.ACCEL_MAGNETOMETER -> Sensor.TYPE_MAGNETIC_FIELD in available && Sensor.TYPE_ACCELEROMETER in available
        }
    }
}

/** What the Qibla screen should tell the user. */
enum class CompassStatus { WAITING, OK, CALIBRATE, NO_DATA, NO_MAGNETOMETER }

object CompassMath {
    /** Screen "up" direction in device coordinates for a display rotation. */
    fun screenUp(rotation: Int): FloatArray = when (rotation) {
        Surface.ROTATION_90 -> floatArrayOf(-1f, 0f, 0f)
        Surface.ROTATION_180 -> floatArrayOf(0f, -1f, 0f)
        Surface.ROTATION_270 -> floatArrayOf(1f, 0f, 0f)
        else -> floatArrayOf(0f, 1f, 0f)
    }

    /**
     * Magnetic azimuth (0..360, clockwise from magnetic north) of the screen-up direction, from a rotation
     * matrix whose rows are East, North, Up expressed in device coordinates (as SensorManager returns).
     */
    fun azimuthFromMatrix(r: FloatArray, rotation: Int): Float? {
        val u = screenUp(rotation)
        val e = r[0] * u[0] + r[1] * u[1] + r[2] * u[2]
        val n = r[3] * u[0] + r[4] * u[1] + r[5] * u[2]
        if (e * e + n * n < 1e-6f) return null // screen-up points straight up/down: azimuth undefined
        return norm(Math.toDegrees(atan2(e.toDouble(), n.toDouble())).toFloat())
    }

    /** Same as SensorManager.getRotationMatrix (rows E, N, Up), but pure Kotlin so it is unit-testable. Null when degenerate. */
    fun rotationMatrix(gravity: FloatArray, geomagnetic: FloatArray): FloatArray? {
        val (ax, ay, az) = gravity
        val (ex, ey, ez) = geomagnetic
        var hx = ey * az - ez * ay; var hy = ez * ax - ex * az; var hz = ex * ay - ey * ax
        val normH = sqrt(hx * hx + hy * hy + hz * hz)
        val normA = sqrt(ax * ax + ay * ay + az * az)
        if (normH < 0.1f || normA < 0.1f) return null // free fall or in a strong field / near magnetic pole
        hx /= normH; hy /= normH; hz /= normH
        val gx = ax / normA; val gy = ay / normA; val gz = az / normA
        val mx = gy * hz - gz * hy; val my = gz * hx - gx * hz; val mz = gx * hy - gy * hx
        return floatArrayOf(hx, hy, hz, mx, my, mz, gx, gy, gz)
    }

    /** Low-pass filter for vectors (alpha = weight of the new sample). */
    fun lowPass(input: FloatArray, prev: FloatArray?, alpha: Float = 0.15f): FloatArray =
        if (prev == null) input.copyOf() else FloatArray(input.size) { prev[it] + alpha * (input[it] - prev[it]) }

    /** Low-pass on the circle, so 359° -> 1° does not swing through 180°. */
    fun smoothHeading(prev: Float?, next: Float, alpha: Float = 0.15f): Float =
        if (prev == null) norm(next) else norm(prev + angleDiff(next, prev) * alpha)

    fun trueHeading(magnetic: Float, declination: Float) = norm(magnetic + declination)

    fun status(source: CompassSource?, gotData: Boolean, accuracy: Int, waitedMs: Long): CompassStatus = when {
        source == null -> CompassStatus.NO_MAGNETOMETER
        !gotData -> if (waitedMs > 3_000) CompassStatus.NO_DATA else CompassStatus.WAITING
        accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassStatus.CALIBRATE
        else -> CompassStatus.OK
    }

    fun norm(d: Float): Float { val x = d % 360f; return if (x < 0) x + 360f else x }
}
