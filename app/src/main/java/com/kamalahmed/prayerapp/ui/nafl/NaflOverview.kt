package com.kamalahmed.prayerapp.ui.nafl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.summary.WorshipSummary
import com.kamalahmed.prayerapp.tracker.Stats
import com.kamalahmed.prayerapp.tracker.StatsPeriod
import com.kamalahmed.prayerapp.ui.streaks.*
import com.kamalahmed.prayerapp.ui.theme.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun countLabel(value: Long): String=NumberFormat.getIntegerInstance().format(value)
fun compactCount(value: Long): String=when {
    value>=1_000_000_000 -> String.format(java.util.Locale.getDefault(),"%.1fB",value/1_000_000_000.0)
    value>=1_000_000 -> String.format(java.util.Locale.getDefault(),"%.1fM",value/1_000_000.0)
    value>=100_000 -> String.format(java.util.Locale.getDefault(),"%.0fK",value/1000.0)
    else -> countLabel(value)
}

@Composable
fun NaflModeSwitch(overview: Boolean,change: (Boolean)->Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp)) {
        listOf("Log","Overview").forEachIndexed { i,label -> SegmentedButton(selected=overview==(i==1),onClick={change(i==1)},shape=SegmentedButtonDefaults.itemShape(i,2)) { Text(label) } }
    }
}

