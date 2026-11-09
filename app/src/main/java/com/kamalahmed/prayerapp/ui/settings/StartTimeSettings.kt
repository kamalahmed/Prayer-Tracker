package com.kamalahmed.prayerapp.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.core.prayer.PrayerDay
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.core.prayer.StartTimeLimits
import com.kamalahmed.prayerapp.core.safety.guard
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.ui.common.*
import java.time.LocalDate
import java.time.ZonedDateTime

/** Pure helpers for the start-time editor (unit-tested). */
object StartTimeText {
    fun offset(min: Int): String {
        if (min == 0) return "0 min"
        val sign = if (min > 0) "+" else "−"
        val a = kotlin.math.abs(min)
        return if (a < 60) "$sign$a min" else "$sign$a min (${a / 60} h ${a % 60} min)"
    }

    /** Offset that moves [calcMinute] to the masjid clock time [pickedMinute], taking the shorter way round midnight. */
    fun offsetFor(calcMinute: Int, pickedMinute: Int): Int {
        var d = pickedMinute - calcMinute
        if (d > 720) d -= 1440
        if (d < -720) d += 1440
        return d.coerceIn(StartTimeLimits.offsetRange)
    }

    fun summary(offset: Int, fixed: Int?, calculated: String?, final: String?, limited: Boolean): String {
        val base = when {
            fixed != null -> "Fixed ${final ?: ""} · calculated ${calculated ?: "—"}"
            offset != 0 -> "Calculated ${calculated ?: "—"} ${offset(offset)} → ${final ?: "—"}"
            else -> "Calculated ${calculated ?: "—"}"
        }
        return if (limited) "$base · limited to keep prayer order" else base
    }
}

private fun minuteOf(z: ZonedDateTime) = z.hour * 60 + z.minute

@Composable
fun StartTimeAdjustments(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val today = remember { LocalDate.now() }
    val calculated: PrayerDay? = remember(s.calcMethod, s.latitude, s.longitude, s.timeZoneId, s.asrMethod, s.highLatRule) {
        runCatching { PrayerSchedule(s.copy(adjustments = emptyMap(), startFixed = emptyMap()).toCalcParams()).day(today) }.getOrNull()
    }
    val adjusted: PrayerDay? = remember(s) { runCatching { PrayerSchedule(s.toCalcParams()).day(today) }.getOrNull() }
    var editing by remember { mutableStateOf<Prayer?>(null) }
    SectionHeader("Prayer start time (adhan)")
    CardGroup {
        Text(
            "These change when each prayer STARTS. The adhan alarm, notifications, widgets, Today and tracking all use the adjusted time. " +
                "To keep the real start time and add the masjid's congregation time instead, use Settings › Iqamah.",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
        )
        Prayer.entries.forEachIndexed { i, p ->
            RowDivider()
            val calc = calculated?.get(p)?.let { TimeFormat.time(it, s.use24h) }
            val fin = adjusted?.get(p)?.let { TimeFormat.time(it, s.use24h) }
            ListRow(p.displayName,
                subtitle = StartTimeText.summary(s.adjustment(p), s.startFixed[p], calc, fin, adjusted?.limited?.contains(p) == true),
                onClick = { editing = p })
        }
    }
    editing?.let { p ->
        StartTimeDialog(p, s, calculated?.get(p), onDismiss = { editing = null }) { offset, fixed ->
            update { old ->
                old.copy(
                    adjustments = old.adjustments + (p to offset.coerceIn(StartTimeLimits.offsetRange)),
                    startFixed = if (fixed != null && p.isPrayer) old.startFixed + (p to fixed.coerceIn(0, 1439)) else old.startFixed - p,
                )
            }
            editing = null
        }
    }
}

@Composable
private fun StartTimeDialog(p: Prayer, s: AppSettings, calculated: ZonedDateTime?, onDismiss: () -> Unit, onSave: (Int, Int?) -> Unit) {
    val context = LocalContext.current
    var fixedMode by remember { mutableStateOf(p.isPrayer && s.startFixed[p] != null) }
    var offset by remember { mutableIntStateOf(s.adjustment(p)) }
    var fixed by remember { mutableIntStateOf(s.startFixed[p] ?: calculated?.let { minuteOf(it.plusMinutes(s.adjustment(p).toLong())) } ?: 780) }
    fun setOffset(v: Int) { offset = v.coerceIn(StartTimeLimits.offsetRange) }
    fun pick(initial: Int, save: (Int) -> Unit) = guard("StartTime", "clock picker") {
        TimePickerDialog(context, { _, h, m -> save(h * 60 + m) }, initial / 60, initial % 60, s.use24h).show()
    }
    fun label(minute: Int) = TimeFormat.time(java.time.LocalTime.of(minute / 60, minute % 60), s.use24h)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${p.displayName} start time") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calculated today: ${calculated?.let { TimeFormat.time(it, s.use24h) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                ModeRow("Calculated time + offset", !fixedMode) { fixedMode = false }
                if (p.isPrayer) ModeRow("Fixed clock time every day", fixedMode) { fixedMode = true }
                if (!fixedMode) {
                    Text(StartTimeText.offset(offset), style = MaterialTheme.typography.titleMedium)
                    listOf(listOf(-60, -15, -1), listOf(1, 15, 60)).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { d ->
                                OutlinedButton(onClick = { setOffset(offset + d) }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    Text(if (d > 0) "+$d" else "−${-d}")
                                }
                            }
                        }
                    }
                    Text("Allowed: −360 to +360 min (6 hours either way).", style = MaterialTheme.typography.bodySmall)
                    if (calculated != null) TextButton(onClick = {
                        pick(minuteOf(calculated.plusMinutes(offset.toLong()))) { m -> setOffset(StartTimeText.offsetFor(minuteOf(calculated), m)) }
                    }) { Text("Set from the masjid's clock time…") }
                    Text("New start today: ${calculated?.plusMinutes(offset.toLong())?.let { TimeFormat.time(it, s.use24h) } ?: "—"}", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedButton(onClick = { pick(fixed) { fixed = it } }) { Text("Starts at ${label(fixed)}") }
                    if (p == Prayer.ISHA) Text("Isha before 12:00 PM counts as after midnight (the same night).", style = MaterialTheme.typography.bodySmall)
                }
                Text("Times are kept in order (each prayer after the previous one, Isha before the next Fajr).", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (fixedMode) s.adjustment(p) else offset, if (fixedMode) fixed else null) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(0, null) }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ModeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null); Spacer(Modifier.width(8.dp)); Text(label)
    }
}
