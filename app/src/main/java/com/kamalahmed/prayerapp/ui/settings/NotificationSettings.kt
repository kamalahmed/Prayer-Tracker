package com.kamalahmed.prayerapp.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.alarm.AdhanService
import com.kamalahmed.prayerapp.alarm.Sounds
import com.kamalahmed.prayerapp.alarm.SystemStatus
import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.SoundSource
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SectionHeader
import com.kamalahmed.prayerapp.ui.common.SliderRow
import com.kamalahmed.prayerapp.ui.common.StepperRow
import com.kamalahmed.prayerapp.ui.common.SubScreen
import com.kamalahmed.prayerapp.ui.common.SwitchRow
import com.kamalahmed.prayerapp.ui.times.icon

@Composable
fun NotificationSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onSound: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st = s ?: return
    val ctx = LocalContext.current
    var fullScreenPrompt by remember { mutableStateOf(false) }
    SubScreen("Notifications & Adhan", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Per prayer")
            CardGroup {
                Prayer.entries.forEachIndexed { i, p ->
                    var open by remember { mutableStateOf(false) }
                    ListRow(p.displayName, subtitle = st.mode(p).label, leading = st.mode(p).icon(), onClick = { open = true }) {
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            NotifyMode.entries.forEach { m ->
                                DropdownMenuItem(text = { Text(m.label) }, leadingIcon = { Icon(m.icon(), null) }, onClick = {
                                    open = false
                                    vm.update { it.copy(notifyModes = it.notifyModes + (p to m)) }
                                })
                            }
                        }
                    }
                    if (i < Prayer.entries.lastIndex) RowDivider()
                }
            }
            SectionHeader("Adhan sound")
            CardGroup {
                ListRow("Sound", subtitle = st.soundName, onClick = onSound)
                RowDivider()
                SwitchRow("Show full-screen notification", st.fullScreenAlarm, "Lock-screen alarm with Stop / Snooze") { on ->
                    vm.update { it.copy(fullScreenAlarm = on) }
                    if (on && !AdhanService.canUseFullScreen(ctx)) fullScreenPrompt = true
                }
                RowDivider()
                StepperRow("Snooze", "${st.snoozeMinutes} min",
                    onMinus = { vm.update { it.copy(snoozeMinutes = (it.snoozeMinutes - 1).coerceAtLeast(1)) } },
                    onPlus = { vm.update { it.copy(snoozeMinutes = (it.snoozeMinutes + 1).coerceAtMost(60)) } })
                RowDivider()
                SwitchRow("Vibrate", st.vibrate) { on -> vm.update { it.copy(vibrate = on) } }
                RowDivider()
                SwitchRow("Adhan dua", st.adhanDua, "Show the dua after the adhan finishes") { on -> vm.update { it.copy(adhanDua = on) } }
            }
            SectionHeader("Reminders")
            CardGroup {
                StepperRow("Pre-prayer reminder", if (st.preReminderMinutes == 0) "off" else "${st.preReminderMinutes} min before",
                    onMinus = { vm.update { it.copy(preReminderMinutes = (it.preReminderMinutes - 5).coerceAtLeast(0)) } },
                    onPlus = { vm.update { it.copy(preReminderMinutes = (it.preReminderMinutes + 5).coerceAtMost(60)) } })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (fullScreenPrompt) {
        AlertDialog(
            onDismissRequest = { fullScreenPrompt = false },
            title = { Text("Allow full-screen alarms") },
            text = {
                Text(
                    "Android 14+ requires you to allow \"Full-screen notifications\" for Prayer App so the adhan can show on the lock screen. " +
                        "On the next screen, turn on the switch for Prayer App, then come back.",
                )
            },
            confirmButton = {
                TextButton(onClick = { fullScreenPrompt = false; SystemStatus.open(ctx, SystemStatus.fullScreenSettings(ctx)) }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { fullScreenPrompt = false }) { Text("Later") } },
        )
    }
}

@Composable
fun SoundSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()
    val st = s ?: return
    val ctx = LocalContext.current

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33)
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else @Suppress("DEPRECATION") res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val title = uri?.let { RingtoneManager.getRingtone(ctx, it)?.getTitle(ctx) } ?: "Ringtone"
            vm.setRingtone(uri, title)
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCustom(uri)
    }

    SubScreen("Adhan sound", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Built-in")
            CardGroup {
                Sounds.bundled.forEachIndexed { i, b ->
                    val selected = st.soundSource == SoundSource.BUNDLED && st.bundledSound == b.id
                    ListRow(b.label, onClick = {
                        vm.update { it.copy(soundSource = SoundSource.BUNDLED, bundledSound = b.id, fajrBundledSound = b.id, soundName = b.label) }
                    }, showChevron = false) { RadioButton(selected = selected, onClick = null) }
                    if (i < Sounds.bundled.lastIndex) RowDivider()
                }
            }
            SectionHeader("Your sounds")
            CardGroup {
                ListRow(
                    "System ringtone / alarm…",
                    subtitle = if (st.soundSource == SoundSource.RINGTONE) st.soundName else "Pick any ringtone, alarm or notification sound",
                    onClick = {
                        ringtoneLauncher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Adhan sound")
                            if (st.soundSource == SoundSource.RINGTONE) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, st.soundUri?.let(Uri::parse))
                        })
                    },
                ) { RadioButton(selected = st.soundSource == SoundSource.RINGTONE, onClick = null) }
                RowDivider()
                ListRow(
                    "Custom audio file…",
                    subtitle = if (st.soundSource == SoundSource.CUSTOM_FILE) st.soundName else "MP3, OGG, M4A, WAV from your files",
                    onClick = { fileLauncher.launch(arrayOf("audio/*")) },
                ) { RadioButton(selected = st.soundSource == SoundSource.CUSTOM_FILE, onClick = null) }
            }
            SectionHeader("Playback")
            CardGroup {
                SliderRow("Maximum play time", st.maxPlaySeconds.toFloat(), 15f..900f, formatDuration(st.maxPlaySeconds), onChange = { v ->
                    vm.update { it.copy(maxPlaySeconds = (v / 15).toInt() * 15) }
                })
                RowDivider()
                SwitchRow("Loop until stopped / max time", st.loopSound) { on -> vm.update { it.copy(loopSound = on) } }
                RowDivider()
                SwitchRow("Use alarm volume", st.useAlarmStream, if (st.useAlarmStream) "Plays even when the phone is on silent" else "Uses media volume") { on ->
                    vm.update { it.copy(useAlarmStream = on) }
                }
                RowDivider()
                SliderRow("Volume", st.volumePercent.toFloat(), 10f..100f, "${st.volumePercent}%", onChange = { v ->
                    vm.update { it.copy(volumePercent = v.toInt()) }
                })
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (playing != null) {
                    Button(onClick = vm::stopPreview) { Icon(Icons.Filled.Stop, null); Text(" Stop preview") }
                } else {
                    Button(onClick = vm::preview) { Icon(Icons.Filled.PlayArrow, null); Text(" Play preview") }
                }
            }
            Text(
                "The adhan is played by a foreground service, so the full recording plays (up to the maximum time), " +
                    "not a short notification clip. Preview is limited to 30 seconds.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

fun formatDuration(sec: Int): String = when {
    sec < 60 -> "$sec s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "${sec / 60} min ${sec % 60} s"
}
