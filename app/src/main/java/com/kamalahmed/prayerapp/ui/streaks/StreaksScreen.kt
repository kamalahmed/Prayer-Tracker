package com.kamalahmed.prayerapp.ui.streaks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.tracker.PeriodStats
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.ui.common.SubScreen
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.Card
import com.kamalahmed.prayerapp.ui.theme.CardHigh
import com.kamalahmed.prayerapp.ui.theme.Danger
import com.kamalahmed.prayerapp.ui.theme.Gold

fun PrayerStatus?.color(): Color = when (this) {
    PrayerStatus.ON_TIME -> AccentGreen
    PrayerStatus.LATE -> Gold
    PrayerStatus.MISSED -> Danger
    null -> CardHigh
}

fun PrayerStatus?.label(): String = when (this) {
    PrayerStatus.ON_TIME -> "prayed on time"
    PrayerStatus.LATE -> "prayed late"
    PrayerStatus.MISSED -> "missed"
    null -> "not marked"
}

/** Five tap-to-mark circles for today (used on the Prayers screen and Streaks screen). */
@Composable
fun TodayTracker(vm: StreaksViewModel = hiltViewModel(), abbreviated: Boolean = true) {
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Prayer.obligatory.forEach { p ->
                val status = s.todayStatus[p]
                val enabled = p in s.passed || status != null
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (status != null) status.color() else Color.Transparent)
                            .border(2.dp, if (enabled) status.color().takeIf { status != null } ?: AccentGreen else CardHigh, CircleShape)
                            .clickable(enabled = enabled, role = Role.Checkbox) { vm.toggle(p) }
                            .semantics { contentDescription = "${p.displayName}: ${status.label()}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (status) {
                            PrayerStatus.ON_TIME -> Icon(Icons.Filled.Check, null, tint = Color.Black)
                            PrayerStatus.LATE -> Icon(Icons.Filled.Schedule, null, tint = Color.Black)
                            PrayerStatus.MISSED -> Icon(Icons.Filled.Close, null, tint = Color.Black)
                            null -> {}
                        }
                    }
                    Text(p.label(abbreviated), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(
            "🔥 ${s.stats.currentDays} day streak · ${s.stats.currentPrayers} prayers in a row",
            color = Gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

