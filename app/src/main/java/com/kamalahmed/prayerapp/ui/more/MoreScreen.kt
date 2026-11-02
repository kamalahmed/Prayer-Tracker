package com.kamalahmed.prayerapp.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider

@Composable
fun MoreScreen(
    onStreaks: () -> Unit,
    onCalendar: () -> Unit,
    onHolidays: () -> Unit,
    onWidgets: (() -> Unit)?,
    onAbout: (() -> Unit)?,
    banner: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("More", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp))
        banner()
        CardGroup {
            ListRow("Prayer tracker & streaks", leading = Icons.Filled.LocalFireDepartment, onClick = onStreaks)
            RowDivider()
            ListRow("Monthly calendar", leading = Icons.Filled.CalendarMonth, onClick = onCalendar)
            RowDivider()
            ListRow("Islamic holidays", leading = Icons.Filled.Celebration, onClick = onHolidays)
            if (onWidgets != null) { RowDivider(); ListRow("Widget studio", leading = Icons.Filled.Widgets, onClick = onWidgets) }
            if (onAbout != null) { RowDivider(); ListRow("About", leading = Icons.Filled.Info, onClick = onAbout) }
        }
    }
}
