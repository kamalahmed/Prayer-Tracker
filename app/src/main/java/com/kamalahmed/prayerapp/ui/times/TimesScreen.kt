package com.kamalahmed.prayerapp.ui.times

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import com.kamalahmed.prayerapp.ui.adaptive.LocalAdaptive
import com.kamalahmed.prayerapp.ui.common.SectionHeader
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.model.NotifyMode
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.theme.Gold
import java.time.format.DateTimeFormatter
import java.util.Locale

fun NotifyMode.icon() = when (this) {
    NotifyMode.OFF -> Icons.Filled.NotificationsOff
    NotifyMode.SILENT -> Icons.Filled.NotificationsNone
    NotifyMode.BEEP -> Icons.Filled.Notifications
    NotifyMode.ADHAN -> Icons.Filled.NotificationsActive
}

@Composable
fun TimesScreen(
    onCalendar: () -> Unit,
    onHolidays: () -> Unit,
    vm: TimesViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    val ctx = LocalContext.current
    val use24 = s.settings.use24h

    val dayPane: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    s.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
                    color = Gold, fontSize = 22.sp, textAlign = TextAlign.Center,
                )
                Text("${s.hijri} · ${s.settings.cityName}", color = Gold, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { vm.shiftDay(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
        }
        CardGroup {
            Prayer.entries.forEachIndexed { i, p ->
                val mode = s.settings.mode(p)
                ListRow(
                    title = p.label(s.settings.abbreviations),
                    leading = mode.icon(),
                    onClick = { vm.cycleMode(p) },
                    showChevron = false,
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(TimeFormat.time(s.day[p], use24), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (s.settings.iqamaEnabled && p.isPrayer) {
                            Text(
                                "Iqama " + TimeFormat.time(s.day[p].plusMinutes(s.settings.iqama(p).toLong()), use24),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                RowDivider()
            }
            ListRow("Midnight", leading = Icons.Filled.NotificationsOff, leadingTint = MaterialTheme.colorScheme.onSurfaceVariant) {
                Text(TimeFormat.time(s.day.midnight, use24), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RowDivider()
            ListRow("Last Third", leading = Icons.Filled.NotificationsOff, leadingTint = MaterialTheme.colorScheme.onSurfaceVariant) {
                Text(TimeFormat.time(s.day.lastThird, use24), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Tap a prayer to cycle its alert: off → silent → beep → adhan",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
    val morePane: @Composable ColumnScope.(Boolean) -> Unit = { wide ->
        if (wide) {
            SectionHeader("Upcoming Islamic holidays")
            CardGroup {
                val today = java.time.LocalDate.now()
                s.holidays.take(6).forEachIndexed { i, h ->
                    if (i > 0) RowDivider()
                    val days = java.time.temporal.ChronoUnit.DAYS.between(today, h.date)
                    ListRow(h.holiday.name, subtitle = h.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))) {
                        Text(if (days == 0L) "Today" else "in $days d", color = Gold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        CardGroup {
            ListRow("Monthly Calendar", leading = Icons.Filled.CalendarMonth, onClick = onCalendar)
            RowDivider()
            ListRow("Islamic Holidays", leading = Icons.Filled.Celebration, onClick = onHolidays)
        }
    }
    if (LocalAdaptive.current.twoPane) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { dayPane(); Spacer(Modifier.height(24.dp)) }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp)) { morePane(true); Spacer(Modifier.height(24.dp)) }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            dayPane()
            morePane(false)
            Spacer(Modifier.height(24.dp))
        }
    }

}

