package com.kamalahmed.prayerapp.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kamalahmed.prayerapp.alarm.Iqamah
import com.kamalahmed.prayerapp.alarm.Sounds
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.safety.guard
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.nafl.Clock12
import com.kamalahmed.prayerapp.ui.common.*
import java.time.LocalDate

/** Quick choices for "minutes before iqamah"; the stepper still allows any 0–180 value. */
val IqamahLeadPresets = listOf(0, 5, 10, 15, 20)

fun iqamahLeadLabel(lead: Int) = if (lead == 0) "At iqamah" else "$lead min before"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeadChips(lead: Int, onPick: (Int) -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IqamahLeadPresets.forEach { m ->
            FilterChip(selected = lead == m, onClick = { onPick(m) }, label = { Text(if (m == 0) "At iqamah" else "$m min") })
        }
    }
}

@Composable
fun IqamahSettingsContent(
    s: AppSettings,
    update: ((AppSettings)->AppSettings)->Unit,
    notify: (Prayer?,Boolean)->Unit,
    onPickRingtone: () -> Unit = {},
    onPreview: (Boolean) -> Unit = {},
    previewing: Boolean = false,
) {
    val context = LocalContext.current
    fun pick(minute: Int,save: (Int)->Unit) {
        guard("Iqamah","clock picker") {
            TimePickerDialog(context,{ _,h,m -> save(h*60+m) },minute/60,minute%60,false).show()
        }
    }
    val day = remember(s) { runCatching { PrayerSchedule(s.toCalcParams()).day(LocalDate.now()) }.getOrNull() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        CardGroup {
            SwitchRow("Show iqamah times",s.iqamaEnabled,subtitle = "Display on Times / Today; does not enable alarms") { on -> update { it.copy(iqamaEnabled = on) } }
            Text("Two separate alerts per prayer: the adhan alarm at prayer start (Settings › Notifications) and an optional before-iqamah alarm here. Iqamah times follow the adjusted start time from Settings › Calculation.",Modifier.padding(16.dp),style = MaterialTheme.typography.bodySmall)
        }
        Prayer.obligatory.forEach { p ->
            SectionHeader(p.displayName)
            val fixed = s.iqamaFixed[p]
            val offset = s.iqama(p).coerceIn(0,180)
            CardGroup {
                SwitchRow("Fixed clock time",fixed != null,subtitle = if (fixed == null) "Minutes after adhan" else Clock12.label(fixed)) { on ->
                    update { old ->
                        val initial = day?.let { Iqamah.time(old,p,it[p]) }?.let { it.hour*60+it.minute } ?: 780
                        old.copy(iqamaFixed = if (on) old.iqamaFixed + (p to initial) else old.iqamaFixed - p)
                    }
                }
                if (fixed != null) ListRow("Iqamah at ${Clock12.label(fixed)}",subtitle = "Tap to set · AM / PM",onClick = { pick(fixed) { minute -> update { it.copy(iqamaFixed = it.iqamaFixed + (p to minute)) } } })
                else StepperRow("After adhan","$offset min",onMinus = { update { it.copy(iqamaMinutes = it.iqamaMinutes + (p to (offset-1).coerceAtLeast(0))) } },onPlus = { update { it.copy(iqamaMinutes = it.iqamaMinutes + (p to (offset+1).coerceAtMost(180))) } })
                val lead = (s.iqamaBefore[p] ?: 0).coerceIn(0,180)
                val enabled = p in s.iqamaNotifications
                day?.let { times ->
                    val at = Iqamah.time(s,p,times[p])
                    val alert = at?.minusMinutes(lead.toLong())
                    val nextDay = at != null && at.toLocalDate() > times[p].toLocalDate()
                    Text(when {
                        at == null -> "Time is before adhan today: no iqamah alarm will fire."
                        enabled -> "Today: iqamah ${Clock12.label(at.hour*60+at.minute)}${if (nextDay) " (+1 day)" else ""} · alarm ${Clock12.label(alert!!.hour*60+alert.minute)}"
                        else -> "Today: iqamah ${Clock12.label(at.hour*60+at.minute)}${if (nextDay) " (+1 day)" else ""}"
                    },Modifier.padding(16.dp),style = MaterialTheme.typography.bodySmall)
                }
                RowDivider()
                SwitchRow("Before-iqamah alarm",enabled,subtitle = "Off by default · separate from the prayer-start adhan") { notify(p,it) }
                if (enabled) {
                    LeadChips(lead) { m -> update { it.copy(iqamaBefore = it.iqamaBefore + (p to m)) } }
                    StepperRow("Custom minutes before",iqamahLeadLabel(lead),onMinus = { update { it.copy(iqamaBefore = it.iqamaBefore + (p to (lead-1).coerceAtLeast(0))) } },onPlus = { update { it.copy(iqamaBefore = it.iqamaBefore + (p to (lead+1).coerceAtMost(180))) } })
                    val loud = p !in s.iqamaQuiet
                    SwitchRow("Full alarm sound",loud,subtitle = if (loud) "Alarm volume · plays the iqamah sound below until stopped" else "Quiet notification · uses notification volume") { on ->
                        update { it.copy(iqamaQuiet = if (on) it.iqamaQuiet - p else it.iqamaQuiet + p) }
                    }
                }
            }
        }
        SectionHeader("Friday Jumu'ah (optional)")
        CardGroup {
            SwitchRow("Jumu'ah override",s.jumuahEnabled,subtitle = "Replaces Friday Dhuhr iqamah only") { on -> update { it.copy(jumuahEnabled = on) } }
            if (s.jumuahEnabled) {
                SwitchRow("Fixed Jumu'ah time",s.jumuahFixed,subtitle = "Clock time or minutes after Dhuhr adhan") { on -> update { it.copy(jumuahFixed = on) } }
                if (!s.jumuahFixed) StepperRow("After Dhuhr adhan","${s.jumuahOffset} min",onMinus = { update { it.copy(jumuahOffset = (it.jumuahOffset-1).coerceAtLeast(0)) } },onPlus = { update { it.copy(jumuahOffset = (it.jumuahOffset+1).coerceAtMost(180)) } })
                else ListRow("Jumu'ah at ${Clock12.label(s.jumuahMinute)}",subtitle = "Fixed clock time · AM / PM",onClick = { pick(s.jumuahMinute) { minute -> update { it.copy(jumuahMinute = minute) } } })
                SwitchRow("Before-Jumu'ah alarm",s.jumuahNotify,subtitle = "Separate Friday opt-in") { notify(null,it) }
                if (s.jumuahNotify) {
                    LeadChips(s.jumuahBefore) { m -> update { it.copy(jumuahBefore = m) } }
                    StepperRow("Custom minutes before",iqamahLeadLabel(s.jumuahBefore),onMinus = { update { it.copy(jumuahBefore = (it.jumuahBefore-1).coerceAtLeast(0)) } },onPlus = { update { it.copy(jumuahBefore = (it.jumuahBefore+1).coerceAtMost(180)) } })
                    SwitchRow("Full alarm sound",!s.jumuahQuiet,subtitle = if (!s.jumuahQuiet) "Alarm volume" else "Quiet notification") { on -> update { it.copy(jumuahQuiet = !on) } }
                }
            }
        }
        SectionHeader("Iqamah alarm sound")
        CardGroup {
            val choices = listOf(AppSettings.IQAMAH_SYSTEM_ALARM to "System alarm sound") + Sounds.bundled.map { it.id to it.label }
            choices.forEachIndexed { i, (id, label) ->
                if (i > 0) RowDivider()
                ListRow(label, onClick = { update { it.copy(iqamahSound = id, iqamahSoundName = label) } }, showChevron = false) {
                    RadioButton(selected = s.iqamahSound == id, onClick = null)
                }
            }
            RowDivider()
            ListRow("Ringtone / alarm…", subtitle = if (s.iqamahSound == AppSettings.IQAMAH_RINGTONE) s.iqamahSoundName else "Pick any system sound", onClick = onPickRingtone) {
                RadioButton(selected = s.iqamahSound == AppSettings.IQAMAH_RINGTONE, onClick = null)
            }
            RowDivider()
            ListRow(if (previewing) "Stop preview" else "Preview iqamah alarm", subtitle = "Plays on the alarm stream at the volume below", onClick = { onPreview(!previewing) }, showChevron = false)
            RowDivider()
            SliderRow("Volume", s.iqamahVolume.toFloat(), 10f..100f, "${s.iqamahVolume}%") { v -> update { it.copy(iqamahVolume = v.toInt().coerceIn(10,100)) } }
            SliderRow("Stop after", s.iqamahMaxPlaySeconds.toFloat(), 10f..600f, "${s.iqamahMaxPlaySeconds} s") { v -> update { it.copy(iqamahMaxPlaySeconds = v.toInt().coerceIn(10,600)) } }
            SwitchRow("Repeat sound", s.iqamahLoop, subtitle = "Loop until Stop or the time limit") { on -> update { it.copy(iqamahLoop = on) } }
            SwitchRow("Full-screen alarm", s.iqamahFullScreen, subtitle = "Lock-screen Stop button, like the adhan alarm") { on -> update { it.copy(iqamahFullScreen = on) } }
        }
        Text("Fixed iqamah times before adhan are skipped (Isha may roll to the next day). Full alarms use the ALARM stream and Android's alarm-clock scheduling; notification permission, battery and exact-alarm settings still apply.",Modifier.padding(16.dp),style = MaterialTheme.typography.bodySmall)
    }
}
