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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.SegmentedButton
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

/** Streaks tab: "Stats" (tracker dashboard) and "Qada" (make-up prayers) sub-tabs, keeping 5 nav destinations. */
@Composable
fun StreaksScreen(vm: StreaksViewModel = hiltViewModel()) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text(when (tab) { 0 -> "Streaks"; 1 -> "Qada"; else -> "Nafl" }, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Stats", "Qada", "Nafl").forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = tab == i, onClick = { tab = i },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 3),
                    ) { Text(l) }
                }
            }
        }
        when (tab) {
            0 -> StatsContent(vm)
            1 -> com.kamalahmed.prayerapp.ui.qada.QadaScreen()
            else -> com.kamalahmed.prayerapp.ui.nafl.NaflScreen()
        }
    }
}

@Composable
fun StatsContent(vm: StreaksViewModel) {
    val st by vm.state.collectAsStateWithLifecycle()
    val rep by vm.report.collectAsStateWithLifecycle()
    val runs by vm.streakHistory.collectAsStateWithLifecycle()
    val all by vm.history.collectAsStateWithLifecycle()
    var editDay by remember { mutableStateOf<LocalDate?>(null) }
    val s = st ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        KpiGrid(listOf(
            { m -> KpiCard("Current streak", "${s.stats.currentDays}", "days with all 5", Gold, m) },
            { m -> KpiCard("Best streak", "${s.stats.bestDays}", "days", Gold, m) },
            { m -> KpiCard("In a row", "${s.stats.currentPrayers}", "prayers", AccentGreen, m) },
            { m -> KpiCard("Today", "${s.todayStatus.count { it.value == PrayerStatus.ON_TIME || it.value == PrayerStatus.LATE }}/5", "prayed", AccentGreen, m) },
        ))
        Spacer(Modifier.height(12.dp))
        Panel("Today", "Tap a prayer once it has started. Tap a calendar day below to edit past days.") { TodayTracker(vm, abbreviated = true) }
        Spacer(Modifier.height(16.dp))
        PeriodHeader(rep, s.today, vm)
        val r = rep ?: return@Column
        Spacer(Modifier.height(8.dp))
        val blocks = mutableListOf<@Composable () -> Unit>()
        if (r.isEmpty) {
            blocks += {
                Panel("No data for this ${r.period.label.lowercase()}") {
                    Text("Mark prayers from the Prayers tab, a widget or a notification, or tap a day in the calendar to backfill. " +
                        "Your report fills in automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            blocks += { Overview(r) }
            blocks += { PrayedChart(r) }
            blocks += { RateTrend(r) }
            blocks += { PerPrayer(r) }
            if (r.insights.isNotEmpty()) blocks += {
                Panel("Insights") { r.insights.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) } }
            }
        }
        blocks += { CalendarPanel(r, s.today) { editDay = it } }
        blocks += { StreakHistory(runs, s.today) }
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
private fun PeriodHeader(rep: PeriodReport?, today: LocalDate, vm: StreaksViewModel) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        StatsPeriod.entries.forEachIndexed { i, p ->
            SegmentedButton(
                selected = rep?.period == p, onClick = { vm.setPeriod(p) },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, StatsPeriod.entries.size),
            ) { Text(p.label) }
        }
    }
    val r = rep ?: return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous ${r.period.label}") }
        Text(r.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { vm.shift(1) }, enabled = r.end.isBefore(today)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next ${r.period.label}")
        }
    }
}

