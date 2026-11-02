package com.kamalahmed.prayerapp.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.R
import com.kamalahmed.prayerapp.alarm.SystemStatus
import com.kamalahmed.prayerapp.core.location.Cities
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SwitchRow
import com.kamalahmed.prayerapp.ui.settings.SettingsViewModel
import com.kamalahmed.prayerapp.ui.theme.AccentGreen
import com.kamalahmed.prayerapp.ui.theme.AccentGreenDim
import com.kamalahmed.prayerapp.ui.theme.Danger
import com.kamalahmed.prayerapp.ui.theme.Gold

private const val STEPS = 5

/** First-run flow: location, notifications, reliable alarms, widgets. Every step can be skipped. */
@Composable
fun OnboardingScreen(vm: SettingsViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val status by vm.status.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }
    BackHandler(enabled = step > 0) { step-- }
    val ctx = LocalContext.current

    val locationPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) { vm.update { it.copy(autoLocation = true) }; vm.refreshLocation() }
        else vm.message.value = "Permission denied – pick your city below"
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshStatus() }
    LaunchedEffect(step) { vm.refreshStatus() }

    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp)) {
        // Progress dots
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(STEPS) { i ->
                Box(
                    Modifier.padding(4.dp).size(if (i == step) 10.dp else 8.dp)
                        .background(if (i <= step) AccentGreen else AccentGreenDim, CircleShape),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> {
                    Spacer(Modifier.height(40.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(112.dp).background(AccentGreenDim, CircleShape), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(150.dp))
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Title("Assalamu alaikum")
                    Body("Accurate prayer times that work offline, the adhan with your choice of sound, home-screen widgets, and a gentle prayer tracker.")
                    Spacer(Modifier.height(12.dp))
                    Body("Let's set it up – it takes under a minute.")
                }
                1 -> {
                    StepIcon(Icons.Filled.LocationOn)
                    Title("Your location")
                    Body("Prayer times depend on where you are. Using your location is most accurate; it stays on this phone.")
                    Spacer(Modifier.height(16.dp))
                    CardGroup {
                        ListRow("Current: ${s.cityName}", subtitle = if (s.autoLocation) "Automatic" else "Manual")
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (vm.hasLocationPermission()) { vm.update { it.copy(autoLocation = true) }; vm.refreshLocation() }
                        else locationPerm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Use my location") }
                    msg?.let { Text(it, color = Gold, modifier = Modifier.padding(top = 8.dp)) }
                    Text("Or pick a city", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    CardGroup {
                        Cities.all.take(12).forEachIndexed { i, c ->
                            if (i > 0) RowDivider()
                            ListRow(c.display, onClick = { vm.setCity(c) }, showChevron = false) {
                                if (!s.autoLocation && s.cityName == c.name) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = AccentGreen)
                            }
                        }
                    }
                    Body("More cities in Settings → Location.")
                }
                2 -> {
                    StepIcon(Icons.Filled.Notifications)
                    Title("Prayer notifications")
                    Body("Allow notifications so the app can announce each prayer and play the adhan.")
                    Spacer(Modifier.height(16.dp))
                    StatusLine("Notifications", status.notificationsAllowed)
                    Spacer(Modifier.height(12.dp))
                    if (!status.notificationsAllowed) {
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else SystemStatus.open(ctx, SystemStatus.notificationSettings(ctx))
                        }, modifier = Modifier.fillMaxWidth()) { Text("Allow notifications") }
                    }
                }
                3 -> {
                    StepIcon(Icons.Filled.Alarm)
                    Title("Adhan on time")
                    Body("Android can delay alarms to save battery. These keep the adhan exactly on time. You can change them later in Settings → System.")
                    Spacer(Modifier.height(16.dp))
                    CardGroup {
                        ListRow("Alarms & reminders", subtitle = if (status.exactAlarmsAllowed) "Allowed" else "Tap to allow",
                            leading = Icons.Filled.Alarm, leadingTint = if (status.exactAlarmsAllowed) AccentGreen else Danger,
                            onClick = { SystemStatus.open(ctx, SystemStatus.exactAlarmSettings(ctx)) })
                        RowDivider()
                        ListRow("Battery: unrestricted", subtitle = if (status.batteryUnrestricted) "Done" else "Recommended – tap to allow",
                            leading = Icons.Filled.BatteryChargingFull, leadingTint = if (status.batteryUnrestricted) AccentGreen else Gold,
                            onClick = { SystemStatus.open(ctx, SystemStatus.batterySettings(ctx)) })
                        RowDivider()
                        SwitchRow("Full-screen adhan", s.fullScreenAlarm, subtitle = "Show an alarm screen over the lock screen") { v ->
                            vm.update { it.copy(fullScreenAlarm = v) }
                            if (v && !status.fullScreenAllowed) SystemStatus.open(ctx, SystemStatus.fullScreenSettings(ctx))
                        }
                    }
                }
                else -> {
                    StepIcon(Icons.Filled.Widgets)
                    Title("You're all set")
                    Body("Add a widget: long-press your home screen → Widgets → Prayer App. There are six styles, each fully customisable – or open Settings → Widget studio.")
                    Spacer(Modifier.height(12.dp))
                    Body("Mark prayers from the Prayers tab or the tracker widget to build your streak.")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step in 1 until STEPS - 1) TextButton(onClick = { step++ }) { Text("Skip") }
            else if (step == 0) TextButton(onClick = { vm.finishOnboarding() }) { Text("Skip setup") }
            Spacer(Modifier.weight(1f))
            if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.padding(end = 8.dp)) { Text("Back") }
            Button(onClick = { if (step < STEPS - 1) step++ else vm.finishOnboarding() }) {
                Text(if (step < STEPS - 1) "Next" else "Start")
            }
        }
    }
    }
}

@Composable
private fun StepIcon(icon: ImageVector) {
    Spacer(Modifier.height(16.dp))
    Box(Modifier.size(64.dp).background(AccentGreenDim, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(32.dp))
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Title(text: String) = Text(
    text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
)

@Composable
private fun Body(text: String) = Text(
    text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Start, modifier = Modifier.padding(top = 4.dp),
)

@Composable
private fun StatusLine(title: String, ok: Boolean) {
    CardGroup {
        ListRow(title, subtitle = if (ok) "Allowed" else "Not allowed", leading = Icons.Filled.CheckCircle, leadingTint = if (ok) AccentGreen else Danger)
    }
}
