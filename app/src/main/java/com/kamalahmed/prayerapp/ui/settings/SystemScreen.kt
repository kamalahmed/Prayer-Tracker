package com.kamalahmed.prayerapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.alarm.SystemStatus
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SectionHeader
import com.kamalahmed.prayerapp.ui.common.SubScreen
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.Danger

@Composable
fun SystemScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }
    val status by vm.status.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    @Composable
    fun item(title: String, ok: Boolean, okText: String, badText: String, fix: () -> Unit) {
        ListRow(
            title, subtitle = if (ok) okText else badText,
            leading = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
            leadingTint = if (ok) AccentGreen else Danger,
            onClick = fix,
        )
    }

    SubScreen("System", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "These system settings can stop the adhan from playing on time. Anything red needs your attention.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            CardGroup {
                item("Notifications", status.notificationsAllowed, "Allowed", "Blocked – tap to allow") {
                    SystemStatus.open(ctx, SystemStatus.notificationSettings(ctx))
                }
                RowDivider()
                item("Adhan notification channel", !status.adhanChannelBlocked, "Enabled", "Turned off – tap to enable") {
                    SystemStatus.open(ctx, SystemStatus.notificationSettings(ctx))
                }
                RowDivider()
                item("Alarms & reminders (exact alarms)", status.exactAlarmsAllowed, "Allowed – adhan is on time", "Not allowed – adhan may be late") {
                    SystemStatus.open(ctx, SystemStatus.exactAlarmSettings(ctx))
                }
                RowDivider()
                item("Battery optimisation", status.batteryUnrestricted, "Unrestricted", "Restricted – may delay or kill the adhan") {
                    SystemStatus.open(ctx, SystemStatus.batterySettings(ctx))
                }
                RowDivider()
                item(
                    "Full-screen notifications", status.fullScreenAllowed || s?.fullScreenAlarm != true,
                    if (status.fullScreenAllowed) "Allowed" else "Not needed (setting off)", "Needed for the lock-screen alarm",
                ) { SystemStatus.open(ctx, SystemStatus.fullScreenSettings(ctx)) }
                RowDivider()
                item("Location", status.locationAllowed || s?.autoLocation != true, if (status.locationAllowed) "Allowed" else "Manual city in use", "Needed for automatic location") {
                    SystemStatus.open(ctx, SystemStatus.appDetails(ctx))
                }
            }
            SectionHeader("Phone maker tips")
            Text(
                "• Xiaomi / Redmi / POCO: Settings › Apps › Prayer App › Autostart ON, Battery saver: No restrictions.\n" +
                    "• Samsung: Settings › Battery › Background usage limits – add Prayer App to \"Never sleeping apps\".\n" +
                    "• Huawei / Honor: Battery › App launch › Prayer App › Manage manually (all on).\n" +
                    "• OnePlus / Oppo / Realme / Vivo: Battery › allow background activity and auto-launch.\n" +
                    "• Keep Prayer App out of any \"cleaner\" or task-killer apps.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
