package com.kamalahmed.prayerapp.ui.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.prayer.Qibla
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.Danger
import com.kamalahmed.prayerapp.ui.theme.Gold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@HiltViewModel
class QiblaViewModel @Inject constructor(repo: SettingsRepository) : ViewModel() {
    val settings: Flow<AppSettings> = repo.settings
}

/** Shortest signed angle difference a-b in (-180, 180]. */
fun angleDiff(a: Float, b: Float): Float { var d = (a - b) % 360f; if (d > 180) d -= 360; if (d <= -180) d += 360; return d }

@Composable
fun QiblaScreen(vm: QiblaViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    val ctx = LocalContext.current
    var heading by remember { mutableFloatStateOf(0f) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE) }
    var hasSensor by remember { mutableIntStateOf(1) }

    val declination = remember(s.latitude, s.longitude) {
        GeomagneticField(s.latitude.toFloat(), s.longitude.toFloat(), 0f, System.currentTimeMillis()).declination
    }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9); val remapped = FloatArray(9); val orient = FloatArray(3)
        @Suppress("DEPRECATION")
        val display = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                val (x, y) = when (display.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, x, y, remapped)
                SensorManager.getOrientation(remapped, orient)
                val magnetic = Math.toDegrees(orient[0].toDouble()).toFloat()
                val trueHeading = (magnetic + declination + 360f) % 360f
                // low-pass filter on the circle
                heading = (heading + angleDiff(trueHeading, heading) * 0.15f + 360f) % 360f
                if (e.accuracy != accuracy) accuracy = e.accuracy
            }
            override fun onAccuracyChanged(sensor: Sensor, acc: Int) { accuracy = acc }
        }
        if (sensor == null) hasSensor = 0 else sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    val qibla = Qibla.bearing(s.latitude, s.longitude).toFloat()
    val distance = Qibla.distanceKm(s.latitude, s.longitude)
    val compassRot by animateFloatAsState(-heading, tween(120), label = "compass")
    val offBy = abs(angleDiff(qibla, heading))
    val aligned = offBy < 3f

    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Qibla", fontSize = 26.sp, color = Gold)
        Text("${qibla.roundToInt()}° from true north · ${distance.roundToInt()} km to Makkah", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        AccuracyIndicator(if (hasSensor == 0) -1 else accuracy)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).semantics {
                contentDescription = if (aligned) "Facing the Qibla" else "Turn ${if (angleDiff(qibla, heading) > 0) "right" else "left"} ${offBy.roundToInt()} degrees"
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2 * 0.9f
                rotate(compassRot) {
                    drawCircle(Color(0xFF1C1C1E), r)
                    drawCircle(if (aligned) AccentGreen else Color(0xFF3A3A3C), r, style = Stroke(4.dp.toPx()))
                    for (i in 0 until 72) {
                        val major = i % 18 == 0
                        rotate(i * 5f) {
                            drawLine(
                                if (major) Color.White else Color.Gray,
                                Offset(center.x, center.y - r + 6.dp.toPx()),
                                Offset(center.x, center.y - r + (if (major) 22 else 12).dp.toPx()),
                                strokeWidth = if (major) 3.dp.toPx() else 1.dp.toPx(),
                            )
                        }
                    }
                    // north marker
                    drawCircle(Danger, 7.dp.toPx(), Offset(center.x, center.y - r + 36.dp.toPx()))
                    // qibla needle
                    rotate(qibla) {
                        val p = Path().apply {
                            moveTo(center.x, center.y - r * 0.82f)
                            lineTo(center.x - 16.dp.toPx(), center.y)
                            lineTo(center.x + 16.dp.toPx(), center.y)
                            close()
                        }
                        drawPath(p, if (aligned) AccentGreen else Gold)
                        drawRect(Color.Black, Offset(center.x - 9.dp.toPx(), center.y - r * 0.84f - 18.dp.toPx()), androidx.compose.ui.geometry.Size(18.dp.toPx(), 18.dp.toPx()))
                        drawRect(Gold, Offset(center.x - 9.dp.toPx(), center.y - r * 0.84f - 12.dp.toPx()), androidx.compose.ui.geometry.Size(18.dp.toPx(), 3.dp.toPx()))
                    }
                }
                // fixed phone-direction marker
                drawLine(Color.White, Offset(center.x, 0f), Offset(center.x, 20.dp.toPx()), strokeWidth = 4.dp.toPx())
                drawCircle(Color.White, 8.dp.toPx(), center)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (aligned) "You are facing the Qibla" else "Turn ${if (angleDiff(qibla, heading) > 0) "right" else "left"} ${offBy.roundToInt()}°",
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (aligned) AccentGreen else Color.White,
        )
        Text("Heading ${heading.roundToInt()}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Hold the phone flat, away from metal and magnets.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AccuracyIndicator(acc: Int) {
    val (label, color) = when (acc) {
        -1 -> "No compass sensor on this device" to Danger
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Compass accuracy: high" to AccentGreen
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Compass accuracy: medium" to Gold
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Accuracy low – move phone in a figure-8" to Danger
        else -> "Calibrate: move phone in a figure-8" to Danger
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}
