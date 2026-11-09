package com.kamalahmed.prayerapp.ui.qada

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.qada.QadaCalculator
import com.kamalahmed.prayerapp.qada.QadaFeedback
import com.kamalahmed.prayerapp.qada.QadaHistoryRow
import com.kamalahmed.prayerapp.qada.QadaLine
import com.kamalahmed.prayerapp.qada.QadaPrayer
import com.kamalahmed.prayerapp.qada.QadaReport
import com.kamalahmed.prayerapp.qada.QadaStats
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import com.kamalahmed.prayerapp.ui.streaks.BarSlot
import com.kamalahmed.prayerapp.ui.streaks.Panel
import com.kamalahmed.prayerapp.ui.streaks.ProgressRing
import com.kamalahmed.prayerapp.ui.streaks.StackedBarChart
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.AccentGreenDim
import com.kamalahmed.prayerapp.ui.theme.Card
import com.kamalahmed.prayerapp.ui.theme.CardHigh
import com.kamalahmed.prayerapp.ui.theme.Danger
import com.kamalahmed.prayerapp.ui.theme.Gold
import com.kamalahmed.prayerapp.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

internal val prayerColors = mapOf(
    QadaPrayer.FAJR to Color(0xFF7FB2FF), QadaPrayer.DHUHR to Gold, QadaPrayer.ASR to Color(0xFFF08A4B),
    QadaPrayer.MAGHRIB to Color(0xFFE76F8E), QadaPrayer.ISHA to Color(0xFFA48BFF), QadaPrayer.WITR to AccentGreen,
)
private val OnGreen = Color(0xFF04160A)
private fun fmt(n: Int) = "%,d".format(Locale.getDefault(), n)

/** Snackbar title after logging [n] of each of [prayers]; [todayBefore] is what was already logged today. */
internal fun snackTitle(prayers: List<QadaPrayer>, n: Int, goalHit: Boolean, todayBefore: Map<QadaPrayer, Int>): String = when {
    goalHit -> "Daily goal reached · MashaAllah"
    prayers.size > 1 -> "Full day logged · ${prayers.size} prayers"
    else -> { val p = prayers.single(); "${p.displayName} +$n · ×${(todayBefore[p] ?: 0) + n} today" }
}

/** What the snackbar shows after a tap; [id] restarts the auto-hide timer on every new tap. */
data class QadaSnack(val id: Long, val title: String, val detail: String, val canUndo: Boolean, val goal: Boolean = false)

