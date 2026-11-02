package com.kamalahmed.prayerapp.ui.streaks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.tracker.PeriodStats
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.ui.common.SubScreen
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.Card
import com.kamalahmed.prayerapp.ui.theme.CardHigh
import com.kamalahmed.prayerapp.ui.theme.Danger
import com.kamalahmed.prayerapp.ui.theme.Gold

fun PrayerStatus?.color(): Color = when (this) {
    PrayerStatus.ON_TIME -> AccentGreen
    PrayerStatus.LATE -> Gold
    PrayerStatus.MISSED -> Danger
    null -> CardHigh
}

fun PrayerStatus?.label(): String = when (this) {
    PrayerStatus.ON_TIME -> "prayed on time"
    PrayerStatus.LATE -> "prayed late"
    PrayerStatus.MISSED -> "missed"
    null -> "not marked"
}

/** Five tap-to-mark circles for today (used on the Prayers screen and Streaks screen). */
@Composable
fun TodayTracker(vm: StreaksViewModel = hiltViewModel(), abbreviated: Boolean = true) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Prayer.obligatory.forEach { p ->
                val status = s.todayStatus[p]
                val enabled = p in s.passed || status != null
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (status != null) status.color() else Color.Transparent)
                            .border(2.dp, if (enabled) status.color().takeIf { status != null } ?: AccentGreen else CardHigh, CircleShape)
                            .clickable(enabled = enabled, role = Role.Checkbox) { vm.toggle(p) }
                            .semantics { contentDescription = "${p.displayName}: ${status.label()}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (status) {
                            PrayerStatus.ON_TIME -> Icon(Icons.Filled.Check, null, tint = Color.Black)
                            PrayerStatus.LATE -> Icon(Icons.Filled.Schedule, null, tint = Color.Black)
                            PrayerStatus.MISSED -> Icon(Icons.Filled.Close, null, tint = Color.Black)
                            null -> {}
                        }
                    }
                    Text(p.label(abbreviated), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(
            "🔥 ${s.stats.currentDays} day streak · ${s.stats.currentPrayers} prayers in a row",
            color = Gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun StreaksScreen(onBack: () -> Unit, vm: StreaksViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    SubScreen("Streaks", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Current streak", "${s.stats.currentDays}", "days", Modifier.weight(1f))
                StatCard("Best streak", "${s.stats.bestDays}", "days", Modifier.weight(1f))
                StatCard("In a row", "${s.stats.currentPrayers}", "prayers", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a prayer once it has started: marked on time ✓, or late if its time has ended. Tap again to clear. " +
                    "Use the 7-day grid below to cycle on time → late → missed.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TodayTracker(vm, abbreviated = true)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PeriodCard("This week", s.stats.week, Modifier.weight(1f))
                PeriodCard("This month", s.stats.month, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Text("Last 12 weeks", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Heatmap(s.stats.heatmap.map { it.second })
            Spacer(Modifier.height(16.dp))
            Text("Last 7 days (tap to change)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            HistoryGrid(s, vm)
            Spacer(Modifier.height(24.dp))
        }
    }
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
private fun PeriodCard(title: String, p: PeriodStats, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Card).padding(12.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${p.prayedPct}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
        Text("${p.prayed}/${p.possible} prayed", style = MaterialTheme.typography.bodyMedium)
        Text("${p.onTimePct}% on time · ${p.late} late · ${p.missed} missed", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Heatmap(values: List<Int>) {
    // columns = weeks, rows = 7 days
    val weeks = values.chunked(7)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics {
        contentDescription = "Heatmap of prayers prayed per day for the last ${values.size} days"
    }) {
        weeks.forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                w.forEach { v ->
                    Box(
                        Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (v == 0) CardHigh else AccentGreen.copy(alpha = 0.2f + 0.16f * v)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryGrid(s: StreaksUi, vm: StreaksViewModel) {
    val days = (6 downTo 0).map { s.today.minusDays(it.toLong()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HistoryRows(days, vm)
    }
}

@Composable
private fun HistoryRows(days: List<java.time.LocalDate>, vm: StreaksViewModel) {
    val all by vm.history.collectAsStateWithLifecycle()
    days.forEach { d ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " ${d.dayOfMonth}", Modifier.weight(1f))
            Prayer.obligatory.forEach { p ->
                val st = all[d]?.get(p)
                Box(
                    Modifier.padding(horizontal = 4.dp).size(30.dp).clip(CircleShape).background(st.color())
                        .clickable(role = Role.Button) { vm.cycle(d, p) }
                        .semantics { contentDescription = "${p.displayName} on $d: ${st.label()}" },
                    contentAlignment = Alignment.Center,
                ) { Text(p.abbr.take(1), color = if (st == null) Color.White else Color.Black, fontSize = 12.sp) }
            }
        }
    }
}
