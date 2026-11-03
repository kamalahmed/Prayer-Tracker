package com.kamalahmed.prayerapp.ui.prayers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.kamalahmed.prayerapp.ui.adaptive.AdaptiveMath
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import com.kamalahmed.prayerapp.ui.adaptive.SizedBox
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
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
    if (LocalAdaptive.current.twoPane) PrayersTwoPane(s, trackerContent) else PrayersSinglePane(s, trackerContent)
}

private fun PrayersUiState.ev(e: PrayerEvent) = "${e.prayer.label(settings.abbreviations)}  ${TimeFormat.time(e.time, settings.use24h)}"

@Composable
private fun PrayersSinglePane(s: PrayersUiState, trackerContent: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            s.others.getOrNull(0)?.let { Text(s.ev(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            s.others.getOrNull(1)?.let { Text(s.ev(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(8.dp))
        Text(s.hijri, color = Gold, fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(s.settings.cityName, color = Gold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        // The ring takes whatever is left, sized to the smaller dimension so it is never cropped.
        SizedBox(Modifier.fillMaxWidth().weight(1f)) { w, h ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Ring(s, AdaptiveMath.dialDiameter(w, h, minDp = 160f))
            }
        }
        trackerContent()
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            s.others.getOrNull(2)?.let { Text(s.ev(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            s.others.getOrNull(3)?.let { Text(s.ev(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PrayersTwoPane(s: PrayersUiState, trackerContent: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        SizedBox(Modifier.weight(1.1f).fillMaxHeight()) { w, h ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Ring(s, AdaptiveMath.dialDiameter(w, h)) }
        }
        Spacer(Modifier.size(16.dp))
        Column(
            Modifier.weight(1f).widthIn(max = 520.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(s.hijri, color = Gold, fontSize = 26.sp)
            Text(s.settings.cityName, color = Gold, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            CardGroup {
                s.day.forEachIndexed { i, e ->
                    if (i > 0) RowDivider()
                    val isNext = e.prayer == s.next.prayer
                    ListRow(e.prayer.label(s.settings.abbreviations), showChevron = false) {
                        Text(
                            TimeFormat.time(e.time, s.settings.use24h),
                            color = if (isNext) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            trackerContent()
        }
    }
}

@Composable
private fun Ring(s: PrayersUiState, diameter: Float) {
    val use24 = s.settings.use24h
    val label = if (s.countUp != null) "since ${s.previous.prayer.displayName}" else "left until"
    val big = if (s.countUp != null) "+" + TimeFormat.countdown(s.countUp) else TimeFormat.countdown(s.remaining)
    // Text scales with the ring so it always fits inside it.
    val k = (diameter / 340f).coerceIn(0.55f, 1.5f)
    Box(
        Modifier.size(diameter.dp).semantics {
            contentDescription = "$big $label ${s.next.prayer.displayName} at ${TimeFormat.time(s.next.time, use24)}"
        },
        contentAlignment = Alignment.Center,
    ) {
        CountdownRing(s.progress, (26f * k).coerceIn(14f, 32f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, fontSize = (38 * k).sp, fontWeight = FontWeight.Light)
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height((8 * k).dp))
            Text(s.next.prayer.displayName, fontSize = (44 * k).sp, fontWeight = FontWeight.Bold)
            Text(TimeFormat.time(s.next.time, use24), style = MaterialTheme.typography.bodyLarge)
            s.iqamaRemaining?.let {
                Spacer(Modifier.height(6.dp))
                Text("Iqama in ${TimeFormat.countdown(it)}", color = AccentGreen, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CountdownRing(progress: Float, strokeDp: Float) {
    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
        val stroke = strokeDp.dp.toPx()
        val r = (size.minDimension - stroke * 1.2f) / 2
        val tl = Offset(center.x - r, center.y - r)
        val arc = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        drawArc(AccentGreenDim.copy(alpha = 0.45f), 0f, 360f, false, tl, arc, style = Stroke(stroke))
        drawArc(AccentGreen, -90f, 360f * progress, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        val ang = Math.toRadians((-90.0 + 360.0 * progress))
        drawCircle(Gold, stroke * 0.55f, Offset(center.x + r * cos(ang).toFloat(), center.y + r * sin(ang).toFloat()))
    }
}
