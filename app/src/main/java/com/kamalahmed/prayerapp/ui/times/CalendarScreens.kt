package com.kamalahmed.prayerapp.ui.times

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.hijri.HijriCalendar
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SubScreen
import com.kamalahmed.prayerapp.ui.theme.CardHigh
import com.kamalahmed.prayerapp.ui.theme.Gold
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun MonthlyCalendarScreen(onBack: () -> Unit, vm: TimesViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val s = st ?: return
    val today = LocalDate.now()
    SubScreen("Monthly Calendar", onBack) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.month.value = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                color = Gold, fontSize = 20.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            IconButton(onClick = { vm.month.value = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
        }
        val cols = listOf("Day") + Prayer.entries.map { it.abbr }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            cols.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }
        }
        LazyColumn {
            items((1..month.lengthOfMonth()).toList()) { d ->
                val date = month.atDay(d)
                val day = s.schedule.day(date)
                val h = HijriCalendar.of(date, s.settings.hijriAdjust)
                val isToday = date == today
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (isToday) Modifier.background(CardHigh) else Modifier)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$d", fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                        Text("${h.day}/${h.month}", style = MaterialTheme.typography.labelMedium, color = Gold)
                    }
                    Prayer.entries.forEach { p ->
                        Text(
                            TimeFormat.time(day[p], s.settings.use24h, showAmPm = false),
                            Modifier.weight(1f), fontSize = 13.sp, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HolidaysScreen(onBack: () -> Unit, vm: TimesViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val today = LocalDate.now()
    SubScreen("Islamic Holidays", onBack) {
        Text(
            "Dates use the Umm al-Qura calendar${if (s.settings.hijriAdjust != 0) " adjusted by ${s.settings.hijriAdjust} day(s)" else ""}; local moon sighting may differ by a day.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item {
                CardGroup {
                    s.holidays.forEachIndexed { i, h ->
                        val days = ChronoUnit.DAYS.between(today, h.date)
                        ListRow(
                            h.holiday.name,
                            subtitle = "${h.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))} · ${h.hijri.day} ${h.hijri.monthName} ${h.hijri.year}",
                        ) {
                            Text(if (days == 0L) "Today" else "in $days d", color = Gold)
                        }
                        if (i < s.holidays.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}
