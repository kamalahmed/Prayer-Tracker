package com.kamalahmed.prayerapp.ui.qada

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.qada.QadaCalculator
import com.kamalahmed.prayerapp.qada.QadaEntry
import com.kamalahmed.prayerapp.qada.QadaLine
import com.kamalahmed.prayerapp.qada.QadaPrayer
import com.kamalahmed.prayerapp.qada.QadaStats
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import com.kamalahmed.prayerapp.ui.streaks.BarSlot
import com.kamalahmed.prayerapp.ui.streaks.KpiCard
import com.kamalahmed.prayerapp.ui.streaks.KpiGrid
import com.kamalahmed.prayerapp.ui.streaks.Panel
import com.kamalahmed.prayerapp.ui.streaks.ProgressRing
import com.kamalahmed.prayerapp.ui.streaks.StackedBarChart
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.CardHigh
import com.kamalahmed.prayerapp.ui.theme.Danger
import com.kamalahmed.prayerapp.ui.theme.Gold
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val prayerColors = mapOf(
    QadaPrayer.FAJR to Color(0xFF7FB2FF), QadaPrayer.DHUHR to Gold, QadaPrayer.ASR to Color(0xFFF08A4B),
    QadaPrayer.MAGHRIB to Color(0xFFE76F8E), QadaPrayer.ISHA to Color(0xFFA48BFF), QadaPrayer.WITR to AccentGreen,
)

