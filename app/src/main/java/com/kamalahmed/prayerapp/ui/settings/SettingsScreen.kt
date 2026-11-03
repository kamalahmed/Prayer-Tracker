package com.kamalahmed.prayerapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.prayer.CalculationMethod
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SectionHeader
import com.kamalahmed.prayerapp.ui.theme.Danger

/** Destinations reachable from the Settings root. */
enum class SettingsDest(val route: String) {
    NOTIFICATIONS("settings/notifications"),
    SOUND("settings/sound"),
    SYSTEM("settings/system"),
    CALCULATION("settings/calculation"),
    LOCATION("settings/location"),
    TIME("settings/time"),
    IQAMA("settings/iqama"),
    APP("settings/app"),
    WIDGETS("settings/widgets"),
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, open: (SettingsDest) -> Unit, available: Set<SettingsDest>, selected: SettingsDest? = null, banner: @Composable () -> Unit = {}) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }
    val s by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val st = s ?: return
    SettingsContent(st, status.hasProblem(st.fullScreenAlarm), open, available, selected, banner)
}

/** Stateless settings list (testable without a ViewModel). [selected] is highlighted in list-detail mode. */
@Composable
fun SettingsContent(
    st: com.kamalahmed.prayerapp.data.settings.AppSettings, systemProblem: Boolean, open: (SettingsDest) -> Unit,
    available: Set<SettingsDest>, selected: SettingsDest? = null, banner: @Composable () -> Unit = {},
) {
    fun go(d: SettingsDest): (() -> Unit)? = if (d in available) ({ open(d) }) else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
        banner()
        SectionHeader("Notification")
        CardGroup {
            ListRow("Prayer alerts & adhan", subtitle = "Per-prayer mode, full-screen, snooze, reminders", onClick = go(SettingsDest.NOTIFICATIONS), selected = selected == SettingsDest.NOTIFICATIONS)
            RowDivider()
            ListRow("Adhan sound", subtitle = st.soundName, onClick = go(SettingsDest.SOUND), selected = selected == SettingsDest.SOUND)
        }
        SectionHeader("Prayer")
        CardGroup {
            ListRow("Calculation", subtitle = CalculationMethod.fromId(st.calcMethod).label, onClick = go(SettingsDest.CALCULATION), selected = selected == SettingsDest.CALCULATION)
            RowDivider()
            ListRow("Location", subtitle = st.cityName + if (st.autoLocation) " (auto)" else "", onClick = go(SettingsDest.LOCATION), selected = selected == SettingsDest.LOCATION)
            RowDivider()
            ListRow("Time", subtitle = "24-hour, hijri, abbreviations, count-up", onClick = go(SettingsDest.TIME), selected = selected == SettingsDest.TIME)
            RowDivider()
            ListRow("Iqama", subtitle = if (st.iqamaEnabled) "On" else "Off", onClick = go(SettingsDest.IQAMA), selected = selected == SettingsDest.IQAMA)
        }
        SectionHeader("Device")
        CardGroup {
            ListRow("System", onClick = go(SettingsDest.SYSTEM), selected = selected == SettingsDest.SYSTEM) {
                if (systemProblem) {
                    Box(Modifier.size(22.dp).background(Danger, CircleShape), contentAlignment = Alignment.Center) {
                        Text("!", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            RowDivider()
            ListRow("App", subtitle = "About, updates, onboarding, reset", onClick = go(SettingsDest.APP), selected = selected == SettingsDest.APP)
        }
        SectionHeader("Widgets")
        CardGroup {
            ListRow("Widget studio", subtitle = "Themes, transparency, sizes, spacing", onClick = go(SettingsDest.WIDGETS), selected = selected == SettingsDest.WIDGETS)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Prayer App ${com.kamalahmed.prayerapp.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp),
        )
    }
}
