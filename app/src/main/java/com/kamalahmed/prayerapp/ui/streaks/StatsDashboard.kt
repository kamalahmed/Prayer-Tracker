package com.kamalahmed.prayerapp.ui.streaks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.tracker.Counts
import com.kamalahmed.prayerapp.tracker.PeriodReport
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.StatsPeriod
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.Card
import com.kamalahmed.prayerapp.ui.theme.CardHigh
import com.kamalahmed.prayerapp.ui.theme.Danger
import com.kamalahmed.prayerapp.ui.theme.Gold
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Streaks tab: streaks, today, and the week / month / year statistics report. */
@Composable
fun StreaksScreen(vm: StreaksViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val rep by vm.report.collectAsStateWithLifecycle()
    val runs by vm.streakHistory.collectAsStateWithLifecycle()
    val all by vm.history.collectAsStateWithLifecycle()
    var editDay by remember { mutableStateOf<LocalDate?>(null) }
    val s = st ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Streaks", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Current streak", "${s.stats.currentDays}", "days", Modifier.weight(1f))
            StatCard("Best streak", "${s.stats.bestDays}", "days", Modifier.weight(1f))
            StatCard("In a row", "${s.stats.currentPrayers}", "prayers", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Panel {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Text("Tap a prayer once it has started. Tap a day in the calendar below to edit past days.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            TodayTracker(vm, abbreviated = true)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(selected = rep?.period == p, onClick = { vm.setPeriod(p) }, label = { Text(p.label) })
            }
        }
        val r = rep ?: return@Column
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous ${r.period.label}") }
            Text(r.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { vm.shift(1) }, enabled = r.end.isBefore(s.today)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next ${r.period.label}")
            }
        }
        val blocks = mutableListOf<@Composable () -> Unit>()
        if (r.isEmpty) {
        blocks += {
            Panel {
                Text("No data for this ${r.period.label.lowercase()} yet", style = MaterialTheme.typography.titleMedium)
                Text("Mark prayers from the Prayers tab, a widget or the notification, or tap a day in the calendar to backfill. " +
                    "Your report fills in automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        } else {
            blocks += { Totals(r) }
        blocks += {
            Panel {
                Text("Prayed vs missed", style = MaterialTheme.typography.titleMedium)
                Legend()
                BarChart(r)
            }
        }
        blocks += {
            Panel {
                Text("By prayer (most missed first)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                r.perPrayer.forEach { PrayerRow(it.prayer, it.counts, it.missedPct) }
            }
        }
            if (r.insights.isNotEmpty()) {
        blocks += {
                Panel {
                    Text("Insights", style = MaterialTheme.typography.titleMedium)
                    r.insights.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                }
        }
            }
        }
        blocks += {
        Panel {
            Text("Calendar (tap a day to edit)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Heat(r, s.today) { editDay = it }
        }
        }
        blocks += {
        Panel {
            Text("Streak history", style = MaterialTheme.typography.titleMedium)
            if (runs.isEmpty()) Text("Pray all five on a day to start your first streak.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val f = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
            val best = runs.maxOfOrNull { it.days } ?: 0
            runs.take(20).forEach { run ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (run.start == run.end) run.start.format(f) else "${run.start.format(f)} – ${run.end.format(f)}", style = MaterialTheme.typography.bodyMedium)
                        val ongoing = run.end == s.today || run.end == s.today.minusDays(1)
                        if (ongoing || run.days == best) Text(listOfNotNull("current".takeIf { ongoing }, "best".takeIf { run.days == best }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = Gold)
                    }
                    Text("🔥 ${run.days} ${if (run.days == 1) "day" else "days"}", color = Gold, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
        // Multi-column dashboard on tablets / the Fold inner screen; one column on phones.
        val cols = LocalAdaptive.current.dashboardColumns
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (c in 0 until cols) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    blocks.filterIndexed { i, _ -> i % cols == c }.forEach { it() }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    editDay?.let { d -> DayEditor(d, all[d].orEmpty(), onSet = { p, v -> vm.set(d, p, v) }, onClose = { editDay = null }) }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp)) { content() }
}

@Composable
private fun StatCard(title: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Card).padding(12.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Gold)
        Text(unit, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Totals(r: PeriodReport) {
    val t = r.totals
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${t.completionPct}%", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                Text("${t.prayed} of ${t.due} prayed", style = MaterialTheme.typography.bodyMedium)
            }
            if (r.previous.due > 0) {
                val tr = r.trend
                val (icon, c) = when {
                    tr > 0 -> Icons.Filled.ArrowUpward to AccentGreen
                    tr < 0 -> Icons.Filled.ArrowDownward to Danger
                    else -> Icons.Filled.Remove to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = c)
                        Text("${if (tr > 0) "+" else ""}$tr pts", color = c, fontWeight = FontWeight.Bold)
                    }
                    Text("vs ${r.previous.completionPct}% last ${r.period.label.lowercase()}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("On time", t.onTime, AccentGreen, Modifier.weight(1f))
            Pill("Late", t.late, Gold, Modifier.weight(1f))
            Pill("Missed", t.missed, Danger, Modifier.weight(1f))
        }
        Text("${t.onTimePct}% of prayed prayers were on time", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Pill(label: String, n: Int, c: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.15f)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n", color = c, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Legend() {
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("On time" to AccentGreen, "Late" to Gold, "Missed" to Danger, "Completion %" to Color.White).forEach { (l, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)); Spacer(Modifier.width(4.dp))
                Text(l, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Stacked bars (on time / late / missed) per day or month, with a completion % trend line. */
@Composable
private fun BarChart(r: PeriodReport) {
    val b = r.buckets
    val maxDue = b.maxOf { it.counts.due }.coerceAtLeast(1)
    val grid = CardHigh
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics {
        contentDescription = "Chart: " + b.filter { it.counts.due > 0 }.joinToString { "${it.label} ${it.counts.prayed}/${it.counts.due}" }
    }) {
        val n = b.size
        val slot = size.width / n
        val bw = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val h = size.height
        for (i in 0..4) drawLine(grid, Offset(0f, h * i / 4), Offset(size.width, h * i / 4), 1f)
        val pts = ArrayList<Offset>()
        b.forEachIndexed { i, bk ->
            val x = slot * i + (slot - bw) / 2
            var y = h
            fun seg(v: Int, c: Color) {
                if (v <= 0) return
                val sh = h * v / maxDue
                drawRoundRect(c, Offset(x, y - sh), Size(bw, sh), CornerRadius(3f, 3f)); y -= sh
            }
            seg(bk.counts.onTime, AccentGreen); seg(bk.counts.late, Gold); seg(bk.counts.missed, Danger)
            if (bk.counts.due > 0) pts += Offset(slot * i + slot / 2, h - h * bk.counts.completionPct / 100f)
        }
        if (pts.size >= 2) {
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(2.dp.toPx()))
        }
        pts.forEach { drawCircle(Color.White, 2.5.dp.toPx(), it) }
    }
    val step = when (r.period) { StatsPeriod.MONTH -> 5; else -> 1 }
    Row(Modifier.fillMaxWidth()) {
        r.buckets.forEachIndexed { i, bk ->
            Text(if (i % step == 0 || r.period != StatsPeriod.MONTH) bk.label.take(3) else "", Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = if (r.period == StatsPeriod.MONTH) 8.sp else 11.sp)
        }
    }
}

@Composable
private fun PrayerRow(p: Prayer, c: Counts, missedPct: Int) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.displayName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("${c.missed} missed ($missedPct%) · ${c.onTimePct}% on time", style = MaterialTheme.typography.labelMedium,
                color = if (c.missed > 0) Danger else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CardHigh)) {
            val due = c.due.coerceAtLeast(1).toFloat()
            if (c.onTime > 0) Box(Modifier.weight(c.onTime / due).fillMaxSize().background(AccentGreen))
            if (c.late > 0) Box(Modifier.weight(c.late / due).fillMaxSize().background(Gold))
            if (c.missed > 0) Box(Modifier.weight(c.missed / due).fillMaxSize().background(Danger))
            val rest = 1f - (c.onTime + c.late + c.missed) / due
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
    }
}

/** Calendar heatmap: weekday columns for week/month, a compact GitHub-style grid for the year. */
@Composable
private fun Heat(r: PeriodReport, today: LocalDate, onDay: (LocalDate) -> Unit) {
    fun color(v: Int?) = when {
        v == null -> CardHigh.copy(alpha = 0.4f)
        v == 0 -> Danger.copy(alpha = 0.35f)
        else -> AccentGreen.copy(alpha = 0.2f + 0.16f * v)
    }
    val cells = r.heatmap
    val lead = (cells.first().first.dayOfWeek.value - DayOfWeek.MONDAY.value)
    if (r.period == StatsPeriod.YEAR) {
        val padded: List<Pair<LocalDate, Int?>?> = List(lead) { null } + cells
        val weeks = padded.chunked(7)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            weeks.forEach { w ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    w.forEach { c ->
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(1.dp))
                            .background(if (c == null) Color.Transparent else color(c.second))
                            .then(if (c != null && !c.first.isAfter(today)) Modifier.clickable { onDay(c.first) } else Modifier))
                    }
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    val padded: List<Pair<LocalDate, Int?>?> = List(lead) { null } + cells
    padded.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0 until 7).forEach { i ->
                val c = week.getOrNull(i)
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                        .background(if (c == null) Color.Transparent else color(c.second))
                        .then(if (c != null && !c.first.isAfter(today)) Modifier.clickable { onDay(c.first) } else Modifier)
                        .semantics { if (c != null) contentDescription = "${c.first}: ${c.second ?: 0} of 5 prayed" },
                    contentAlignment = Alignment.Center,
                ) { if (c != null) Text("${c.first.dayOfMonth}", fontSize = 11.sp, color = if (c.first == today) Gold else Color.White) }
            }
        }
    }
}

/** Backfill / edit any past day. */
@Composable
private fun DayEditor(d: LocalDate, day: Map<Prayer, PrayerStatus>, onSet: (Prayer, PrayerStatus?) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(d.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.getDefault()))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Prayer.obligatory.forEach { p ->
                    Text(p.displayName, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(PrayerStatus.ON_TIME to "On time", PrayerStatus.LATE to "Late", PrayerStatus.MISSED to "Missed", null to "Clear").forEach { (v, l) ->
                            FilterChip(selected = day[p] == v && (v != null || p !in day), onClick = { onSet(p, v) }, label = { Text(l, fontSize = 12.sp) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}