/** Qada (make-up of missed prayers): debt setup, quick logging, progress and history. */
@Composable
fun QadaScreen(vm: QadaViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    if (!s.config.setupDone || editing) {
        DebtSetup(
            initial = s.report.lines.associate { it.prayer to it.owed }, initialWitr = s.config.witr, canCancel = s.config.setupDone,
            onCancel = { editing = false },
            onSave = { counts, witr -> vm.saveDebt(counts, witr); editing = false },
        )
        return
    }
    val r = s.report
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Hero(r)
        Spacer(Modifier.height(12.dp))
        val span = QadaStats.span(r.daysCovered)
        KpiGrid(listOf(
            { m -> KpiCard("Days covered", "${r.daysCovered}", "full days (all 5) · $span", AccentGreen, m) },
            { m -> KpiCard("Days remaining", "${r.daysRemaining}", QadaStats.span(r.daysRemaining).toString(), Gold, m) },
            { m -> KpiCard("Pace", "%.1f".format(Locale.getDefault(), r.pacePerDay), "prayers / day (30 days)", AccentGreen, m) },
            { m -> KpiCard("Qada streak", "${r.streakDays}", "days · best ${r.bestStreakDays}", Gold, m) },
        ))
        Spacer(Modifier.height(12.dp))
        val blocks = mutableListOf<@Composable () -> Unit>()
        blocks += { LogPanel(s.report.lines, r.today, r.goal, vm) }
        blocks += { PerPrayerPanel(r.lines) }
        blocks += { FinishPanel(r) }
        blocks += { ChartsPanel(r) }
        blocks += { HistoryPanel(s.entries, vm) }
        blocks += {
            GoalPanel(s.config.dailyGoal, s.config.reminder, s.config.reminderMinute, vm,
                onEdit = { editing = true }, onReset = { confirmReset = true })
        }
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
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset qada tracker?") },
        text = { Text("This deletes your debt and the whole make-up log. It can't be undone.") },
        confirmButton = { TextButton(onClick = { vm.reset(); confirmReset = false }) { Text("Reset", color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Hero(r: com.kamalahmed.prayerapp.qada.QadaReport) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(r.progress, 128.dp, AccentGreen, stroke = 16.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(r.progress * 100).roundToInt()}%", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("made up", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("%,d".format(r.totalDone), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                Text("prayers made up", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text("%,d".format(r.remaining), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Gold)
                Text("of %,d still to go".format(r.totalOwed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LogPanel(lines: List<QadaLine>, today: Int, goal: Int, vm: QadaViewModel) {
    var bulkFor by remember { mutableStateOf<QadaPrayer?>(null) }
    Panel("Log make-up prayers", if (goal > 0) "Today: $today of $goal goal" else "Today: $today") {
        if (goal > 0) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(CardHigh)) {
                Box(Modifier.fillMaxWidth((today.toFloat() / goal).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(AccentGreen))
            }
            Spacer(Modifier.height(10.dp))
        }
        lines.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(prayerColors.getValue(l.prayer)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.prayer.displayName, fontWeight = FontWeight.Medium)
                    Text("%,d left".format(l.remaining), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { bulkFor = l.prayer }, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("+N") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.log(l.prayer, 1) },
                    modifier = Modifier.semantics { contentDescription = "Add one ${l.prayer.displayName}" },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                ) { Text("+1", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { vm.logDay() }, modifier = Modifier.weight(1f)) { Text("+1 full day (5)") }
            OutlinedButton(onClick = { vm.undo() }) {
                Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Undo")
            }
        }
    }
    bulkFor?.let { p ->
        NumberDialog("Add ${p.displayName}", "How many did you make up?", 2,
            onDismiss = { bulkFor = null }, onConfirm = { vm.log(p, it); bulkFor = null }, max = 1000)
    }
}

@Composable
private fun PerPrayerPanel(lines: List<QadaLine>) {
    Panel("By prayer") {
        lines.forEach { l ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Row {
                    Text(l.prayer.displayName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("%,d / %,d".format(l.done, l.owed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CardHigh)) {
                    if (l.pct > 0f) Box(Modifier.fillMaxWidth(l.pct).height(8.dp).clip(RoundedCornerShape(4.dp)).background(prayerColors.getValue(l.prayer)))
                }
            }
        }
    }
}

@Composable
private fun FinishPanel(r: com.kamalahmed.prayerapp.qada.QadaReport) {
    val f = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    Panel("Projection") {
        when {
            r.hasDebt && r.remaining == 0 -> Text("All recorded qada is complete. MashaAllah!", color = AccentGreen, fontWeight = FontWeight.SemiBold)
            r.finishDate != null -> {
                Text(r.finishDate.format(f), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
                Text("estimated finish at your current pace of %.1f prayers a day".format(Locale.getDefault(), r.pacePerDay),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val perDayFor1Year = r.remaining / 365.0
                if (perDayFor1Year >= 1) Text("To finish within a year: about %.0f a day.".format(Locale.getDefault(), kotlin.math.ceil(perDayFor1Year)),
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
            else -> Text("Log a few prayers to see your estimated finish date.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Today", r.today); Stat("7 days", r.last7); Stat("30 days", r.last30)
        }
        Text("Made up so far ≈ ${QadaStats.span(r.daysEquivalent.toInt())} of prayers (5 prayers = 1 day)",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Stat(label: String, n: Int) {
    Column {
        Text("$n", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartsPanel(r: com.kamalahmed.prayerapp.qada.QadaReport) {
    var monthly by rememberSaveable { mutableStateOf(false) }
    Panel("Made up per ${if (monthly) "month" else "week"}") {
        SingleChoiceSegmentedButtonRow(Modifier.padding(bottom = 10.dp)) {
            listOf("Weeks", "Months").forEachIndexed { i, l ->
                SegmentedButton(selected = monthly == (i == 1), onClick = { monthly = i == 1 }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
            }
        }
        val data = if (monthly) r.monthly else r.weekly
        if (data.all { it.second == 0 }) {
            Text("No make-up prayers logged in this range yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val slots = data.map { (d, n) ->
                val label = if (monthly) d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1) else "${d.dayOfMonth}/${d.monthValue}"
                BarSlot(label, listOf(n to AccentGreen), true, "$label: $n")
            }
            StackedBarChart(slots)
        }
    }
}

@Composable
private fun HistoryPanel(entries: List<QadaEntry>, vm: QadaViewModel) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val tf = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())
    Panel("Log history", "${entries.size} entries") {
        if (entries.isEmpty()) Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        entries.take(if (showAll) 200 else 8).forEach { e ->
            val p = QadaPrayer.of(e.prayer) ?: return@forEach
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(prayerColors.getValue(p)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${p.displayName} × ${e.count}", fontWeight = FontWeight.Medium)
                    Text(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).format(tf), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.delete(e.id) }) { Icon(Icons.Filled.Delete, "Delete entry", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (entries.size > 8) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show less" else "Show all") }
    }
}

@Composable
private fun GoalPanel(goal: Int, reminder: Boolean, minute: Int, vm: QadaViewModel, onEdit: () -> Unit, onReset: () -> Unit) {
    var pickTime by remember { mutableStateOf(false) }
    Panel("Goal & reminder") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily goal", Modifier.weight(1f))
            OutlinedButton(onClick = { vm.setGoal(goal - 1) }, enabled = goal > 0) { Text("−") }
            Text(if (goal == 0) "Off" else "$goal", Modifier.width(48.dp), fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            OutlinedButton(onClick = { vm.setGoal(goal + 1) }) { Text("+") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Daily reminder")
                Text(if (goal == 0) "Set a daily goal first" else "At %02d:%02d if the goal isn't met".format(minute / 60, minute % 60),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(enabled = goal > 0) { pickTime = true })
            }
            Switch(checked = reminder && goal > 0, onCheckedChange = { vm.setReminder(it) }, enabled = goal > 0)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) { Text("Edit debt") }
            TextButton(onClick = onReset) { Text("Reset…", color = Danger) }
        }
    }
    if (pickTime) {
        NumberDialog("Reminder hour", "Hour of day (0–23)", minute / 60, onDismiss = { pickTime = false },
            onConfirm = { vm.setReminderMinute(it * 60); pickTime = false }, max = 23, min = 0)
    }
}

@Composable
private fun NumberDialog(title: String, label: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit, max: Int, min: Int = 1) {
    var text by remember { mutableStateOf("$initial") }
    val v = text.trim().toIntOrNull()
    val ok = v != null && v in min..max
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it.filter(Char::isDigit).take(7) }, label = { Text(label) }, singleLine = true,
                isError = !ok, supportingText = { if (!ok) Text("Enter $min–$max") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        },
        confirmButton = { TextButton(onClick = { if (ok) onConfirm(v!!) }, enabled = ok) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** First-run / edit: enter counts directly or calculate them from a missed period. */
@Composable
private fun DebtSetup(
    initial: Map<QadaPrayer, Int>, initialWitr: Boolean, canCancel: Boolean,
    onCancel: () -> Unit, onSave: (Map<QadaPrayer, Int>, Boolean) -> Unit,
) {
    var witr by rememberSaveable { mutableStateOf(initialWitr) }
    val counts = remember { mutableStateMapOf<QadaPrayer, String>().apply { QadaPrayer.entries.forEach { put(it, "${initial[it] ?: 0}") } } }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 years/months, 1 date range
    var years by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var exclude by rememberSaveable { mutableStateOf("0") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel("Make up missed prayers",
            "Qada lets you track prayers you are making up from the past. Work out how many you owe, then log each one as you pray it.") {}
        Panel("1 · Calculate (optional)", "Fills in the counts below. You can still edit them.") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Years & months", "Date range").forEachIndexed { i, l ->
                    SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (mode == 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Years", years, Modifier.weight(1f)) { years = it }
                NumField("Months", months, Modifier.weight(1f)) { months = it }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from, { from = it.take(10) }, Modifier.weight(1f), label = { Text("From (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(to, { to = it.take(10) }, Modifier.weight(1f), label = { Text("To (YYYY-MM-DD)") }, singleLine = true)
            }
            NumField("Days to exclude per month (e.g. menstruation)", exclude, Modifier.fillMaxWidth().padding(top = 8.dp)) { exclude = it }
            val days = if (mode == 0) QadaCalculator.daysFor(years.toIntOrNull() ?: 0, months.toIntOrNull() ?: 0) else {
                val a = runCatching { LocalDate.parse(from.trim()) }.getOrNull(); val b = runCatching { LocalDate.parse(to.trim()) }.getOrNull()
                if (a != null && b != null) QadaCalculator.daysBetween(a, b) else 0
            }
            val net = QadaCalculator.applyExclusion(days, (exclude.toIntOrNull() ?: 0).coerceIn(0, 15))
            Text(if (net > 0) "$net days ≈ ${QadaStats.span(net)} → $net of each prayer" else "Enter a period to calculate.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = { QadaCalculator.counts(net, witr).forEach { (p, n) -> counts[p] = "$n" } }, enabled = net > 0,
                modifier = Modifier.padding(top = 6.dp)) { Text("Fill counts") }
        }
        Panel("2 · Prayers owed") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include Witr", Modifier.weight(1f))
                Switch(checked = witr, onCheckedChange = { witr = it })
            }
            QadaPrayer.entries.filter { it != QadaPrayer.WITR || witr }.forEach { p ->
                NumField(p.displayName, counts[p].orEmpty(), Modifier.fillMaxWidth().padding(top = 6.dp)) { counts[p] = it }
            }
            Row(Modifier.padding(top = 6.dp)) {
                listOf(1, 5, 10).forEach { y ->
                    FilterChip(false, { val n = QadaCalculator.daysFor(y, 0); QadaCalculator.counts(n, witr).forEach { (p, c) -> counts[p] = "$c" } },
                        label = { Text("$y yr") }, modifier = Modifier.padding(end = 6.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canCancel) OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onSave(QadaPrayer.entries.associateWith { (counts[it]?.toIntOrNull() ?: 0).coerceIn(0, 1_000_000) }, witr) },
                modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            ) { Text("Save") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(7)) }, modifier, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