/** Qada (make-up of missed prayers): numbered sections — overview, log today, history, progress, pace, settings. */
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
    val ctx = LocalContext.current
    val r = s.report
    var snack by remember { mutableStateOf<QadaSnack?>(null) }
    val ticks = remember { mutableStateMapOf<QadaPrayer, Int>() }
    var lastAdd by remember { mutableIntStateOf(1) }
    var sheetFor by remember { mutableStateOf<QadaPrayer?>(null) }

    fun logged(prayers: List<QadaPrayer>, n: Int) {
        val before = r.today
        val after = before + n * prayers.size
        val goalHit = r.goal > 0 && before < r.goal && after >= r.goal
        QadaFeedback.play(ctx, if (goalHit) QadaFeedback.Kind.GOAL else QadaFeedback.Kind.TAP)
        lastAdd = n
        prayers.forEach { ticks[it] = (ticks[it] ?: 0) + 1 }
        val remaining = max(0, r.remaining - prayers.sumOf { p -> minOf(n, r.lines.firstOrNull { it.prayer == p }?.remaining ?: 0) })
        val title = snackTitle(prayers, n, goalHit, s.todayByPrayer)
        snack = QadaSnack(System.nanoTime(), title, "${fmt(remaining)} left", canUndo = true, goal = goalHit)
    }
    val onAdd: (QadaPrayer, Int) -> Unit = { p, n -> vm.log(p, n); logged(listOf(p), n) }
    val onUndo: () -> Unit = {
        vm.undo(); QadaFeedback.play(ctx, QadaFeedback.Kind.UNDO)
        snack = QadaSnack(System.nanoTime(), "Last log removed", "Totals updated", canUndo = false)
    }

    Box(Modifier.fillMaxSize()) {
        val blocks = mutableListOf<@Composable () -> Unit>()
        blocks += { Section(1, "Overview", "all qada") { Overview(r) } }
        blocks += {
            Section(2, "Log today", "tap +1 as often as you pray") {
                LogCard(r, s.todayByPrayer, ticks, lastAdd, onAdd = onAdd, onMany = { sheetFor = it },
                    onDay = { vm.logDay(); logged(QadaPrayer.fard, 1) })
            }
        }
        blocks += { Section(3, "History", "one row per day") { HistoryCard(s, vm, onUndo) } }
        blocks += { Section(4, "Progress by prayer", "done / owed") { PerPrayer(r.lines) } }
        blocks += { Section(5, "Pace & finish", "last 30 days") { PaceCard(r) } }
        blocks += {
            SettingsSection(s.config.dailyGoal, s.config.reminder, s.config.reminderMinute, s.haptics, vm,
                onEdit = { editing = true }, onReset = { confirmReset = true })
        }
        val cols = LocalAdaptive.current.dashboardColumns
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (cols <= 1) blocks.forEach { it() } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Wide screens: logging stays first on the left; history and details on the right.
                Column(Modifier.weight(1f)) { blocks[0](); blocks[1](); blocks[3]() }
                Column(Modifier.weight(1f)) { blocks[2](); blocks[4](); blocks[5]() }
            }
            Spacer(Modifier.height(96.dp))
        }
        QadaSnackbar(snack, onUndo = onUndo, onTimeout = { snack = null }, modifier = Modifier.align(Alignment.BottomCenter))
    }
    sheetFor?.let { p ->
        AddManySheet(p, onDismiss = { sheetFor = null }, onAdd = { n -> sheetFor = null; onAdd(p, n) })
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
private fun Section(n: Int, title: String, hint: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(n, title, hint)
        content()
    }
}

@Composable
internal fun SectionTitle(n: Int, title: String, hint: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(CardHigh), contentAlignment = Alignment.Center) {
            Text("$n", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
internal fun QCard(modifier: Modifier = Modifier, border: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(20.dp)) else Modifier).padding(16.dp),
        content = content,
    )
}

