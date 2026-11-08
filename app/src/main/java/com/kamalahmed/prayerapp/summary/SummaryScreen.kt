package com.kamalahmed.prayerapp.summary

import android.Manifest
import android.app.TimePickerDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.safety.*
import com.kamalahmed.prayerapp.nafl.Clock12
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.ui.nafl.countLabel
import com.kamalahmed.prayerapp.ui.streaks.*
import com.kamalahmed.prayerapp.ui.theme.*
import java.time.format.DateTimeFormatter
import java.time.ZoneId

@Composable
fun SummaryScreen(onBack: ()->Unit,initial: SummaryRequest?=null,vm: SummaryViewModel=hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    LaunchedEffect(initial) { initial?.let(vm::open) }
    if(ui==null) Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(error ?: "Gathering your moments…")
        if(error!=null) Button(onClick=vm::retry) { Text("Retry") }
        TextButton(onClick=onBack) { Text("Back") }
    } else SummaryContent(ui!!,vm::kind,vm::shift,vm::today,vm::notify,vm::time,onBack)
}

/** Daily and weekly reflections retain units and expose details only on request. */
@Composable
fun SummaryContent(ui: SummaryUi,onKind: (SummaryKind)->Unit={},onShift: (Long)->Unit={},onToday: ()->Unit={},onNotify: (SummaryKind,Boolean)->Unit={_,_->},onTime: (SummaryKind,Int)->Unit={_,_->},onBack: ()->Unit={}) {
    val r=ui.report;val weekly=ui.kind==SummaryKind.WEEK;val previous=ui.previous
    val soFar=r.end>=ui.today
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Streaks / Your summaries",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=AccentGreen)
            TextButton(onClick=onBack) { Text("Back") }
        }
        Text(if(weekly) "Your week" else "Your day",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Text(if(weekly) "A little perspective on your progress." else "A reflection, not a score.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf("Daily","Weekly").forEachIndexed { i,label ->
            SegmentedButton(selected=weekly==(i==1),onClick={onKind(if(i==0) SummaryKind.DAY else SummaryKind.WEEK)},shape=SegmentedButtonDefaults.itemShape(i,2)) { Text(label) }
        } }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={onShift(-1)},modifier=Modifier.semantics { contentDescription="Previous summary period" }) { Text("‹") }
            Column(Modifier.weight(1f)) {
                Text(if(weekly) "${r.start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${r.end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}" else r.start.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")),style=MaterialTheme.typography.titleSmall)
                Text(if(soFar) "In progress · so far" else "Completed period · recorded history",style=MaterialTheme.typography.labelSmall,color=if(soFar) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick={onShift(1)},enabled=r.end<ui.today,modifier=Modifier.semantics { contentDescription="Next summary period" }) { Text("›") }
        }
        if(r.start!=ui.today && (!weekly || r.end<ui.today)) TextButton(onClick=onToday) { Text(if(weekly) "This week so far" else "Back to today") }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AccentGreen.copy(alpha=0.15f)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(weekly) "YOUR WEEK IN FARD PRAYERS" else "FARD PRAYERS",style=MaterialTheme.typography.labelSmall,color=AccentGreen)
            if(weekly) Text(if(r.fard.prayed>0) "You kept showing up." else "A week to reflect on.",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${r.fard.prayed}",fontSize=46.sp,fontWeight=FontWeight.Bold)
                Text("/ ${r.fard.due}",fontSize=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=6.dp))
            }
            Text(if(r.fard.due==0) "No tracked/due prayers in this period yet" else "prayers recorded · tracked / due opportunities",style=MaterialTheme.typography.bodySmall)
            if(previous!=null && previous.fard.due>0) {
                val equalCoverage=r.fard.due==previous.fard.due
                Text(if(equalCoverage) delta(r.fard.prayed.toLong(),previous.fard.prayed.toLong(),"fard prayers",weekly) else "Previous period: ${previous.fard.prayed} / ${previous.fard.due} · tracking coverage differs",style=MaterialTheme.typography.labelSmall,color=AccentGreen)
            }
            if(weekly) StackedBarChart(r.days.map { d -> BarSlot(d.date.dayOfWeek.name.take(1),listOf(d.fard.onTime to AccentGreen,d.fard.late to Gold),d.fard.due>0,"${d.date}: ${d.fard.prayed} fard prayed") },yMax=5)
            else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) { Prayer.obligatory.forEach { p ->
                val status=ui.statuses[p];val prayed=status==PrayerStatus.ON_TIME || status==PrayerStatus.LATE
                val color=when(status) { PrayerStatus.ON_TIME->AccentGreen;PrayerStatus.LATE->Gold;PrayerStatus.MISSED->Danger;else->MaterialTheme.colorScheme.onSurfaceVariant }
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha=0.16f)).semantics { contentDescription="${p.displayName}: ${when(status) { PrayerStatus.ON_TIME->"prayed on time";PrayerStatus.LATE->"prayed later";PrayerStatus.MISSED->"marked missed";else->"not recorded" }}" },contentAlignment=Alignment.Center) { Text(if(prayed) "✓" else if(status==PrayerStatus.MISSED) "×" else "—",color=color) }
                    Text(p.displayName,style=MaterialTheme.typography.labelSmall)
                }
            } }
            Text("${r.fard.onTime} on time · ${r.fard.late} prayed later",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SummaryMetric("SUNNAH",countLabel(r.sunnah.toLong()),"rakaat · ${r.days.size*12} daily opportunities",Modifier.weight(1f),AccentGreen,
                previous?.let { delta(r.sunnah.toLong(),it.sunnah.toLong(),"rakaat",weekly) })
            SummaryMetric("OTHER NAFL",countLabel(r.otherPrayers),"prayer sessions",Modifier.weight(1f),Gold)
        }
        if(weekly) r.bestDay?.let { best ->
            Panel("A day to remember", "Most fard recorded; earliest day if tied") {
                Text(best.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("${best.fard.prayed} fard · ${best.sunnah} Sunnah rakaat · ${countLabel(best.dhikr)} dhikr",style=MaterialTheme.typography.bodySmall,color=Gold)
            }
        }
        Panel("Time with the Quran") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(weekly) "${r.quranDays} reading days" else if(r.quranDays>0) "Reading completed" else "No completed reading mark",style=MaterialTheme.typography.bodyMedium)
                    Text("${countLabel(r.quranMinutes)} minutes recorded",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${countLabel(r.quranPages)} pages",fontSize=24.sp,color=Gold)
            }
            previous?.let { Text(delta(r.quranPages,it.quranPages,"pages",weekly),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Panel("Dhikr & tasbih") {
            Text(countLabel(r.dhikr),fontSize=34.sp,color=AccentGreen,fontWeight=FontWeight.Bold)
            Text("recitations across ${r.counters.count { it.count>0 }} active counters",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            previous?.let { p ->
                Text(delta(r.dhikr,p.dhikr,"recitations",weekly),style=MaterialTheme.typography.labelSmall,color=AccentGreen)
                if(p.dhikr>0) { Text("${Math.round((r.dhikr-p.dhikr)*100.0/p.dhikr)}% change in recorded counts",style=MaterialTheme.typography.labelSmall);LinearProgressIndicator(progress={if(maxOf(r.dhikr,p.dhikr)==0L) 0f else r.dhikr.toFloat()/maxOf(r.dhikr,p.dhikr)},modifier=Modifier.fillMaxWidth().padding(top=8.dp),color=AccentGreen) }
            }
            var counters by rememberSaveable(r.start.toString()) { mutableStateOf(false) }
            TextButton(onClick={counters=!counters},contentPadding=PaddingValues(vertical=8.dp)) { Text(if(counters) "Hide counter totals  −" else "View all counter totals  +") }
            if(counters) r.counters.filter { it.item.enabled || it.count>0 }.forEach { c -> Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(c.item.name+if(!c.item.enabled) " (hidden)" else "",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                Text(countLabel(c.count),color=AccentGreen,fontWeight=FontWeight.SemiBold)
            } }
        }
        Panel("Qada recovered") {
            Text("${countLabel(r.qada)} make-up prayers",style=MaterialTheme.typography.titleLarge,color=Gold)
            Text("Recovery is counted separately from this period's fard prayers.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            previous?.let { Text(delta(r.qada,it.qada,"recovered",weekly),style=MaterialTheme.typography.labelSmall) }
        }
        if(r.otherWorship>0) Panel("Other worship") { Text("${countLabel(r.otherWorship)} logged · Ruqyah / custom checkbox items",style=MaterialTheme.typography.bodyMedium) }
        if(weekly) Panel("Your consistent moments") {
            Text("Longest 5-fard run: ${r.fardRun} days",style=MaterialTheme.typography.bodyMedium)
            Text("Longest Quran reading run: ${r.quranRun} days",style=MaterialTheme.typography.bodyMedium)
            Text("Within this selected week, not lifetime streaks.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SummaryNotificationCard(ui,onNotify,onTime)
        Text("Only saved worship is included. No mixed worship score, and no pressure to compare different acts. Future days are excluded; untracked fard days are not called missed. Dates are kept as logged; reports and reminders use ${ZoneId.systemDefault().id}. Fard due times follow your calculation location.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

private fun delta(current: Long,previous: Long,unit: String,weekly: Boolean): String {
    val diff=current-previous
    return "${if(diff>0) "+" else ""}${countLabel(diff)} $unit vs recorded previous ${if(weekly) "week" else "day"}"
}

@Composable
private fun SummaryMetric(label: String,value: String,unit: String,modifier: Modifier,color: Color,comparison: String?=null) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Card).padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,fontSize=30.sp,color=color,fontWeight=FontWeight.Bold)
        Text(unit,style=MaterialTheme.typography.bodySmall)
        comparison?.let { Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SummaryNotificationCard(ui: SummaryUi,onNotify: (SummaryKind,Boolean)->Unit,onTime: (SummaryKind,Int)->Unit) {
    val ctx=LocalContext.current;val kind=ui.kind;val weekly=kind==SummaryKind.WEEK
    var pending by remember { mutableStateOf<SummaryKind?>(null) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { onNotify(it,granted) };pending=null
        if(!granted) AppErrors.report("Notifications are blocked. Summary notifications remain off.")
    }
    val enabled=if(weekly) ui.settings.summaryWeekly else ui.settings.summaryDaily
    val minute=if(weekly) ui.settings.summaryWeeklyMinute else ui.settings.summaryDailyMinute
    Panel {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(weekly) "Weekly summary notification" else "Daily summary notification",fontWeight=FontWeight.SemiBold)
                Text("Optional · off by default",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked=enabled,onCheckedChange={ value ->
                guard("Summaries","notification permission") {
                    if(value) SummaryNotifier.createChannel(ctx)
                    if(value && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) { pending=kind;permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    else onNotify(kind,value)
                }
            },modifier=Modifier.semantics { contentDescription=if(weekly) "Enable weekly summary notification" else "Enable daily summary notification" })
        }
        TextButton(onClick={ guard("Summaries","12-hour time picker") { TimePickerDialog(ctx,{_,h,m ->onTime(kind,h*60+m)},minute/60,minute%60,false).show() } },contentPadding=PaddingValues(vertical=6.dp)) { Text("${if(weekly) "Sunday · " else "Daily · "}${Clock12.label(minute)}  · Change time") }
        Text("A snapshot of your day / week so far. Android may delay delivery; weekly reports can catch up within 24 hours after Sunday’s time. Checks run about every 15 minutes and on app resume. No internet is needed.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val blocked=!NotificationManagerCompat.from(ctx).areNotificationsEnabled() || runCatching { ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(SummaryNotifier.CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE }.getOrDefault(false)
        if(blocked) {
            Text("Android notifications or this channel are blocked.",style=MaterialTheme.typography.bodySmall,color=Gold)
            TextButton(onClick={guard("Summaries","notification settings") { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName)) }}) { Text("Open notification settings") }
        }
    }
}
