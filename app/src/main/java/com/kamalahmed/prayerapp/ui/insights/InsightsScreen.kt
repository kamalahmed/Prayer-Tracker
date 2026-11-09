package com.kamalahmed.prayerapp.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.insights.ChangeRow
import com.kamalahmed.prayerapp.insights.Coach
import com.kamalahmed.prayerapp.insights.CoachAction
import com.kamalahmed.prayerapp.insights.GlanceCheck
import com.kamalahmed.prayerapp.insights.Health
import com.kamalahmed.prayerapp.insights.InsightsEngine
import com.kamalahmed.prayerapp.insights.InsightsReport
import com.kamalahmed.prayerapp.insights.InsightsViewModel
import com.kamalahmed.prayerapp.insights.Scorecard
import com.kamalahmed.prayerapp.insights.TrendPoint
import com.kamalahmed.prayerapp.qada.QadaPrayer
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import com.kamalahmed.prayerapp.ui.qada.QCard
import com.kamalahmed.prayerapp.ui.qada.SectionTitle
import com.kamalahmed.prayerapp.ui.qada.prayerColors
import com.kamalahmed.prayerapp.ui.streaks.ProgressRing
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.AccentGreenDim
import com.kamalahmed.prayerapp.ui.theme.Card
import com.kamalahmed.prayerapp.ui.theme.CardHigh
import com.kamalahmed.prayerapp.ui.theme.Gold
import com.kamalahmed.prayerapp.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OnGreen = Color(0xFF04160A)
private fun color(p: Prayer) = QadaPrayer.of(p.name)?.let { prayerColors[it] } ?: AccentGreen

/** Insights sub-tab: at a glance → coach → fard → what changed → trends → scorecards. Everything is computed on the phone. */
@Composable
fun InsightsScreen(onAction: (CoachAction, Prayer?) -> Unit, vm: InsightsViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val r = s.report
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (r == null) {
            QCard { Text(s.error ?: "Could not build insights.", color = Gold) }
            return@Column
        }
        RangeLine(r)
        val blocks = mutableListOf<@Composable () -> Unit>()
        blocks += { Glance(r) }
        blocks += {
            if (s.coachHidden) Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Coach hidden", color = TextSecondary, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.hideCoach(false) }) { Text("Show coach") }
            } else CoachCard(r.coach, onAction, onNotHelpful = { vm.notHelpful(r.coach.suggestions.map { it.id }) }, onHide = { vm.hideCoach(true) })
        }
        if (r.fardWeek.due > 0 || r.fardPrev.due > 0) blocks += { Fard(r) } else blocks += {
            SectionTitle(3, "Fard prayers", "5 a day")
            QCard { Text("Mark your five daily prayers (Stats tab, Prayers tab, a widget or the notification) and this fills in after a day or two.", color = TextSecondary) }
        }
        blocks += { Changes(r) }
        blocks += { Trends(r) }
        blocks += { Scorecards(r) }
        val cols = LocalAdaptive.current.dashboardColumns
        if (cols <= 1) blocks.forEach { it() } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { blocks[0](); blocks[1](); blocks[2]() }
            Column(Modifier.weight(1f)) { blocks[3](); blocks[4](); blocks[5]() }
        }
        Text("Private: built on this phone from your own logs. No internet, no AI service, nothing leaves the device.",
            fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp))
        Spacer(Modifier.height(24.dp))
    }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private fun range(a: LocalDate, b: LocalDate) = if (a.month == b.month) "${a.dayOfMonth}–${b.format(shortDate)}" else "${a.format(shortDate)}–${b.format(shortDate)}"

@Composable
private fun RangeLine(r: InsightsReport) {
    Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Last 7 days · ${range(r.weekStart, r.weekEnd)}", fontSize = 12.5.sp)
        Text("  vs ${range(r.prevStart, r.prevEnd)}", fontSize = 12.5.sp, color = TextSecondary)
    }
}

