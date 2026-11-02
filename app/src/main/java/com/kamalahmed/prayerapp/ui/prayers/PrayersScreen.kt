package com.kamalahmed.prayerapp.ui.prayers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.core.prayer.PrayerEvent
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.AccentGreenDim
import com.kamalahmed.prayerapp.ui.theme.Gold
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PrayersScreen(
    vm: PrayersViewModel = hiltViewModel(),
    trackerContent: @Composable () -> Unit = {},
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val use24 = s.settings.use24h
    val abbr = s.settings.abbreviations
    fun ev(e: PrayerEvent) = "${e.prayer.label(abbr)}  ${TimeFormat.time(e.time, use24)}"

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            s.others.getOrNull(0)?.let { Text(ev(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            s.others.getOrNull(1)?.let { Text(ev(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(8.dp))
        Text(s.hijri, color = Gold, fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(s.settings.cityName, color = Gold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))

        val label = if (s.countUp != null) "since ${s.previous.prayer.displayName}" else "left until"
        val big = if (s.countUp != null) "+" + TimeFormat.countdown(s.countUp) else TimeFormat.countdown(s.remaining)
        Box(
            Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1f).semantics {
                contentDescription = "$big $label ${s.next.prayer.displayName} at ${TimeFormat.time(s.next.time, use24)}"
            },
            contentAlignment = Alignment.Center,
        ) {
            CountdownRing(s.progress)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(big, fontSize = 38.sp, fontWeight = FontWeight.Light)
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(s.next.prayer.displayName, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text(TimeFormat.time(s.next.time, use24), style = MaterialTheme.typography.bodyLarge)
                s.iqamaRemaining?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Iqama in ${TimeFormat.countdown(it)}", color = AccentGreen, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        trackerContent()
        Spacer(Modifier.weight(0.01f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            s.others.getOrNull(2)?.let { Text(ev(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            s.others.getOrNull(3)?.let { Text(ev(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CountdownRing(progress: Float) {
    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
        val stroke = 26.dp.toPx()
        val r = (size.minDimension - stroke) / 2
        val tl = Offset(center.x - r, center.y - r)
        val arc = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        drawArc(AccentGreenDim.copy(alpha = 0.45f), 0f, 360f, false, tl, arc, style = Stroke(stroke))
        drawArc(AccentGreen, -90f, 360f * progress, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        val ang = Math.toRadians((-90.0 + 360.0 * progress))
        drawCircle(Gold, stroke * 0.55f, Offset(center.x + r * cos(ang).toFloat(), center.y + r * sin(ang).toFloat()))
    }
}
