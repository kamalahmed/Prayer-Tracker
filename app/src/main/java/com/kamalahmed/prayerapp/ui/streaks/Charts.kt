package com.kamalahmed.prayerapp.ui.streaks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalahmed.prayerapp.ui.adaptive.SizedBox
import com.kamalahmed.prayerapp.ui.theme.Card
import com.kamalahmed.prayerapp.ui.theme.CardHigh
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** Shared dashboard building blocks (Stats and Qada). Pure drawing; no data access. */

@Composable
fun Panel(title: String? = null, subtitle: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (title != null || subtitle != null) Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun KpiCard(label: String, value: String, caption: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Card).padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** KPI cards: 2 per row on phones, all in one row when there is room. */
@Composable
fun KpiGrid(items: List<@Composable (Modifier) -> Unit>) {
    SizedBox(Modifier.fillMaxWidth()) { w, _ ->
        val perRow = if (w >= 560f) items.size else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { it(Modifier.weight(1f)) }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun Legend(items: List<Pair<String, Color>>) {
    Row(Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (l, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)); Spacer(Modifier.width(5.dp))
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Nice" axis maximum: 1, 2, 5 x 10^n at or above [v]. */
fun niceMax(v: Int): Int {
    if (v <= 5) return max(v, 1).let { if (it <= 5) 5 else it }
    var m = 1
    while (true) {
        for (k in intArrayOf(1, 2, 5)) if (k * m >= v) return k * m
        m *= 10
    }
}

/** Which x labels to show so they never overlap: every [step]th. */
fun labelStep(count: Int, widthDp: Float, minLabelDp: Float = 28f): Int =
    if (count <= 0) 1 else max(1, ceil(count * minLabelDp / max(widthDp, 1f)).toInt())

data class BarSlot(val label: String, val segments: List<Pair<Int, Color>>, val tracked: Boolean, val describe: String)

/**
 * Stacked bar chart with a y axis (0 / mid / max), grid lines and non-overlapping x labels.
 * Untracked slots are drawn as a dashed outline, so they never look like real zeros.
 */
@Composable
fun StackedBarChart(slots: List<BarSlot>, yMax: Int? = null, height: Dp = 170.dp, line: List<Int?>? = null, lineColor: Color = Color.White) {
    if (slots.isEmpty()) return
    val top = yMax ?: niceMax(slots.maxOf { s -> s.segments.sumOf { it.first } })
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.height(height).width(30.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            listOf(top, top / 2, 0).forEach { Text("$it", fontSize = 10.sp, color = axisColor, maxLines = 1) }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(height).semantics {
                contentDescription = "Chart: " + slots.filter { it.tracked }.joinToString { it.describe }
            }) {
                val h = size.height
                val n = slots.size
                val slot = size.width / n
                val bw = (slot * 0.64f).coerceAtMost(26.dp.toPx()).coerceAtLeast(2f)
                for (i in 0..2) drawLine(CardHigh, Offset(0f, h * i / 2), Offset(size.width, h * i / 2), 1f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                slots.forEachIndexed { i, s ->
                    val x = slot * i + (slot - bw) / 2
                    if (!s.tracked) {
                        val sh = h * 0.18f
                        drawRoundRect(CardHigh, Offset(x, h - sh), Size(bw, sh), CornerRadius(4f), style = Stroke(1.dp.toPx(), pathEffect = dash))
                        return@forEachIndexed
                    }
                    var y = h
                    s.segments.forEach { (v, c) ->
                        if (v <= 0) return@forEach
                        val sh = (h * v / top).coerceAtMost(y)
                        drawRoundRect(c, Offset(x, y - sh), Size(bw, sh), CornerRadius(3.dp.toPx())); y -= sh
                    }
                }
                if (line != null) {
                    val pts = line.mapIndexedNotNull { i, v -> v?.let { Offset(slot * i + slot / 2, h - h * it.coerceIn(0, 100) / 100f) } }
                    if (pts.size >= 2) {
                        val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(p, lineColor.copy(alpha = 0.85f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    }
                    pts.forEach { drawCircle(lineColor, 2.5.dp.toPx(), it) }
                }
            }
            SizedBox(Modifier.fillMaxWidth()) { w, _ ->
                val step = labelStep(slots.size, w)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    slots.forEachIndexed { i, s ->
                        Text(
                            if (i % step == 0) s.label else "", Modifier.weight(1f), fontSize = 10.sp, maxLines = 1,
                            textAlign = TextAlign.Center, color = if (s.tracked) axisColor else axisColor.copy(alpha = 0.5f),
                            softWrap = false, overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}

/** 0..100 line chart with an axis, for rates over time. Null points are gaps (no data). */
@Composable
fun RateLineChart(points: List<Pair<String, List<Int?>>>, labels: List<String>, colors: List<Color>, height: Dp = 140.dp) {
    if (labels.isEmpty()) return
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.height(height).width(30.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            listOf("100%", "50%", "0%").forEach { Text(it, fontSize = 10.sp, color = axisColor, maxLines = 1) }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = "Rate chart" }) {
                val h = size.height; val n = labels.size; val slot = size.width / n
                for (i in 0..2) drawLine(CardHigh, Offset(0f, h * i / 2), Offset(size.width, h * i / 2), 1f)
                points.forEachIndexed { k, (_, values) ->
                    val c = colors[k % colors.size]
                    var path: Path? = null
                    values.forEachIndexed { i, v ->
                        if (v == null) { path?.let { drawPath(it, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }; path = null; return@forEachIndexed }
                        val o = Offset(slot * i + slot / 2, h - h * v.coerceIn(0, 100) / 100f)
                        path = (path ?: Path().apply { moveTo(o.x, o.y) }).apply { lineTo(o.x, o.y) }
                        drawCircle(c, 2.5.dp.toPx(), o)
                    }
                    path?.let { drawPath(it, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
                }
            }
            SizedBox(Modifier.fillMaxWidth()) { w, _ ->
                val step = labelStep(labels.size, w)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    labels.forEachIndexed { i, l ->
                        Text(if (i % step == 0) l else "", Modifier.weight(1f), fontSize = 10.sp, maxLines = 1, softWrap = false,
                            textAlign = TextAlign.Center, color = axisColor)
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp, color: Color, track: Color = CardHigh, stroke: Dp = 14.dp, center: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val d = this.size.minDimension - sw
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, -90f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            if (progress > 0f) drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        center()
    }
}

/** Colour scale for 0..5 prayers prayed; null = no data. */
fun heatColor(v: Int?, good: Color, bad: Color): Color = when {
    v == null -> CardHigh.copy(alpha = 0.35f)
    v == 0 -> bad.copy(alpha = 0.45f)
    else -> good.copy(alpha = 0.22f + 0.156f * v.coerceAtMost(5))
}

/**
 * GitHub-style year grid: one column per week (Mon..Sun rows), month labels on top, weekday labels on the left.
 * Cells scale to the width; below a readable minimum the grid scrolls horizontally instead of shrinking.
 */
@Composable
fun ContributionGrid(cells: List<Pair<LocalDate, Int?>>, today: LocalDate, good: Color, bad: Color, onDay: (LocalDate) -> Unit, locale: Locale = Locale.getDefault(), maxCount: Int = 5, unit: String = "prayed") {
    if (cells.isEmpty()) return
    val lead = cells.first().first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val padded: List<Pair<LocalDate, Int?>?> = List(lead) { null } + cells
    val weeks = padded.chunked(7)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    SizedBox(Modifier.fillMaxWidth()) { w, _ ->
        val labelW = 22f; val gap = 2f
        val fit = (w - labelW) / weeks.size - gap
        val cell = fit.coerceIn(10f, 18f)
        val scroll = fit < 10f
        Row {
            Column(Modifier.width(labelW.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                listOf("M", "", "W", "", "F", "", "").forEach {
                    Box(Modifier.height(cell.dp), contentAlignment = Alignment.CenterStart) { Text(it, fontSize = 9.sp, color = axisColor) }
                }
            }
            Row(
                (if (scroll) Modifier.horizontalScroll(rememberScrollState(Int.MAX_VALUE)) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(gap.dp),
            ) {
                weeks.forEachIndexed { wi, week ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                        val days = week.filterNotNull()
                        // Label a column when a month starts in it (skip the very first column if the next month starts within 2 weeks).
                        val showMonth = days.any { it.first.dayOfMonth == 1 } || (wi == 0 && days.isNotEmpty() && days.first().first.dayOfMonth <= 14)
                        Box(Modifier.height(12.dp).width(cell.dp)) {
                            if (showMonth) {
                                val m = days.firstOrNull { it.first.dayOfMonth == 1 }?.first ?: days.first().first
                                Text(m.month.getDisplayName(TextStyle.SHORT, locale), fontSize = 9.sp, color = axisColor, maxLines = 1, softWrap = false,
                                    overflow = TextOverflow.Visible, modifier = Modifier.width((cell * 3).dp))
                            }
                        }
                        (0 until 7).forEach { i ->
                            val c = week.getOrNull(i)
                            Box(
                                Modifier.size(cell.dp).clip(RoundedCornerShape(3.dp))
                                    .background(if (c == null) Color.Transparent else heatColor(c.second?.let { ((it * 5f / maxCount.coerceAtLeast(1))).toInt().coerceAtLeast(if (it > 0) 1 else 0) }, good, bad))
                                    .then(if (c != null && !c.first.isAfter(today)) Modifier.clickable { onDay(c.first) } else Modifier)
                                    .semantics { if (c != null) contentDescription = "${c.first}: ${c.second?.let { "$it of $maxCount $unit" } ?: "no data"}" },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeatScale(good: Color, bad: Color) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("No data", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(heatColor(null, good, bad)))
        Spacer(Modifier.width(8.dp))
        Text("0", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0..5).forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(heatColor(it, good, bad))) }
        Text("5 prayed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