@Composable
private fun Overview(r: PeriodReport) {
    val t = r.totals
    Panel("Overview", "${t.due} prayers were due this ${r.period.label.lowercase()}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(t.completionPct / 100f, 104.dp, AccentGreen) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${t.completionPct}%", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("prayed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetricLine("On time", t.onTime, t.due, AccentGreen)
                MetricLine("Late", t.late, t.due, Gold)
                MetricLine("Missed", t.missed, t.due, Danger)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${t.onTimePct}% of prayed were on time", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.previous.due > 0) {
                val tr = r.trend
                val (icon, c) = when {
                    tr > 0 -> Icons.Filled.ArrowUpward to AccentGreen
                    tr < 0 -> Icons.Filled.ArrowDownward to Danger
                    else -> Icons.Filled.Remove to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(icon, null, tint = c, modifier = Modifier.size(16.dp))
                Text("${if (tr > 0) "+" else ""}$tr pts vs last ${r.period.label.lowercase()}", color = c, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, n: Int, of: Int, c: Color) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("$n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c)
        }
        Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(CardHigh)) {
            if (n > 0 && of > 0) Box(Modifier.fillMaxWidth(n.toFloat() / of).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
        }
    }
}

private fun slotsOf(r: PeriodReport): List<BarSlot> = r.buckets.map { b ->
    val label = when (r.period) { StatsPeriod.MONTH -> "${b.start.dayOfMonth}"; StatsPeriod.YEAR -> b.label.take(1); else -> b.label.take(2) }
    BarSlot(label, listOf(b.counts.onTime to AccentGreen, b.counts.late to Gold, b.counts.missed to Danger), b.tracked,
        "${b.label} ${b.counts.prayed} of ${b.counts.due}")
}

@Composable
private fun PrayedChart(r: PeriodReport) {
    val title = when (r.period) { StatsPeriod.WEEK -> "Prayers per day"; StatsPeriod.MONTH -> "Prayers per day"; StatsPeriod.YEAR -> "Prayers per month" }
    Panel(title, if (r.buckets.any { !it.tracked }) "Dashed bars: no data (before tracking started or in the future)" else null) {
        Legend(listOf("On time" to AccentGreen, "Late" to Gold, "Missed" to Danger))
        // Day charts use a fixed 0..5 scale; month bars scale to the busiest month.
        StackedBarChart(slotsOf(r), yMax = if (r.period == StatsPeriod.YEAR) null else 5)
    }
}

@Composable
private fun RateTrend(r: PeriodReport) {
    val tracked = r.buckets.count { it.tracked }
    if (tracked < 2) return
    Panel(if (r.period == StatsPeriod.YEAR) "Monthly completion" else "Daily completion") {
        Legend(listOf("Prayed" to AccentGreen, "On time" to Gold))
        val labels = r.buckets.map { b -> when (r.period) { StatsPeriod.MONTH -> "${b.start.dayOfMonth}"; StatsPeriod.YEAR -> b.label.take(1); else -> b.label.take(2) } }
        RateLineChart(
            listOf("Prayed" to r.buckets.map { if (it.tracked && it.counts.due > 0) it.counts.completionPct else null },
                "On time" to r.buckets.map { if (it.tracked) it.onTimeRate else null }),
            labels, listOf(AccentGreen, Gold),
        )
    }
}

@Composable
private fun PerPrayer(r: PeriodReport) {
    Panel("By prayer", "Most missed first") {
        r.perPrayer.forEach { PrayerRow(it.prayer, it.counts, it.missedPct) }
    }
}

@Composable
private fun CalendarPanel(r: PeriodReport, today: LocalDate, onDay: (LocalDate) -> Unit) {
    Panel(if (r.period == StatsPeriod.YEAR) "Year at a glance" else "Calendar", "Tap a day to edit it") {
        if (r.period == StatsPeriod.YEAR) ContributionGrid(r.heatmap, today, AccentGreen, Danger, onDay)
        else MonthCalendar(r, today, onDay)
        HeatScale(AccentGreen, Danger)
    }
}

@Composable
private fun StreakHistory(runs: List<com.kamalahmed.prayerapp.tracker.StreakRun>, today: LocalDate) {
    Panel("Streak history") {
        if (runs.isEmpty()) Text("Pray all five on a day to start your first streak.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val f = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val best = runs.maxOfOrNull { it.days } ?: 0
        runs.take(20).forEach { run ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (run.start == run.end) run.start.format(f) else "${run.start.format(f)} – ${run.end.format(f)}", style = MaterialTheme.typography.bodyMedium)
                    val ongoing = run.end == today || run.end == today.minusDays(1)
                    if (ongoing || run.days == best) Text(listOfNotNull("current".takeIf { ongoing }, "best".takeIf { run.days == best }).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = Gold)
                }
                Text("🔥 ${run.days} ${if (run.days == 1) "day" else "days"}", color = Gold, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PrayerRow(p: Prayer, c: Counts, missedPct: Int) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.displayName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("${c.prayed}/${c.due} · ${c.onTimePct}% on time", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CardHigh)) {
            val due = c.due.coerceAtLeast(1).toFloat()
            if (c.onTime > 0) Box(Modifier.weight(c.onTime / due).fillMaxSize().background(AccentGreen))
            if (c.late > 0) Box(Modifier.weight(c.late / due).fillMaxSize().background(Gold))
            if (c.missed > 0) Box(Modifier.weight(c.missed / due).fillMaxSize().background(Danger))
            val rest = 1f - (c.onTime + c.late + c.missed) / due
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
        if (c.missed > 0) Text("${c.missed} missed ($missedPct%)", style = MaterialTheme.typography.labelSmall, color = Danger, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Week / month calendar with weekday headers. */
@Composable
private fun MonthCalendar(r: PeriodReport, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val cells = r.heatmap
    if (cells.isEmpty()) return
    val lead = cells.first().first.dayOfWeek.value - DayOfWeek.MONDAY.value
    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach {
            Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val padded: List<Pair<LocalDate, Int?>?> = List(lead) { null } + cells
    padded.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0 until 7).forEach { i ->
                val c = week.getOrNull(i)
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (c == null) Color.Transparent else heatColor(c.second, AccentGreen, Danger))
                        .then(if (c != null && !c.first.isAfter(today)) Modifier.clickable { onDay(c.first) } else Modifier)
                        .semantics { if (c != null) contentDescription = "${c.first}: ${c.second?.let { "$it of 5 prayed" } ?: "no data"}" },
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