@Composable
fun Delta(value: Int?, unit: String = "", steady: Boolean = false, big: Boolean = false) {
    val size = if (big) 20.sp else 13.sp
    val icon = if (big) 18.dp else 13.dp
    when {
        value == null -> Text("—", color = TextSecondary, fontSize = size)
        steady || value == 0 -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = if (value == 0) "no change" else "steady" }) {
            Icon(Icons.Filled.Remove, null, Modifier.size(icon), tint = TextSecondary)
            Text(if (value == 0) "0" else "steady", color = TextSecondary, fontSize = size, fontWeight = FontWeight.Bold)
        }
        else -> {
            val up = value > 0
            val c = if (up) AccentGreen else Gold
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "${if (up) "up" else "down"} ${kotlin.math.abs(value)} $unit" }) {
                Icon(if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, Modifier.size(icon), tint = c)
                Text("${if (up) "+" else "−"}${InsightsEngine.fmt(kotlin.math.abs(value))}${if (unit.isNotEmpty()) " $unit" else ""}", color = c, fontSize = size, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HealthBadge(h: Health) {
    val (text, c, icon) = when (h) {
        Health.GOOD -> Triple("Good", AccentGreen, Icons.Filled.Check)
        Health.ATTENTION -> Triple("Needs attention", Gold, Icons.Filled.Info)
        Health.NO_DATA -> Triple("Not tracked yet", TextSecondary, Icons.Filled.Remove)
    }
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = c); Spacer(Modifier.width(4.dp))
        Text(text, color = c, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Glance(r: InsightsReport) {
    SectionTitle(1, "At a glance", "this week")
    r.glance.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { g -> GlanceTile(g, Modifier.weight(1f)) }
        }
    }
    val good = r.glance.count { it.health == Health.GOOD }; val attention = r.glance.count { it.health == Health.ATTENTION }
    if (good + attention > 0) Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = AccentGreen); Spacer(Modifier.width(6.dp))
        Text("$good good · $attention ${if (attention == 1) "needs" else "need"} attention." +
            if (r.fardWeek.due > 0) " ${r.fardWeek.prayed} of ${r.fardWeek.due} fard prayed." else "", fontSize = 12.5.sp, color = Color(0xFFCFCFD3))
    }
}

@Composable
private fun GlanceTile(g: GlanceCheck, modifier: Modifier) {
    val border = when (g.health) { Health.ATTENTION -> Gold.copy(alpha = 0.35f); Health.GOOD -> AccentGreen.copy(alpha = 0.2f); else -> Color.Transparent }
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(if (g.health == Health.ATTENTION) Brush.verticalGradient(listOf(Gold.copy(alpha = 0.08f), Card)) else Brush.verticalGradient(listOf(Card, Card)))
        .border(1.dp, border, RoundedCornerShape(18.dp)).padding(12.dp)) {
        Text(g.title, fontSize = 12.sp, color = Color(0xFFCFCFD3))
        Text(g.value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${g.detail} · ", fontSize = 11.5.sp, color = TextSecondary)
            Delta(g.delta, steady = g.steady)
        }
        Spacer(Modifier.height(8.dp))
        HealthBadge(g.health)
    }
}