/** A scannable overview, with detailed charts and individual slots only when requested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaflOverview(s: NaflUi,onPeriod: (StatsPeriod)->Unit,onShift: (Long)->Unit,onDay: (LocalDate)->Unit,onSummaries: ()->Unit={},modifier: Modifier=Modifier) {
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var slots by rememberSaveable { mutableStateOf(false) }
    val baseline=NaflDefaults.items.filter { it.baseline }.map { it.id }.toSet()
    val visible=s.report.items.filter { it.item.enabled || it.count>0 || (it.item.id=="quran" && (s.report.pages>0 || s.report.minutes>0)) }
    val activeDates=s.logs.asSequence().filter { it.count>0 || it.pages>0 || it.minutes>0 }.map { it.date }.toSet()
    val active=s.report.daily.count { it.date<=s.today && it.date.toString() in activeDates }
    val dhikr=s.report.items.filter { it.item.isCounter }.sumOf { it.count.toLong() }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Voluntary worship",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("A little space for what matters.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { StatsPeriod.entries.forEachIndexed { i,p ->
            SegmentedButton(selected=s.period==p,onClick={onPeriod(p)},shape=SegmentedButtonDefaults.itemShape(i,3)) { Text(p.label) }
        } }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={onShift(-1)},modifier=Modifier.semantics { contentDescription="Previous Nafl period" }) { Text("‹") }
            Text(s.report.title,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
            TextButton(onClick={onShift(1)},enabled=Stats.range(s.period,s.anchor).second<s.today,modifier=Modifier.semantics { contentDescription="Next Nafl period" }) { Text("›") }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AccentGreen.copy(alpha=0.15f)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("YOUR VOLUNTARY WORSHIP",style=MaterialTheme.typography.labelSmall,color=AccentGreen)
            Text(if(active==0) "Every small beginning counts." else "Small moments. Steady habits.",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                HeroNumber(active.toLong(),"active days",Modifier.weight(1f))
                HeroNumber(s.report.quranDays.toLong(),"Quran days",Modifier.weight(1f))
                HeroNumber(dhikr,"dhikr counts",Modifier.weight(1f))
            }
            TextButton(onClick=onSummaries,contentPadding=PaddingValues(0.dp)) { Text("Daily & weekly summaries  →",color=AccentGreen) }
        }
        val elapsed=s.report.daily.count { it.completed!=null }
        val rakaat=s.report.daily.sumOf { it.rakaat };val possible=elapsed*12
        Panel(modifier=Modifier.clickable { slots=true }) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                ProgressRing(if(possible==0) 0f else rakaat.toFloat()/possible,82.dp,AccentGreen,stroke=8.dp) {
                    Text("$rakaat",fontSize=22.sp,fontWeight=FontWeight.Bold,color=AccentGreen)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("The daily 12 Sunnah",fontWeight=FontWeight.SemiBold)
                    Text("$rakaat / $possible rakaat · tracked days",style=MaterialTheme.typography.bodySmall)
                    Text("Prayer-by-prayer details  →",style=MaterialTheme.typography.labelSmall,color=AccentGreen)
                }
            }
            Spacer(Modifier.height(12.dp))
            val recent=s.report.daily.filter { it.date<=s.today }.takeLast(7)
            if(s.period!=StatsPeriod.WEEK) Text("Last ${recent.size} days in this period",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) { recent.forEach { d ->
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(d.date.dayOfWeek.name.take(1),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(6.dp)).background(if(d.completed==null) CardHigh else AccentGreen.copy(alpha=0.12f+0.75f*d.rakaat/12f)).clickable { onDay(d.date) },contentAlignment=Alignment.Center) {
                        Text(if(d.completed==null) "—" else "${d.rakaat}",style=MaterialTheme.typography.labelSmall,modifier=Modifier.semantics { contentDescription="${d.date}: ${d.rakaat} of 12 Sunnah rakaat. Tap to log" })
                    }
                }
            } }
        }
        OverviewGroup("Prayers","days performed",visible.filter { WorshipSummary.isOtherPrayer(it.item) },1,s,{detailId=it.id})
        OverviewGroup("Quran","reading, not recitations",visible.filter { it.item.id=="quran" },1,s,{detailId=it.id},Gold)
        OverviewGroup("Dhikr counters","recitations",visible.filter { it.item.isCounter },2,s,{detailId=it.id})
        var other by rememberSaveable { mutableStateOf(false) }
        val rest=visible.filter { it.item.id !in baseline && !WorshipSummary.isOtherPrayer(it.item) && !it.item.isCounter && it.item.id!="quran" }
        if(rest.isNotEmpty()) {
            OutlinedButton(onClick={other=!other},modifier=Modifier.fillMaxWidth()) { Text("Other worship · ${rest.size} items  ${if(other) "−" else "+"}") }
            if(other) OverviewGroup("Other worship","logged counts",rest,2,s,{detailId=it.id})
        }
        Text("Tap any item for its trend, history and current streak. Prayer days, rakaat, pages and recitations stay separate. Hidden items with history remain in this report.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
    }
    if(slots) AlertDialog(onDismissRequest={slots=false},title={Text("Daily Sunnah · 12 rakaat")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        s.report.items.filter { it.item.id in baseline }.forEach { row ->
            TextButton(onClick={slots=false;detailId=row.item.id},modifier=Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) {
                Text(row.item.name);Text("${row.days} days · ${row.rate}% of tracked days  →",style=MaterialTheme.typography.bodySmall)
            } }
        }
        Text("The baseline stays 12 rakaat. Optional slots never increase it.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={slots=false}) { Text("Done") }})
    detailId?.let { id -> s.report.items.firstOrNull { it.item.id==id }?.let { row ->
        NaflItemDetail(s,row,{detailId=null})
    } }
}

@Composable
private fun HeroNumber(value: Long,label: String,modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants=true) { contentDescription="${countLabel(value)} $label" }) {
        Text(compactCount(value),fontSize=23.sp,fontWeight=FontWeight.Bold)
        Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverviewGroup(title: String,unit: String,rows: List<NaflItemStats>,initial: Int,s: NaflUi,open: (NaflItem)->Unit,color: Color=AccentGreen) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text("${rows.size} items · $unit",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(rows.isEmpty()) Text("Nothing logged here yet. Enable items in Log → Customize worship.",style=MaterialTheme.typography.bodySmall)
    else Panel {
        (if(expanded) rows else rows.take(initial)).forEachIndexed { index,row ->
            if(index>0) HorizontalDivider(Modifier.padding(vertical=8.dp),color=CardHigh)
            Row(Modifier.fillMaxWidth().clickable { open(row.item) }.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.item.name,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                    Text(if(!row.item.enabled) "Hidden · history kept" else if(row.item.id=="quran") "${row.days} reading days" else "${row.days} active days",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val tracked=s.report.daily.filter { it.completed!=null }.map { it.date }.toSet()
                val series=itemDays(s,row.item).filter { it.first in tracked }
                WorshipSparkline(series.map { it.second.toLong() },color,Modifier.width(56.dp).height(28.dp))
                Column(horizontalAlignment=Alignment.End) {
                    val total=if(row.item.id=="quran") s.report.pages.toLong() else if(WorshipSummary.isOtherPrayer(row.item)) row.days.toLong() else row.count.toLong()
                    Text(compactCount(total),fontSize=21.sp,fontWeight=FontWeight.Bold)
                    Text(if(row.item.id=="quran") "pages" else if(WorshipSummary.isOtherPrayer(row.item)) "days" else if(row.item.isCounter) "counts" else "logged",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(rows.size>initial) TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()) { Text(if(expanded) "Show fewer" else "Show ${rows.size-initial} more ${title.lowercase()}",color=color) }
    }
}

internal fun itemDays(s: NaflUi,item: NaflItem): List<Pair<LocalDate,Int>> {
    val logs=s.logs.filter { it.itemId==item.id }.associateBy { it.date }
    return s.report.daily.map { d -> d.date to if(d.date>s.today) 0 else (logs[d.date.toString()]?.let { if(item.id=="quran") it.pages.coerceIn(0,10_000) else it.count.coerceIn(0,1_000_000) } ?: 0) }
}

@Composable
fun WorshipSparkline(values: List<Long>,color: Color,modifier: Modifier=Modifier) {
    Canvas(modifier) {
        if(values.size<2) { drawLine(color.copy(alpha=0.35f),Offset(0f,size.height/2),Offset(size.width,size.height/2),2.dp.toPx());return@Canvas }
        val max=values.maxOrNull()?.coerceAtLeast(1) ?: 1L
        val p=Path();values.forEachIndexed { i,v -> val x=i*size.width/(values.size-1);val y=size.height-3.dp.toPx()-(v.toFloat()/max)*(size.height-6.dp.toPx());if(i==0) p.moveTo(x,y) else p.lineTo(x,y) }
        drawPath(p,color,style=Stroke(width=1.8.dp.toPx()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NaflItemDetail(s: NaflUi,row: NaflItemStats,close: ()->Unit) {
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().heightIn(max=580.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(row.item.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text(s.report.title,style=MaterialTheme.typography.bodySmall)
            Text(if(row.item.id=="quran") "${countLabel(s.report.pages.toLong())} pages · ${s.report.minutes} minutes" else "${countLabel(row.count.toLong())} ${if(row.item.isCounter) "recitations" else "logged"}",style=MaterialTheme.typography.titleLarge,color=if(row.item.id=="quran") Gold else AccentGreen)
            Text("${row.days} active days · ${row.rate}% of tracked days",style=MaterialTheme.typography.bodyMedium)
            val days=itemDays(s,row.item)
            val bars=if(s.period==StatsPeriod.YEAR) days.groupBy { it.first.month }.map { (m,v) -> BarSlot(m.name.take(1),listOf(v.sumOf { it.second } to AccentGreen),v.any { day -> s.report.daily.any { it.date==day.first && it.completed!=null } },"${m.name}: ${v.sumOf { it.second }}") }
                else days.map { (date,n) -> BarSlot(if(s.period==StatsPeriod.WEEK) date.dayOfWeek.name.take(2) else "${date.dayOfMonth}",listOf(n to AccentGreen),s.report.daily.first { it.date==date }.completed!=null,"$date: $n") }
            StackedBarChart(bars)
            Text("Current streak (now): ${row.streak} days",style=MaterialTheme.typography.titleMedium)
            if(row.item.isCounter) Text(if(row.item.dailyTarget>0) "Personal daily target: ${row.item.dailyTarget}. Totals keep growing beyond it." else "Free counting · no daily target",style=MaterialTheme.typography.bodySmall)
            var history by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick={history=!history}) { Text(if(history) "Hide daily history" else "Show daily history") }
            if(history) days.filter { it.first<=s.today }.asReversed().forEach { (date,n) -> Row(Modifier.fillMaxWidth()) { Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy")),Modifier.weight(1f));Text(countLabel(n.toLong())) } }
            TextButton(onClick=close) { Text("Done") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
