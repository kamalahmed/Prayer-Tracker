package com.kamalahmed.prayerapp.ui.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Stable
class CompassReading {
    var heading by mutableFloatStateOf(0f)
    var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    var source by mutableStateOf<CompassSource?>(null)
    var gotData by mutableStateOf(false)
    var startedAt by mutableLongStateOf(SystemClock.elapsedRealtime())
    var now by mutableLongStateOf(SystemClock.elapsedRealtime())
    var unavailable by mutableStateOf(false)
    val status: CompassStatus get() =
        if (unavailable) CompassStatus.NO_MAGNETOMETER else CompassMath.status(source, gotData, accuracy, now - startedAt)
}

/** Registers the best available heading source (see [CompassSelector]) and falls back when one fails or stays silent. */
@Composable
fun rememberCompassReading(ctx: Context, declination: Float): CompassReading {
    val reading = remember { CompassReading() }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500); reading.now = SystemClock.elapsedRealtime() } }

    DisposableEffect(attempt) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val available = sm?.getSensorList(Sensor.TYPE_ALL)?.map { it.type }?.toSet().orEmpty()
        val chain = CompassSelector.chain(available)
        val src = chain.getOrNull(attempt)
        reading.source = src; reading.gotData = false; reading.startedAt = SystemClock.elapsedRealtime()
        reading.unavailable = src == null
        if (sm == null || src == null) return@DisposableEffect onDispose { }

        @Suppress("DEPRECATION")
        val display = runCatching { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay }.getOrNull()
        val rot = FloatArray(9)
        var gravity: FloatArray? = null
        var magnetic: FloatArray? = null
        var smoothed: Float? = null

        fun publish(r: FloatArray) {
            val az = CompassMath.azimuthFromMatrix(r, display?.rotation ?: 0) ?: return
            val h = CompassMath.smoothHeading(smoothed, CompassMath.trueHeading(az, declination))
            smoothed = h; reading.heading = h; reading.gotData = true
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                runCatching {
                    when (e.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rot, e.values); publish(rot)
                        }
                        Sensor.TYPE_ACCELEROMETER -> gravity = CompassMath.lowPass(e.values.copyOf(3), gravity)
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            magnetic = CompassMath.lowPass(e.values.copyOf(3), magnetic)
                            val g = gravity; val m = magnetic
                            if (src == CompassSource.ACCEL_MAGNETOMETER && g != null && m != null) {
                                CompassMath.rotationMatrix(g, m)?.let(::publish)
                            }
                        }
                    }
                }.onFailure { Log.w("Compass", "sensor event", it) }
            }
            override fun onAccuracyChanged(sensor: Sensor, acc: Int) {
                // Calibration state comes from the magnetometer; fused sensors often report a meaningless 0.
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) reading.accuracy = acc
            }
        }
        val types = when (src) {
            CompassSource.ROTATION_VECTOR -> listOf(Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_MAGNETIC_FIELD)
            CompassSource.GEOMAGNETIC_ROTATION_VECTOR -> listOf(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_MAGNETIC_FIELD)
            CompassSource.ACCEL_MAGNETOMETER -> listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD)
        }
        val primaryOk = types.mapIndexed { i, t ->
            val ok = runCatching { sm.getDefaultSensor(t)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) } ?: false }.getOrDefault(false)
            i > 0 || ok
        }.all { it }
        if (!primaryOk && attempt + 1 < chain.size) attempt++
        onDispose { runCatching { sm.unregisterListener(listener) } }
    }
    // A source that registers but never delivers events (seen on some budget phones): try the next one.
    LaunchedEffect(attempt) {
        delay(3_000)
        val chainSize = (ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getSensorList(Sensor.TYPE_ALL)?.map { it.type }?.toSet()?.let { CompassSelector.chain(it).size } ?: 0
        if (!reading.gotData && attempt + 1 < chainSize) attempt++
    }
    return reading
}