@Composable
private fun Overview(r: QadaReport) {
    val progress by animateFloatAsState(r.progress, tween(700), label = "ring")
    val remaining by animateIntAsState(r.remaining, tween(500), label = "remaining")
    QCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress, 92.dp, AccentGreen, stroke = 9.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f%%".format(Locale.getDefault(), r.progress * 100), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("made up", fontSize = 10.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(fmt(remaining), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Gold)
                Text("prayers still to go", color = TextSecondary)
                Text(buildString { append(fmt(r.totalDone)); append(" prayers made up of "); append(fmt(r.totalOwed)) },
                    style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (r.hasDebt && r.remaining == 0) Text("All recorded qada is complete. MashaAllah!", color = AccentGreen, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun LogCard(
    r: QadaReport, todayBy: Map<QadaPrayer, Int>, ticks: Map<QadaPrayer, Int>, lastAdd: Int,
    onAdd: (QadaPrayer, Int) -> Unit, onMany: (QadaPrayer) -> Unit, onDay: () -> Unit,
) {
    val goalMet = r.goal > 0 && r.today >= r.goal
    val todayPop = remember { Animatable(1f) }
    LaunchedEffect(r.today) { if (r.today > 0) { todayPop.snapTo(1.35f); todayPop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) } }
    QCard(border = if (goalMet) Gold.copy(alpha = 0.45f) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${r.today}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.scale(todayPop.value))
            Text(if (r.goal > 0) " of ${r.goal} today" else " today", color = TextSecondary, modifier = Modifier.weight(1f))
            when {
                goalMet -> Pill("Goal reached", AccentGreen, Icons.Filled.Check)
                r.goal > 0 -> Pill("${r.goal - r.today} to goal", TextSecondary)
            }
        }
        if (r.goal > 0) {
            val p by animateFloatAsState((r.today.toFloat() / r.goal).coerceIn(0f, 1f), tween(600), label = "goal")
            Box(Modifier.padding(top = 8.dp, bottom = 10.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(CardHigh)) {
                Box(Modifier.fillMaxWidth(p).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (goalMet) Gold else AccentGreen))
            }
        } else Spacer(Modifier.height(8.dp))
        r.lines.forEach { l -> LogRow(l, todayBy[l.prayer] ?: 0, ticks[l.prayer] ?: 0, lastAdd, onAdd, onMany) }
        FilledTonalButton(onClick = onDay, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentGreenDim)) {
            Text("+1 full day (5)", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(13.dp), tint = color); Spacer(Modifier.width(3.dp)) }
        Text(text, color = color, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** One prayer: done-today highlight (green tint, ✓, ×N) that still accepts more taps, plus all tap feedback. */
@Composable
private fun LogRow(l: QadaLine, today: Int, tick: Int, lastAdd: Int, onAdd: (QadaPrayer, Int) -> Unit, onMany: (QadaPrayer) -> Unit) {
    val done = today > 0
    val color = prayerColors.getValue(l.prayer)
    val flash = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val fly = remember { Animatable(1f) }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        launch { flash.snapTo(1f); flash.animateTo(0f, tween(700)) }
        launch { pop.snapTo(1.45f); pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
        launch { fly.snapTo(0f); fly.animateTo(1f, tween(900)) }
    }
    val base by animateColorAsState(if (done) AccentGreen.copy(alpha = 0.13f) else Color.Transparent, tween(350), label = "rowBg")
    val border by animateColorAsState(if (done) AccentGreen.copy(alpha = 0.35f) else Color.Transparent, tween(350), label = "rowBorder")
    val bg = lerp(base, AccentGreen.copy(alpha = 0.40f), flash.value)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics { if (done) contentDescription = "${l.prayer.displayName} done today, $today times" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (done) AccentGreen else Color.Transparent)
                .border(2.dp, if (done) AccentGreen else color, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (done) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = OnGreen) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.prayer.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (done) {
                    Spacer(Modifier.width(7.dp))
                    Text("×$today", Modifier.scale(pop.value).clip(RoundedCornerShape(10.dp)).background(AccentGreen).padding(horizontal = 7.dp),
                        color = OnGreen, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
            Row {
                if (done) Text("Done today · ", color = AccentGreen, style = MaterialTheme.typography.labelSmall)
                Text("${fmt(l.remaining)} left", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        OutlinedButton(onClick = { onMany(l.prayer) }, contentPadding = PaddingValues(horizontal = 11.dp),
            modifier = Modifier.semantics { contentDescription = "Add several ${l.prayer.displayName}" }) { Text("+N") }
        Spacer(Modifier.width(8.dp))
        Box(contentAlignment = Alignment.Center) {
            PressButton("+1", "Add one ${l.prayer.displayName}") { onAdd(l.prayer, 1) }
            if (fly.value < 1f) Text("+$lastAdd", color = AccentGreen, fontWeight = FontWeight.Black, fontSize = 20.sp,
                modifier = Modifier.offset(y = (-46 * fly.value - 6).dp).alpha((1f - fly.value) * if (fly.value < 0.2f) fly.value * 5 else 1f))
        }
    }
}

/** Big +1: squeezes while pressed and springs back (press animation independent of the data write). */
@Composable
private fun PressButton(label: String, description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "press")
    Button(
        onClick = onClick, interactionSource = source,
        modifier = Modifier.scale(scale).width(62.dp).height(44.dp).semantics { contentDescription = description },
        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = OnGreen),
        contentPadding = PaddingValues(0.dp),
    ) { Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
}

/** Bottom snackbar: what was logged, how many are left, and an Undo that removes exactly that action. */
@Composable
private fun QadaSnackbar(snack: QadaSnack?, onUndo: () -> Unit, onTimeout: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(snack?.id) { if (snack != null) { delay(4_500); onTimeout() } }
    AnimatedVisibility(snack != null, modifier.padding(12.dp), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val s = snack ?: return@AnimatedVisibility
        Row(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2C2C30))
                .border(1.dp, Color(0xFF3C3C40), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(if (s.goal) Gold else AccentGreen), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = OnGreen)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(s.detail, color = TextSecondary, fontSize = 11.5.sp)
            }
            if (s.canUndo) TextButton(onClick = onUndo) { Text("UNDO", color = Gold, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddManySheet(p: QadaPrayer, onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var n by rememberSaveable { mutableIntStateOf(5) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF1F1F22)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add several ${p.displayName}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("For catching up a batch at once. Logged as one entry; one Undo removes it.", color = TextSecondary, fontSize = 12.5.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 5, 10, 20, 50, 100).forEach { q -> FilterChip(n == q, { n = q }, label = { Text("$q") }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { n = (n - 10).coerceAtLeast(1) }) { Text("−10") }
                TextButton(onClick = { n = (n - 1).coerceAtLeast(1) }, modifier = Modifier.semantics { contentDescription = "One fewer" }) { Text("−", fontSize = 22.sp) }
                Text("$n", fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
                TextButton(onClick = { n = (n + 1).coerceAtMost(1000) }, modifier = Modifier.semantics { contentDescription = "One more" }) { Text("+", fontSize = 22.sp) }
                OutlinedButton(onClick = { n = (n + 10).coerceAtMost(1000) }) { Text("+10") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onAdd(n) }, modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = OnGreen)) { Text("Add $n ${p.displayName}", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

internal fun historyLabel(row: QadaHistoryRow, today: LocalDate): String = when (row.kind) {
    QadaHistoryRow.Kind.DAY -> when (row.start) {
        today -> "Today · ${row.start.format(dayFmt)}"
        today.minusDays(1) -> "Yesterday · ${row.start.format(dayFmt)}"
        else -> row.start.format(dayFmt)
    }
    QadaHistoryRow.Kind.WEEK -> if (row.start.month == row.end.month) "${row.start.dayOfMonth}–${row.end.dayOfMonth} ${row.start.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
        else "${row.start.dayOfMonth} ${row.start.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} – ${row.end.dayOfMonth} ${row.end.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
    QadaHistoryRow.Kind.MONTH -> row.start.format(monthFmt)
    QadaHistoryRow.Kind.YEAR -> "${row.start.year}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryCard(s: QadaUi, vm: QadaViewModel, onUndo: () -> Unit) {
    val open = remember { mutableStateMapOf<String, Boolean>().apply { put("d:${s.today}", true) } }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val prayers = s.report.lines.map { it.prayer }
    QCard(Modifier, null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Text(fmt(s.report.totalDone), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(" made up · this week ${s.report.weekly.lastOrNull()?.second ?: 0}", color = TextSecondary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            if (s.canUndo) OutlinedButton(onClick = onUndo, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Undo last", fontSize = 12.sp)
            }
        }
        if (s.history.isEmpty()) {
            Text("Nothing logged yet. Tap +1 above each time you finish a make-up prayer.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            return@QCard
        }
        var dividerShown = false
        s.history.forEach { row ->
            if (row.kind != QadaHistoryRow.Kind.DAY && !dividerShown) {
                dividerShown = true
                Text("EARLIER · FOLDED BY WEEK AND MONTH", fontSize = 10.5.sp, letterSpacing = 1.2.sp, color = Color(0xFF77777C),
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp))
            }
            val isOpen = open[row.key] == true
            val isToday = row.kind == QadaHistoryRow.Kind.DAY && row.start == s.today
            Column(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp))
                    .then(if (isToday) Modifier.background(AccentGreen.copy(alpha = 0.08f)).border(1.dp, AccentGreen.copy(alpha = 0.3f), RoundedCornerShape(14.dp)) else Modifier),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { open[row.key] = !isOpen; if (isOpen && editing == row.key) editing = null }.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(historyLabel(row, s.today), fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        if (row.kind == QadaHistoryRow.Kind.DAY) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            prayers.filter { (row.perPrayer[it] ?: 0) > 0 }.forEach { p ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(prayerColors.getValue(p)))
                                    Text(" ${p.displayName.first()}${row.perPrayer[p]}", fontSize = 11.5.sp, color = Color(0xFFD0D0D4), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else Text("${row.activeDays} ${if (row.activeDays == 1) "day" else "days"}", fontSize = 11.5.sp, color = TextSecondary)
                    }
                    Text(fmt(row.total), Modifier.clip(RoundedCornerShape(10.dp)).background(if (isToday) AccentGreen else CardHigh).padding(horizontal = 9.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isToday) OnGreen else Color.Unspecified)
                    Icon(Icons.Filled.KeyboardArrowRight, if (isOpen) "Collapse" else "Expand", Modifier.rotate(if (isOpen) 90f else 0f), tint = TextSecondary)
                }
                if (isOpen) when {
                    row.kind == QadaHistoryRow.Kind.DAY && editing == row.key -> Column(Modifier.padding(horizontal = 8.dp)) {
                        prayers.forEach { p ->
                            val c = row.perPrayer[p] ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(prayerColors.getValue(p)))
                                Text(p.displayName, Modifier.weight(1f).padding(start = 10.dp))
                                OutlinedButton(onClick = { vm.setDayCount(row.start, p, c - 1) }, enabled = c > 0, contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.width(44.dp).semantics { contentDescription = "One fewer ${p.displayName}" }) { Text("−") }
                                Text("$c", Modifier.width(36.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { vm.setDayCount(row.start, p, c + 1) }, contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.width(44.dp).semantics { contentDescription = "One more ${p.displayName}" }) { Text("+") }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Text("Changes save right away and update totals.", fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                            Button(onClick = { editing = null }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = OnGreen)) { Text("Done") }
                        }
                    }
                    row.kind == QadaHistoryRow.Kind.DAY -> Column(Modifier.padding(horizontal = 10.dp)) {
                        prayers.filter { (row.perPrayer[it] ?: 0) > 0 }.chunked(2).forEach { pair ->
                            Row { pair.forEach { p ->
                                Row(Modifier.weight(1f).padding(end = 14.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(prayerColors.getValue(p)))
                                    Text(p.displayName, Modifier.weight(1f).padding(start = 6.dp), fontSize = 12.5.sp)
                                    Text("${row.perPrayer[p]}", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }
                            }; if (pair.size == 1) Spacer(Modifier.weight(1f)) }
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            if (isToday && s.canUndo) OutlinedButton(onClick = onUndo, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Undo last", fontSize = 12.sp) }
                            OutlinedButton(onClick = { editing = row.key }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Icon(Icons.Filled.Edit, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Edit day", fontSize = 12.sp)
                            }
                        }
                    }
                    else -> Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                        row.children.forEach { c ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(if (c.kind == QadaHistoryRow.Kind.MONTH && row.kind == QadaHistoryRow.Kind.YEAR) c.start.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) else historyLabel(c, s.today),
                                    Modifier.weight(1f), fontSize = 12.5.sp, color = Color(0xFFCFCFD3))
                                Text("${c.activeDays} d · ", fontSize = 11.5.sp, color = TextSecondary)
                                Text(fmt(c.total), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }
        Text("One row per day, then per month and year: the list stays short however many prayers you log.",
            fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PerPrayer(lines: List<QadaLine>) {
    QCard {
        lines.forEach { l ->
            val pct by animateFloatAsState(l.pct, tween(600), label = "pp")
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(prayerColors.getValue(l.prayer)))
                    Text(l.prayer.displayName, Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.Medium)
                    Text("${fmt(l.done)} / ${fmt(l.owed)}", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CardHigh)) {
                    if (pct > 0f) Box(Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(4.dp)).background(prayerColors.getValue(l.prayer)))
                }
            }
        }
    }
}

internal fun finishAt(remaining: Int, perDay: Double, today: LocalDate): LocalDate? =
    if (perDay <= 0.0 || remaining <= 0) null else today.plusDays(ceil(remaining / perDay).toLong().coerceAtMost(365L * 200))

@Composable
private fun PaceCard(r: QadaReport) {
    val f = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
    val today = LocalDate.now()
    val target = if (r.goal > 0) r.goal else 5
    var chart by rememberSaveable { mutableStateOf(false) }
    var monthly by rememberSaveable { mutableStateOf(false) }
    QCard {
        if (r.hasDebt && r.remaining == 0) {
            Text("All recorded qada is complete. MashaAllah!", color = AccentGreen, fontWeight = FontWeight.SemiBold)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PaceStat("%.1f".format(Locale.getDefault(), r.pacePerDay), "a day now", Color.Unspecified)
            PaceStat(r.finishDate?.format(f) ?: "—", if (r.finishDate == null) "log to estimate" else "at this pace", Gold)
            PaceStat(finishAt(r.remaining, target.toDouble(), today)?.format(f) ?: "—", "at $target a day", AccentGreen)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            PaceStat("${r.today}", "today", Color.Unspecified); PaceStat("${r.last7}", "7 days", Color.Unspecified)
            PaceStat("${r.last30}", "30 days", Color.Unspecified); PaceStat("${r.streakDays}", "day streak", Color.Unspecified)
        }
        Text("Made up so far ≈ ${QadaStats.span(r.daysEquivalent.toInt())} of prayers (5 prayers = 1 day)",
            style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(top = 10.dp))
        TextButton(onClick = { chart = !chart }) { Text(if (chart) "Hide chart" else "Show weekly / monthly chart") }
        if (chart) {
            SingleChoiceSegmentedButtonRow(Modifier.padding(bottom = 10.dp)) {
                listOf("Weeks", "Months").forEachIndexed { i, l ->
                    SegmentedButton(selected = monthly == (i == 1), onClick = { monthly = i == 1 }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                }
            }
            val data = if (monthly) r.monthly else r.weekly
            if (data.all { it.second == 0 }) Text("No make-up prayers logged in this range yet.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            else StackedBarChart(data.map { (d, n) ->
                val label = if (monthly) d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1) else "${d.dayOfMonth}/${d.monthValue}"
                BarSlot(label, listOf(n to AccentGreen), true, "$label: $n")
            })
        }
    }
}

@Composable
private fun PaceStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = color)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun SettingsSection(goal: Int, reminder: Boolean, minute: Int, haptics: Boolean, vm: QadaViewModel, onEdit: () -> Unit, onReset: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Column {
        SectionTitle(6, "Goal, reminder & debt", "", Modifier.clickable { open = !open })
        QCard {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text(listOf(if (goal == 0) "No daily goal" else "Goal $goal/day", if (reminder && goal > 0) "Reminder %02d:%02d".format(minute / 60, minute % 60) else "Reminder off",
                    if (haptics) "Vibration on" else "Vibration off").joinToString(" · "), fontSize = 12.5.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowRight, if (open) "Collapse settings" else "Expand settings", Modifier.rotate(if (open) 90f else 0f), tint = TextSecondary)
            }
            if (open) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Daily goal", Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.setGoal(goal - 1) }, enabled = goal > 0) { Text("−") }
                    Text(if (goal == 0) "Off" else "$goal", Modifier.width(48.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { vm.setGoal(goal + 1) }) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily reminder")
                        Text(if (goal == 0) "Set a daily goal first" else "At %02d:%02d if the goal isn't met".format(minute / 60, minute % 60),
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.clickable(enabled = goal > 0) { pickTime = true })
                    }
                    Switch(checked = reminder && goal > 0, onCheckedChange = { vm.setReminder(it) }, enabled = goal > 0)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Tap vibration")
                        Text("Shared with the Tasbih counter's tap haptics", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Switch(checked = haptics, onCheckedChange = { vm.setHaptics(it) })
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) { Text("Edit debt") }
                    TextButton(onClick = onReset) { Text("Reset…", color = Danger) }
                }
            }
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
