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
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.safety.guard
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.nafl.Clock12
import com.kamalahmed.prayerapp.ui.common.*
import java.time.LocalDate

@Composable
fun IqamahSettingsContent(s: AppSettings,update: ((AppSettings)->AppSettings)->Unit,notify: (Prayer?,Boolean)->Unit) {
    val context = LocalContext.current
    fun pick(minute: Int,save: (Int)->Unit) {
        guard("Iqamah","clock picker") {
            TimePickerDialog(context,{ _,h,m -> save(h*60+m) },minute/60,minute%60,false).show()
        }
    }
    val day = remember(s) { runCatching { PrayerSchedule(s.toCalcParams()).day(LocalDate.now()) }.getOrNull() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        CardGroup {
            SwitchRow("Show iqamah times",s.iqamaEnabled,subtitle = "Display on Times / Today; does not enable reminders") { on -> update { it.copy(iqamaEnabled = on) } }
            Text("Reminders are separately opt-in, even when adhan notifications are off. Widgets choose their own Start / Iqamah / Both display.",Modifier.padding(16.dp),style = MaterialTheme.typography.bodySmall)
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
                day?.let { times ->
                    val at = Iqamah.time(s,p,times[p])
                    Text(if (at == null) "Time is before adhan today: no iqamah reminder will fire." else "Today: ${Clock12.label(at.hour*60+at.minute)}${if (at.toLocalDate() > times[p].toLocalDate()) " (+1 day)" else ""}",Modifier.padding(16.dp),style = MaterialTheme.typography.bodySmall)
                }
                RowDivider()
                SwitchRow("Iqamah notification",p in s.iqamaNotifications,subtitle = "Off by default · independent of adhan") { notify(p,it) }
                val lead = s.iqamaBefore[p] ?: 0
                StepperRow("Remind before iqamah",if (lead == 0) "At iqamah" else "$lead min",onMinus = { update { it.copy(iqamaBefore = it.iqamaBefore + (p to (lead-1).coerceAtLeast(0))) } },onPlus = { update { it.copy(iqamaBefore = it.iqamaBefore + (p to (lead+1).coerceAtMost(180))) } })
            }
        }
        SectionHeader("Friday Jumu'ah (optional)")
        CardGroup {
            SwitchRow("Jumu'ah override",s.jumuahEnabled,subtitle = "Replaces Friday Dhuhr iqamah only") { on -> update { it.copy(jumuahEnabled = on) } }
            if (s.jumuahEnabled) {
                SwitchRow("Fixed Jumu'ah time",s.jumuahFixed,subtitle = "Clock time or minutes after Dhuhr adhan") { on -> update { it.copy(jumuahFixed = on) } }
                if (!s.jumuahFixed) StepperRow("After Dhuhr adhan","${s.jumuahOffset} min",onMinus = { update { it.copy(jumuahOffset = (it.jumuahOffset-1).coerceAtLeast(0)) } },onPlus = { update { it.copy(jumuahOffset = (it.jumuahOffset+1).coerceAtMost(180)) } })
                else ListRow("Jumu'ah at ${Clock12.label(s.jumuahMinute)}",subtitle = "Fixed clock time · AM / PM",onClick = { pick(s.jumuahMinute) { minute -> update { it.copy(jumuahMinute = minute) } } })
                SwitchRow("Jumu'ah notification",s.jumuahNotify,subtitle = "Separate Friday opt-in") { notify(null,it) }
                StepperRow("Remind before Jumu'ah",if (s.jumuahBefore == 0) "At iqamah" else "${s.jumuahBefore} min",onMinus = { update { it.copy(jumuahBefore = (it.jumuahBefore-1).coerceAtLeast(0)) } },onPlus = { update { it.copy(jumuahBefore = (it.jumuahBefore+1).coerceAtMost(180)) } })
            }
        }
        Text("Fixed times before adhan are skipped (Isha may roll to the next day). Notifications use the existing beep channel; Android permission, battery and exact-alarm settings still apply.",Modifier.padding(16.dp),style = MaterialTheme.typography.bodySmall)
    }
}