@Composable
private fun CoachCard(c: Coach, onAction: (CoachAction, Prayer?) -> Unit, onNotHelpful: () -> Unit, onHide: () -> Unit) {
    var why by rememberSaveable { mutableStateOf(false) }
    var thanks by rememberSaveable { mutableStateOf(false) }
    SectionTitle(2, "Your coach", "made on this phone")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF173320), Card, Card)))
        .border(1.dp, AccentGreen.copy(alpha = 0.2f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(AccentGreenDim), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(20.dp), tint = AccentGreen)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Assalamu alaikum", fontWeight = FontWeight.Bold)
                Text("Weekly reflection", fontSize = 12.sp, color = TextSecondary)
            }
        }
        Text(c.praise, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 12.dp))
        if (c.suggestions.isNotEmpty()) {
            Text("THIS WEEK, TRY", fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            c.suggestions.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Gold), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color(0xFF1A1205), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, fontWeight = FontWeight.Bold)
                        Text(s.body, fontSize = 12.5.sp, color = Color(0xFFC9C9CE), modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                        if (s.action != null && s.actionLabel != null) {
                            if (i == 0) Button(onClick = { onAction(s.action, s.prayer) }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = OnGreen)) { Text(s.actionLabel, fontSize = 12.5.sp) }
                            else OutlinedButton(onClick = { onAction(s.action, s.prayer) }) { Text(s.actionLabel, fontSize = 12.5.sp, color = AccentGreen) }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)).background(Gold.copy(alpha = 0.06f))
            .padding(start = 0.dp)) {
            Row {
                Box(Modifier.width(3.dp).height(58.dp).background(Gold))
                Column(Modifier.padding(10.dp)) {
                    Text("“${c.quote}”", fontStyle = FontStyle.Italic, fontSize = 12.5.sp, color = Color(0xFFE9E3D6))
                    Text("— ${c.quoteSource}", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { why = !why }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = AccentGreen); Spacer(Modifier.width(6.dp))
            Text("Private and free: how this is made", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowRight, null, Modifier.rotate(if (why) 90f else 0f), tint = TextSecondary)
        }
        if (why) Column(Modifier.padding(top = 6.dp)) {
            Text("Built on your phone by fixed rules over your own logs. No AI service, no internet, no account, no cost. Nothing leaves the device.",
                fontSize = 12.sp, color = Color(0xFFC9C9CE))
            listOf(
                "A prayer's on-time days drop by 2+ vs last week, or stay under 60% → suggest an alarm before its iqamah",
                "Quran on fewer than 5 days → pair one page with your most consistent prayer",
                "Sunnah below 80% of last week → return to the Sunnah you missed most",
                "A missed fard → suggest logging it as qada",
                "A category is its best in 5 weeks → praise it first",
            ).forEach { rule ->
                Text("IF $rule", fontSize = 10.5.sp, color = Color(0xFFBFE7C6), modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xFF0E0E10)).padding(6.dp))
            }
            Text("At most 3 suggestions; praise always comes first. It never compares you with other people or makes claims about reward.",
                fontSize = 12.sp, color = Color(0xFFC9C9CE), modifier = Modifier.padding(top = 6.dp))
        }
        if (thanks) Text("Thanks — those tips are hidden for 4 weeks.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (c.suggestions.isNotEmpty()) OutlinedButton(onClick = { thanks = true; onNotHelpful() }) { Text("Not helpful", fontSize = 12.sp) }
            OutlinedButton(onClick = onHide) { Text("Hide coach", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun Fard(r: InsightsReport) {
    var per by rememberSaveable { mutableStateOf(false) }
    SectionTitle(3, "Fard prayers", "5 a day")
    SingleChoiceSegmentedButtonRow(Modifier.padding(bottom = 10.dp)) {
        listOf("Combined", "Per prayer").forEachIndexed { i, l ->
            SegmentedButton(selected = per == (i == 1), onClick = { per = i == 1 }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
        }
    }
    if (!per) QCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${r.fardWeek.onTime}", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                    Text("/${r.fardWeek.due}", fontSize = 20.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("on time · ${r.fardWeek.prayed}/${r.fardWeek.due} prayed", color = TextSecondary)
            }
            val p = if (r.fardWeek.due == 0) 0f else r.fardWeek.onTime.toFloat() / r.fardWeek.due
            ProgressRing(p, 84.dp, AccentGreen, stroke = 8.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${InsightsEngine.pct(r.fardWeek.onTime, r.fardWeek.due)}%", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("on time", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VsTile("vs yesterday", if (r.fardToday.due == 0) null else r.fardToday.onTime - r.fardYesterdaySameTime.onTime,
                if (r.fardToday.due == 0) "after Fajr" else "today so far: ${r.fardToday.onTime} vs ${r.fardYesterdaySameTime.onTime}", Modifier.weight(1f))
            VsTile("vs last week", if (r.fardPrev.due == 0) null else r.fardWeek.onTime - r.fardPrev.onTime, "${r.fardPrev.onTime} → ${r.fardWeek.onTime} on time", Modifier.weight(1f))
        }
        Text("ON TIME PER DAY (OF 5)", fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        val min = r.dailyOnTime.minOfOrNull { it.value }
        Bars(r.dailyOnTime, 5, r.dailyOnTime.indexOfFirst { it.value == min })
        r.dailyOnTime.minByOrNull { it.value }?.let { p ->
            if (r.fardWeek.due > 0) Text("${p.label} was the hardest day (${p.value} on time).", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp).fillMaxWidth())
        }
    } else QCard {
        r.perPrayer.forEach { pi ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(9.dp).height(30.dp).clip(RoundedCornerShape(5.dp)).background(color(pi.prayer)))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(pi.prayer.displayName, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text("${pi.week.prayed}/${pi.week.due} prayed", fontSize = 11.5.sp, color = TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.semantics { contentDescription = "${pi.week.onTime} of 7 days on time" }) {
                    pi.onTimeByDay.forEach { d -> Box(Modifier.size(9.dp).clip(CircleShape).background(if (d == true) AccentGreen else Color(0xFF3A3A3E))) }
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 60.dp)) {
                    Text("${pi.week.onTime}/${pi.week.due}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Delta(if (pi.prev.due == 0) null else pi.onTimeDelta)
                }
            }
        }
        Text("Dots = on time, oldest → newest. Arrows compare on-time days with last week.", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        r.strongest?.let { s ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, AccentGreen.copy(alpha = 0.2f), RoundedCornerShape(20.dp)).padding(14.dp)) {
                Text("STRONGEST", fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                Text(s.prayer.displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color(s.prayer))
                Text("${s.week.onTime}/${s.week.due} on time")
                Row(verticalAlignment = Alignment.CenterVertically) { Delta(if (s.prev.due == 0) null else s.onTimeDelta, "days"); Text(" vs last week", fontSize = 11.sp, color = TextSecondary) }
            }
        }
        r.weakest?.let { w ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(14.dp)) {
                Text("NEEDS CARE", fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, color = Gold)
                Text(w.prayer.displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color(w.prayer))
                Text("${w.week.onTime}/${w.week.due} on time")
                Row(verticalAlignment = Alignment.CenterVertically) { Delta(if (w.prev.due == 0) null else w.onTimeDelta, "days"); Text(" vs last week", fontSize = 11.sp, color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun VsTile(label: String, delta: Int?, note: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(CardHigh).padding(horizontal = 11.dp, vertical = 9.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Delta(delta, big = true)
        Text(note, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun Bars(points: List<TrendPoint>, max: Int, highlight: Int, height: Int = 118) {
    val top = maxOf(max, points.maxOfOrNull { it.value } ?: 0, 1)
    Row(Modifier.fillMaxWidth().height(height.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        points.forEachIndexed { i, p ->
            Column(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "${p.label}: ${p.value}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(InsightsEngine.fmt(p.value), fontSize = 10.5.sp, color = Color(0xFFCFCFD3))
                Box(Modifier.weight(1f).widthIn(max = 30.dp).fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(CardHigh), contentAlignment = Alignment.BottomCenter) {
                    val f = p.value.toFloat() / top
                    if (f > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(f).clip(RoundedCornerShape(7.dp)).background(if (i == highlight) Gold else AccentGreen))
                }
                Text(p.label, fontSize = 10.5.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Changes(r: InsightsReport) {
    SectionTitle(4, "What changed", "▲ more · ▼ less")
    QCard {
        Row(Modifier.padding(bottom = 6.dp)) {
            Spacer(Modifier.weight(1.1f))
            Text("vs yesterday", Modifier.weight(1f), fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            Text("vs last week", Modifier.weight(1f), fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
        }
        r.changes.forEach { c -> ChangeLine(c) }
        Text("“Yesterday” compares today up to now with yesterday up to the same time, so a half-finished day never shows as a drop. Dhikr and Quran have no time of day, so they compare after Isha.",
            fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ChangeLine(c: ChangeRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(c.label, Modifier.weight(1.1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.weight(1f)) {
            Text(c.todayText, fontSize = 11.sp, color = TextSecondary)
            if (c.vsYesterday == null && c.yesterdayNote != null) Text(c.yesterdayNote, fontSize = 11.sp, color = TextSecondary) else Delta(c.vsYesterday)
        }
        Column(Modifier.weight(1f)) {
            Text(c.weekText, fontSize = 11.sp, color = TextSecondary)
            Delta(c.vsWeek, steady = c.steady)
        }
    }
}

@Composable
private fun Trends(r: InsightsReport) {
    var month by rememberSaveable { mutableStateOf(false) }
    SectionTitle(5, "Trends", "")
    SingleChoiceSegmentedButtonRow(Modifier.padding(bottom = 10.dp)) {
        listOf("Week", "Month").forEachIndexed { i, l ->
            SegmentedButton(selected = month == (i == 1), onClick = { month = i == 1 }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
        }
    }
    QCard {
        if (!month) {
            TrendTitle("FARD ON TIME · LAST 7 DAYS")
            Bars(r.dailyOnTime, 5, r.dailyOnTime.indexOfFirst { p -> p.value == r.dailyOnTime.minOfOrNull { it.value } })
            TrendTitle("QURAN PAGES · LAST 7 DAYS")
            Bars(r.dailyQuranPages, 1, -1)
            val noRead = InsightsEngine.longestGap(r.dailyQuranPages.map { it.value > 0 })
            Note(buildString {
                val lo = r.dailyOnTime.minOfOrNull { it.value } ?: 0; val hi = r.dailyOnTime.maxOfOrNull { it.value } ?: 0
                append("Fard stayed at $lo–$hi on time each day.")
                if (noRead.second >= 2) append(" ${r.dailyQuranPages[noRead.first].label}–${r.dailyQuranPages[noRead.first + noRead.second - 1].label} had no Quran reading.")
            })
        } else {
            TrendTitle("FARD ON TIME · LAST 5 WEEKS (OF 35)")
            Bars(r.weeklyOnTime, 35, r.weeklyOnTime.lastIndex)
            TrendTitle("SUNNAH RAKAAT · LAST 5 WEEKS" + if (r.sunnahPossiblePerWeek > 0) " (OF ${r.sunnahPossiblePerWeek})" else "")
            Bars(r.weeklySunnah, r.sunnahPossiblePerWeek, r.weeklySunnah.lastIndex)
            val on = r.weeklyOnTime.map { it.value }; val sun = r.weeklySunnah.map { it.value }
            Note(buildString {
                append("On-time fard ranged ${on.minOrNull() ?: 0}–${on.maxOrNull() ?: 0} over 5 weeks.")
                val falling = sun.zipWithNext().takeLastWhile { (a, b) -> b < a }.size
                if (falling >= 2) append(" Sunnah has slipped $falling weeks in a row.") else if (sun.zipWithNext().takeLastWhile { (a, b) -> b > a }.size >= 2) append(" Sunnah has grown for weeks in a row.")
            })
        }
    }
}

@Composable private fun TrendTitle(t: String) = Text(t, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
@Composable private fun Note(t: String) = Text(t, fontSize = 12.5.sp, color = Color(0xFFD6D6DA), modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardHigh).padding(10.dp))

@Composable
private fun Scorecards(r: InsightsReport) {
    SectionTitle(6, "Scorecards", "this week vs last")
    r.scorecards.forEach { c -> ScoreCard(c) }
    Text("“Needs attention” only when a number falls 20% or more below your 4-week average, or a practice is skipped 3+ days in a row. There is no overall score and no mixing of different kinds of worship.",
        fontSize = 11.5.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
}

@Composable
private fun ScoreCard(c: Scorecard) {
    val accent = if (c.health == Health.ATTENTION) Gold else AccentGreen
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).background(Card)
        .then(if (c.health == Health.ATTENTION) Modifier.border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(20.dp)) else Modifier).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            HealthBadge(c.health)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(c.value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(c.unit, fontSize = 11.sp, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Sparkline(c.trend, accent)
                Delta(c.delta, c.deltaUnit, c.steady)
            }
        }
        Text(c.note, fontSize = 12.sp, color = Color(0xFFC9C9CE), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Sparkline(values: List<Int>, c: Color) {
    Canvas(Modifier.width(70.dp).height(26.dp).semantics { contentDescription = "5-week trend" }) {
        if (values.size < 2) return@Canvas
        val mx = values.max(); val mn = values.min(); val span = (mx - mn).takeIf { it > 0 } ?: 1
        val pts = values.mapIndexed { i, v -> Offset(i * size.width / (values.size - 1), size.height - 3f - (v - mn).toFloat() / span * (size.height - 6f)) }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, c, style = Stroke(width = 2.dp.toPx()))
        drawCircle(c, 2.6.dp.toPx(), pts.last())
    }
}
