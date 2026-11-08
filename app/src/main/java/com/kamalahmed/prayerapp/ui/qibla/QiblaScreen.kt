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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kamalahmed.prayerapp.ui.adaptive.AdaptiveMath
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import com.kamalahmed.prayerapp.ui.adaptive.SizedBox
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
fun QiblaScreen(vm: QiblaViewModel = hiltViewModel(), onOpenLocation: () -> Unit = {}) {
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    val ctx = LocalContext.current
    val declination = remember(s.latitude, s.longitude) {
        runCatching { GeomagneticField(s.latitude.toFloat(), s.longitude.toFloat(), 0f, System.currentTimeMillis()).declination }.getOrDefault(0f)
    }
    val reading = rememberCompassReading(ctx, declination)
    val heading = reading.heading
    val status = reading.status
    val live = status == CompassStatus.OK || status == CompassStatus.CALIBRATE
    val qibla = Qibla.bearing(s.latitude, s.longitude).toFloat()
    val distance = Qibla.distanceKm(s.latitude, s.longitude)
    val compassRot by animateFloatAsState(-heading, tween(120), label = "compass")
    val offBy = abs(angleDiff(qibla, heading))
    val aligned = offBy < 3f

    val turnText = if (aligned) "You are facing the Qibla" else "Turn ${if (angleDiff(qibla, heading) > 0) "right" else "left"} ${offBy.roundToInt()}°"
    val compass: @Composable (Float) -> Unit = { d ->
        Box(
            Modifier.size(d.dp).semantics {
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
    }
    val header: @Composable () -> Unit = {
        Text("Qibla", fontSize = 26.sp, color = Gold)
        Text("${qibla.roundToInt()}° from true north · ${distance.roundToInt()} km to Makkah", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        CompassStatusBanner(status, reading.source)
        LocationNote(s, onOpenLocation)
    }
    val footer: @Composable () -> Unit = {
        if (live) {
            Text(turnText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (aligned) AccentGreen else Color.White)
            Text("Heading ${heading.roundToInt()}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Qibla bearing ${qibla.roundToInt()}° (clockwise from true north)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Gold)
            Text(
                "Use this bearing with any compass. The dial below is drawn with north at the top.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Hold the phone flat, away from metal and magnets.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (LocalAdaptive.current.twoPane) {
        Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SizedBox(Modifier.weight(1.2f).fillMaxHeight()) { w, h ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { compass(AdaptiveMath.dialDiameter(w, h)) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                header()
                Spacer(Modifier.height(24.dp))
                footer()
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            header()
            Spacer(Modifier.height(12.dp))
            SizedBox(Modifier.fillMaxWidth().weight(1f)) { w, h ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { compass(AdaptiveMath.dialDiameter(w, h, minDp = 160f)) }
            }
            Spacer(Modifier.height(12.dp))
            footer()
        }
    }
}

@Composable
private fun CompassStatusBanner(status: CompassStatus, source: CompassSource?) {
    val (label, color) = when (status) {
        CompassStatus.OK -> "Compass ready" to AccentGreen
        CompassStatus.WAITING -> "Starting compass…" to Gold
        CompassStatus.CALIBRATE -> "Calibration needed: move the phone in a figure-8 a few times" to Gold
        CompassStatus.NO_DATA -> "The compass sensor is not sending data. Close other compass apps, then reopen this tab, or restart the phone." to Danger
        CompassStatus.NO_MAGNETOMETER -> "This phone has no magnetic sensor, so the direction can't be shown live." to Danger
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
    if (source != null && source != CompassSource.ROTATION_VECTOR && status != CompassStatus.NO_MAGNETOMETER) {
        Text("Sensor: ${source.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LocationNote(s: AppSettings, onOpenLocation: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(ctx)) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = hasLocationPermission(ctx) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Location: ${s.cityName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.TextButton(onClick = onOpenLocation) { Text("Change") }
    }
    if (s.autoLocation && !granted) {
        Text(
            "Automatic location is on but location permission is off, so the Qibla uses your last saved place.",
            style = MaterialTheme.typography.bodySmall, color = Gold,
        )
        androidx.compose.material3.TextButton(onClick = {
            runCatching {
                launcher.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }) { Text("Allow location") }
    }
}

private fun hasLocationPermission(ctx: Context) =
    androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
