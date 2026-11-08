package com.kamalahmed.prayerapp.qibla

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import com.kamalahmed.prayerapp.ui.qibla.CompassMath
import com.kamalahmed.prayerapp.ui.qibla.CompassSelector
import com.kamalahmed.prayerapp.ui.qibla.CompassSource
import com.kamalahmed.prayerapp.ui.qibla.CompassStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CompassTest {
    private val fold = setOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val a15 = setOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY)

    @Test fun picksRotationVectorWhenPresent() = assertEquals(CompassSource.ROTATION_VECTOR, CompassSelector.choose(fold))
    @Test fun budgetPhoneWithoutGyroFallsBackToAccelMag() = assertEquals(CompassSource.ACCEL_MAGNETOMETER, CompassSelector.choose(a15))
    @Test fun geomagneticRvPreferredOverRaw() =
        assertEquals(CompassSource.GEOMAGNETIC_ROTATION_VECTOR, CompassSelector.choose(a15 + Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR))
    @Test fun noMagnetometerIsNull() = assertNull(CompassSelector.choose(setOf(Sensor.TYPE_ACCELEROMETER)))
    @Test fun chainIsOrdered() = assertEquals(CompassSource.entries, CompassSelector.chain(fold))

    /** Phone flat, screen up; device y-axis points at [azimuth] degrees from magnetic north. */
    private fun flatField(azimuth: Double): Pair<FloatArray, FloatArray> {
        val a = Math.toRadians(azimuth)
        // Earth's north horizontal component expressed in device coords, plus a downward (dip) component.
        val north = floatArrayOf((-sin(a)).toFloat() * 20f, cos(a).toFloat() * 20f, -40f)
        return floatArrayOf(0f, 0f, 9.81f) to north
    }

    @Test fun accelMagHeadingMatchesForAllDirections() {
        for (az in listOf(0.0, 45.0, 90.0, 180.0, 258.0, 300.0)) {
            val (g, m) = flatField(az)
            val r = CompassMath.rotationMatrix(g, m)!!
            val h = CompassMath.azimuthFromMatrix(r, Surface.ROTATION_0)!!
            assertEquals("az=$az", az.toFloat(), h, 0.5f)
        }
    }

    @Test fun landscapeRotationShiftsBy90() {
        val (g, m) = flatField(0.0)
        val r = CompassMath.rotationMatrix(g, m)!!
        assertEquals(270f, CompassMath.azimuthFromMatrix(r, Surface.ROTATION_90)!!, 0.5f)
        assertEquals(90f, CompassMath.azimuthFromMatrix(r, Surface.ROTATION_270)!!, 0.5f)
    }

    @Test fun degenerateInputIsNull() = assertNull(CompassMath.rotationMatrix(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f)))

    @Test fun smoothingWrapsAround() {
        val h = CompassMath.smoothHeading(359f, 1f, 0.5f)
        assertEquals(0f, if (h > 180) h - 360 else h, 0.01f)
    }

    @Test fun declinationApplied() = assertEquals(2f, CompassMath.trueHeading(358f, 4f), 0.01f)

    @Test fun statuses() {
        assertEquals(CompassStatus.NO_MAGNETOMETER, CompassMath.status(null, false, 3, 0))
        assertEquals(CompassStatus.WAITING, CompassMath.status(CompassSource.ACCEL_MAGNETOMETER, false, 3, 100))
        assertEquals(CompassStatus.NO_DATA, CompassMath.status(CompassSource.ACCEL_MAGNETOMETER, false, 3, 5_000))
        assertEquals(CompassStatus.CALIBRATE, CompassMath.status(CompassSource.ACCEL_MAGNETOMETER, true, SensorManager.SENSOR_STATUS_UNRELIABLE, 0))
        assertEquals(CompassStatus.CALIBRATE, CompassMath.status(CompassSource.ROTATION_VECTOR, true, SensorManager.SENSOR_STATUS_ACCURACY_LOW, 0))
        assertEquals(CompassStatus.OK, CompassMath.status(CompassSource.ROTATION_VECTOR, true, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, 0))
    }
}
