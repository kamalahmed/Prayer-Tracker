package com.kamalahmed.prayerapp.ui.nafl

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.safety.AppErrors
import com.kamalahmed.prayerapp.core.safety.guard
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.tracker.StatsPeriod
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import com.kamalahmed.prayerapp.ui.streaks.*
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.Gold
import com.kamalahmed.prayerapp.ui.theme.Danger
import com.kamalahmed.prayerapp.ui.theme.CardHigh
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun NaflScreen(vm: NaflViewModel = hiltViewModel(), onSummaries: ()->Unit={}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val failed by vm.loadFailed.collectAsStateWithLifecycle()
    val s = state
    if (s == null) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (failed) "Could not load optional worship. Your saved history is unchanged." else "Loading optional worship…")
            if (failed) Button(onClick = { vm.retry() }) { Text("Retry") }
        }
        return
    }
    NaflContent(s, vm::log, vm::item, vm::add, vm::reminder, vm::removeReminder, vm::period, vm::shift, vm::day, vm::addTyped, onSummaries)
}

/** Stateless dashboard boundary so every editor and layout can be exercised without Hilt. */
@Composable
fun NaflContent(
    s: NaflUi,
    onLog: (NaflItem, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onItem: (NaflItem) -> Unit = {},
    onAdd: (String) -> Unit = {},
    onReminder: (QuranReminder) -> Unit = {},
    onRemoveReminder: (Long) -> Unit = {},
    onPeriod: (StatsPeriod) -> Unit = {},
    onShift: (Long) -> Unit = {},
    onDay: (LocalDate) -> Unit = {},
    onAddTyped: (String,String,Int) -> Unit = { name, _, _ -> onAdd(name) },
    onSummaries: ()->Unit={},
) {
    var overview by rememberSaveable { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<NaflItem?>(null) }
    var customize by rememberSaveable { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<NaflItem?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var reminder by remember { mutableStateOf<QuranReminder?>(null) }
    var pendingPermission by remember { mutableStateOf<QuranReminder?>(null) }
    val ctx = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.let { onReminder(it.copy(enabled = granted)) }
        pendingPermission = null
        if (!granted) AppErrors.report("Notifications are not allowed. The Quran reminder remains off.")
    }
    fun saveReminder(r: QuranReminder) {
        if (r.enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingPermission = r
            guard("NaflScreen", "notification permission") { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        } else onReminder(r)
    }
    val dayLogs = s.logs.filter { it.date == s.day.toString() }.associateBy { it.itemId }
    editingLog?.let { item ->
        LogEditor(item, dayLogs[item.id], { editingLog = null }) { n, p, m -> onLog(item, n, p, m); editingLog = null }
        return
    }
    if (customize) {
        EditorPage("Customize worship", { customize = false }, { customize = false }, confirmLabel = "Done") {
            Text("Hide items without losing history. Tap a name to rename it.", style = MaterialTheme.typography.bodySmall)
            s.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { editItem = item; customize = false }, modifier = Modifier.weight(1f)) { Text(item.name) }
                    Switch(checked = item.enabled, onCheckedChange = { onItem(item.copy(enabled = it)) }, modifier = Modifier.semantics { contentDescription = "Show ${item.name}" })
                }
            }
            TextButton(onClick = { adding = true; customize = false }) { Text("Add custom item") }
        }
        return
    }
    if (adding || editItem != null) { NameEditor(editItem, { adding = false; editItem = null }) { name, type, target ->
        editItem?.let { onItem(it.copy(name = name, type = type, dailyTarget = target)) } ?: onAddTyped(name,type,target)
        adding = false; editItem = null
    }; return
    }
    reminder?.let { r -> ReminderEditor(r, { reminder = null }) { saveReminder(it); reminder = null }; return }
    val done = NaflStats.rakaat(dayLogs.values.toList())
    Column(Modifier.fillMaxSize()) {
      NaflModeSwitch(overview,{overview=it})
      if(overview) {
          NaflOverview(s,onPeriod,onShift,{onDay(it);overview=false},onSummaries,Modifier.weight(1f))
      } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(done / 12f, 108.dp, AccentGreen, stroke = 12.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$done / 12", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                        Text("Sunnah rakaat", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Small deeds, steady habits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("A private space for optional worship. Every effort counts.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onDay(s.day.minusDays(1)) }, modifier = Modifier.semantics { contentDescription = "Previous logging day" }) { Text("‹") }
                TextButton(onClick = {
                    guard("NaflScreen", "date picker") {
                        DatePickerDialog(ctx, { _, y, m, d -> onDay(LocalDate.of(y, m + 1, d)) }, s.day.year, s.day.monthValue - 1, s.day.dayOfMonth)
                            .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }
                }) { Text(if (s.day == s.today) "Today" else s.day.format(DateTimeFormatter.ofPattern("d MMM yyyy"))) }
                TextButton(onClick = { onDay(s.day.plusDays(1)) }, enabled = s.day < s.today, modifier = Modifier.semantics { contentDescription = "Next logging day" }) { Text("›") }
            }
            if (s.day != s.today) TextButton(onClick = { onDay(s.today) }) { Text("Back to today") }
        }
        val blocks = listOf<@Composable () -> Unit>(
            { Panel("Daily Sunnah", "12 rakaat: 2 before Fajr, 4 + 2 Dhuhr, 2 after Maghrib, 2 after Isha") {
                s.items.filter { it.group == "SUNNAH" && it.enabled }.forEach { item ->
                    val log = dayLogs[item.id]
                    WorshipRow(item, log, { onLog(item, if ((log?.count ?: 0) > 0) 0 else 1, 0, 0) }, { editingLog = item })
                }
                Text("Optional slots never increase the 12-rakaat total. Witr and other practices vary; customize to suit yours.", style = MaterialTheme.typography.bodySmall)
            } },
            { Panel("Other worship", "Check off a deed. Count opens today’s tasbih; Edit can backfill counts. Quran pages and minutes are optional.") {
                s.items.filter { it.group != "SUNNAH" && it.enabled }.forEach { item ->
                    val log = dayLogs[item.id]
                    WorshipRow(item, log, { onLog(item, if ((log?.count ?: 0) > 0) 0 else 1, log?.pages ?: 0, log?.minutes ?: 0) }, { editingLog = item })
                }
                OutlinedButton(onClick = { customize = true }, modifier = Modifier.fillMaxWidth()) { Text("Customize worship") }
            } },
            { Panel("Calendar", "Tap a past day to log. Intensity = completed worship items; blank = not tracked yet.") {
                ContributionGrid(s.report.daily.map { it.date to it.completed }, s.today, AccentGreen, CardHigh, onDay,
                    maxCount = s.items.size.coerceAtLeast(1), unit = "worship items")
                Text("More green = more items completed. Zero is not a judgement.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            } },
            { Panel("Quran reminders", "Off by default. Choose one or more daily times; notification-style unless alarm sound is enabled.") {
                s.reminders.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { reminder = r }.padding(vertical = 8.dp)) {
                            Text(timeLabel(r.minute), fontWeight = FontWeight.Bold)
                            Text(if (r.alarmSound) "Alarm sound · tap to edit" else "Notification · tap to edit", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = r.enabled, onCheckedChange = { saveReminder(r.copy(enabled = it)) }, modifier = Modifier.semantics { contentDescription = "Enable Quran reminder at ${timeLabel(r.minute)}" })
                        TextButton(onClick = { onRemoveReminder(r.id) }) { Text("Delete") }
                    }
                }
                if (s.reminders.isEmpty()) Text("No reminders set", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { reminder = QuranReminder(minute = 20 * 60) }, enabled = s.reminders.size < 12, modifier = Modifier.fillMaxWidth()) { Text("Add reminder") }
                Text("Delivery follows your device time zone and Android notification settings. Without exact-alarm access it may be delayed. Alarm sound is a notification sound, not a full-screen or continuous alarm.", style = MaterialTheme.typography.bodySmall)
                if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) Text("Notifications are blocked. Allow them in Android settings to receive reminders.", color = Gold, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { guard("NaflScreen", "notification settings") { ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)) } }) { Text("Notification settings") }
            } },
        )
        val cols = LocalAdaptive.current.dashboardColumns.coerceIn(1, 2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(cols) { c ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    blocks.filterIndexed { i, _ -> i % cols == c }.forEach { it() }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
}

@Composable
private fun WorshipRow(item: NaflItem, log: NaflLog?, toggle: () -> Unit, edit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val context = LocalContext.current
        val openCounter: () -> Unit = { guard("Nafl", "open counter") { context.startActivity(Intent(context,TasbihActivity::class.java).putExtra("counterId",item.id)) } }
        if (item.isCounter) TextButton(onClick = openCounter) { Text("Count") }
        else Checkbox(checked = (log?.count ?: 0) > 0, onCheckedChange = { toggle() }, modifier = Modifier.semantics { contentDescription = "Mark ${item.name}" })
        Column(Modifier.weight(1f).clickable(onClick = if (item.isCounter) openCounter else toggle).padding(vertical = 8.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(when {
                item.rakaat > 0 -> "${item.rakaat} rakaat" + if (item.baseline) " · daily Sunnah" else " · optional slot"
                item.id == "quran" -> "${log?.pages ?: 0} pages · ${log?.minutes ?: 0} min"
                item.isCounter -> "${log?.count ?: 0}" + if (item.dailyTarget > 0) " / ${item.dailyTarget} daily target" else " counted"
                else -> "${log?.count ?: 0} logged"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = edit, modifier = Modifier.semantics { contentDescription = "Edit ${item.name}" }) { Text("Edit") }
    }
}

@Composable
private fun LogEditor(item: NaflItem, log: NaflLog?, dismiss: () -> Unit, save: (Int, Int, Int) -> Unit) {
    val checkboxOnly = item.rakaat > 0 || item.id == "quran"
    var count by rememberSaveable { mutableStateOf((log?.count ?: 0).toString()) }
    var pages by rememberSaveable { mutableStateOf((log?.pages ?: 0).toString()) }
    var minutes by rememberSaveable { mutableStateOf((log?.minutes ?: 0).toString()) }
    val valid = count.toIntOrNull() in 0..(if (checkboxOnly) 1 else if (item.isCounter) 1_000_000 else 10000) && pages.ifBlank { "0" }.toIntOrNull() in 0..10000 && minutes.ifBlank { "0" }.toIntOrNull() in 0..1440
    EditorPage(item.name, dismiss, { save(count.toIntOrNull() ?: 0, pages.ifBlank { "0" }.toIntOrNull() ?: 0, minutes.ifBlank { "0" }.toIntOrNull() ?: 0) }, valid) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (checkboxOnly) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = count == "1", onCheckedChange = { count = if (it) "1" else "0" }); Text("Completed")
            } else NumberField(if (item.isCounter) "Count (0–1000000)" else "Count (0–10000)", count, { count = it })
            if (item.id == "quran") {
                NumberField("Pages (optional)", pages, { pages = it })
                NumberField("Minutes (optional)", minutes, { minutes = it })
                Text("Pages/minutes do not check Completed automatically.", style = MaterialTheme.typography.bodySmall)
            }
            if (!valid) Text("Enter a valid count, up to 10000 pages and 1440 minutes.", color = Danger, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= 7) change(it) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NameEditor(initial: NaflItem?, dismiss: () -> Unit, save: (String,String,Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var counter by rememberSaveable { mutableStateOf(initial?.isCounter ?: false) }
    var target by rememberSaveable { mutableStateOf((initial?.dailyTarget ?: 0).takeIf { it > 0 }?.toString().orEmpty()) }
    val valid = name.isNotBlank() && (!counter || target.ifBlank { "0" }.toIntOrNull() in 0..1_000_000)
    EditorPage(if (initial == null) "Add custom item" else "Rename item", dismiss,
        { save(name.trim(),if (counter) "COUNTER" else "CHECKBOX", if (counter) target.toIntOrNull() ?: 0 else 0) },valid) {
        OutlinedTextField(name, { if (it.length <= 60) name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (initial == null || (initial.rakaat == 0 && initial.id != "quran")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = counter,onCheckedChange = { counter = it }); Text("Counter item")
            }
            Text(if (counter) "Tap counter · counts persist each day" else "Daily checkbox / manual count",style = MaterialTheme.typography.bodySmall)
        }
        if (counter) NumberField("Daily target (optional)",target,{ target = it })
        Text("Changing type or target keeps all previous counts.",style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReminderEditor(initial: QuranReminder, dismiss: () -> Unit, save: (QuranReminder) -> Unit) {
    var hour by rememberSaveable { mutableStateOf(((initial.minute / 60 % 12).let { if (it == 0) 12 else it }).toString()) }
    var minute by rememberSaveable { mutableStateOf((initial.minute % 60).toString()) }
    var pm by rememberSaveable { mutableStateOf(initial.minute >= 720) }
    var sound by rememberSaveable { mutableStateOf(initial.alarmSound) }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    val valid = hour.toIntOrNull() in 1..12 && minute.toIntOrNull() in 0..59
    EditorPage("Quran reading reminder", dismiss, { save(initial.copy(minute = Clock12.minute(hour.toIntOrNull() ?: 12,minute.toIntOrNull() ?: 0,pm), enabled = enabled, alarmSound = sound)) }, valid) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Daily time (12-hour clock). Reminders start off.")
            NumberField("Hour (1–12)", hour, { hour = it })
            NumberField("Minute (0–59)", minute, { minute = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !pm,onClick = { pm = false },label = { Text("AM") })
                FilterChip(selected = pm,onClick = { pm = true },label = { Text("PM") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = enabled, onCheckedChange = { enabled = it }); Text("Enable reminder") }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = sound, onCheckedChange = { sound = it }); Text("Use alarm sound") }
            Text("Sound uses the Android default alarm tone once. Manage volume and channels in Notification settings.", style = MaterialTheme.typography.bodySmall)
            if (!valid) Text("Enter an hour from 1 to 12 and minute from 0 to 59.", color = Danger)
        }
    }
}

private fun timeLabel(minute: Int): String = Clock12.label(minute)

/** Full-page editors keep forms usable with large fonts, split-screen and an open keyboard. */
@Composable
private fun EditorPage(title: String, dismiss: () -> Unit, save: () -> Unit, valid: Boolean = true, confirmLabel: String = "Save", content: @Composable ColumnScope.() -> Unit) {
    androidx.activity.compose.BackHandler(onBack = dismiss)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(title) { content() }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = valid, onClick = save) { Text(confirmLabel) }
            OutlinedButton(onClick = dismiss) { Text("Cancel") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
