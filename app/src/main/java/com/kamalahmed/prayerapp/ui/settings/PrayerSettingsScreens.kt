package com.kamalahmed.prayerapp.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.kamalahmed.prayerapp.core.model.AsrMethod
import com.kamalahmed.prayerapp.core.model.HighLatitudeRule
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.core.prayer.CalculationMethod
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SectionHeader
import com.kamalahmed.prayerapp.ui.common.SliderRow
import com.kamalahmed.prayerapp.ui.common.StepperRow
import com.kamalahmed.prayerapp.ui.common.SubScreen
import com.kamalahmed.prayerapp.ui.common.SwitchRow
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun RadioRow(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Messages(vm: SettingsViewModel) {
    val ctx = LocalContext.current
    val msg by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(msg) { msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.message.value = null } }
}

/** Today's times for the current settings — shown live while changing calculation options. */
@Composable
private fun TodayPreview(s: AppSettings) {
    val day = remember(s) { runCatching { PrayerSchedule(s.toCalcParams()).day(LocalDate.now()) }.getOrNull() }
    CardGroup {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            if (day == null) Text("Invalid settings") else Prayer.entries.forEach { p ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.abbr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(TimeFormat.time(day[p], s.use24h, showAmPm = false), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun CalculationScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    SubScreen("Calculation", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader("Today with these settings")
            TodayPreview(s)
            SectionHeader("Method")
            CardGroup {
                CalculationMethod.entries.forEach { m ->
                    val angles = if (m.ishaInterval > 0) "Fajr ${m.fajrAngle}°, Isha +${m.ishaInterval} min" else "Fajr ${m.fajrAngle}°, Isha ${m.ishaAngle}°"
                    RadioRow(m.label, s.calcMethod == m.name, angles) { vm.update { it.copy(calcMethod = m.name) } }
                }
            }
            SectionHeader("Asr")
            CardGroup {
                AsrMethod.entries.forEach { a -> RadioRow(a.label, s.asrMethod == a) { vm.update { it.copy(asrMethod = a) } } }
            }
            SectionHeader("High latitude rule")
            CardGroup {
                HighLatitudeRule.entries.forEach { r -> RadioRow(r.label, s.highLatRule == r) { vm.update { it.copy(highLatRule = r) } } }
            }
            SectionHeader("Manual adjustments (minutes)")
            CardGroup {
                Prayer.entries.forEachIndexed { i, p ->
                    if (i > 0) RowDivider()
                    val v = s.adjustment(p)
                    StepperRow(p.displayName, if (v > 0) "+$v min" else "$v min",
                        onMinus = { vm.update { it.copy(adjustments = it.adjustments + (p to (v - 1).coerceAtLeast(-30))) } },
                        onPlus = { vm.update { it.copy(adjustments = it.adjustments + (p to (v + 1).coerceAtMost(30))) } })
                }
            }
        }
    }
}

@Composable
fun LocationScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    Messages(vm)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) { vm.update { it.copy(autoLocation = true) }; vm.refreshLocation() }
        else vm.message.value = "Location permission denied – choose a city instead"
    }
    fun enableAuto() {
        if (vm.hasLocationPermission()) { vm.update { it.copy(autoLocation = true) }; vm.refreshLocation() }
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    SubScreen("Location", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader("Current")
            CardGroup {
                ListRow(s.cityName, subtitle = String.format(Locale.US, "%.4f, %.4f · %s", s.latitude, s.longitude, s.timeZoneId), leading = Icons.Filled.MyLocation)
            }
            SectionHeader("Automatic")
            CardGroup {
                SwitchRow("Automatic location", s.autoLocation, subtitle = "Uses network/GPS occasionally, low power") { on ->
                    if (on) enableAuto() else vm.update { it.copy(autoLocation = false) }
                }
                if (s.autoLocation) {
                    RowDivider()
                    ListRow("Update now", onClick = { vm.refreshLocation() })
                    RowDivider()
                    SliderRow("Geofence", s.geofenceKm.toFloat(), 1f..50f, "${s.geofenceKm} km") { v ->
                        vm.update { it.copy(geofenceKm = v.roundToInt()) }
                    }
                    Text(
                        "Times are only recalculated when you move further than this.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
            SectionHeader("Choose a city")
            CardGroup {
                CitySearch(selectedLabel = s.cityName.takeIf { !s.autoLocation }, onPick = { vm.setCity(it) })
            }
            SectionHeader("Enter coordinates")
            CardGroup {
                ManualCoordinates { la, lo -> vm.setManualLocation(la, lo, null) }
            }
        }
    }
}

@Composable
fun TimeSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    SubScreen("Time", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader("Format")
            CardGroup {
                SwitchRow("24-hour clock", s.use24h) { v -> vm.update { it.copy(use24h = v) } }
                RowDivider()
                SwitchRow("Abbreviated prayer names", s.abbreviations, subtitle = "FJR, DHR, ASR, MGB, ISH") { v -> vm.update { it.copy(abbreviations = v) } }
            }
            SectionHeader("Hijri date")
            CardGroup {
                StepperRow("Adjustment", if (s.hijriAdjust > 0) "+${s.hijriAdjust} day" else "${s.hijriAdjust} day",
                    onMinus = { vm.update { it.copy(hijriAdjust = (it.hijriAdjust - 1).coerceAtLeast(-2)) } },
                    onPlus = { vm.update { it.copy(hijriAdjust = (it.hijriAdjust + 1).coerceAtMost(2)) } })
                RowDivider()
                SwitchRow("Advance after Maghrib", s.autoIncrementHijri, subtitle = "The Islamic day starts at sunset") { v ->
                    vm.update { it.copy(autoIncrementHijri = v) }
                }
            }
            SectionHeader("After a prayer starts")
            CardGroup {
                StepperRow("Count-up", if (s.countUpMinutes == 0) "Off" else "${s.countUpMinutes} min",
                    onMinus = { vm.update { it.copy(countUpMinutes = (it.countUpMinutes - 5).coerceAtLeast(0)) } },
                    onPlus = { vm.update { it.copy(countUpMinutes = (it.countUpMinutes + 5).coerceAtMost(90)) } })
                Text(
                    "Shows time elapsed since the current prayer began, for this many minutes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
fun IqamaScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Prayer?>(null) }
    var fridayPending by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val prayer = pending
        val friday = fridayPending // Snapshot before resetting state; the ViewModel update is asynchronous.
        if (granted) vm.update { old -> if (friday) old.copy(jumuahNotify = true) else old.copy(iqamaNotifications = old.iqamaNotifications + listOfNotNull(prayer)) }
        else vm.message.value = "Notifications are not allowed; the iqamah reminder remains off."
        pending = null; fridayPending = false
    }
    fun notify(prayer: Prayer?, on: Boolean) {
        if (on && android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pending = prayer; fridayPending = prayer == null
            com.kamalahmed.prayerapp.core.safety.guard("Iqamah","notification permission") { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        } else vm.update { old ->
            if (prayer == null) old.copy(jumuahNotify = on)
            else old.copy(iqamaNotifications = if (on) old.iqamaNotifications + prayer else old.iqamaNotifications - prayer)
        }
    }
    Messages(vm)
    SubScreen("Iqamah",onBack) {
        IqamahSettingsContent(s,{ change -> vm.update(change) },::notify)
    }
}

@Composable
fun AppScreen(vm: SettingsViewModel, onBack: () -> Unit, onOnboarding: () -> Unit, updateVm: com.kamalahmed.prayerapp.update.UpdateViewModel? = null) {
    var confirmReset by remember { mutableStateOf(false) }
    SubScreen("App", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader("About")
            CardGroup {
                ListRow("Prayer App", subtitle = "Version ${com.kamalahmed.prayerapp.BuildConfig.VERSION_NAME} · offline prayer times, adhan, widgets and tracker")
                RowDivider()
                ListRow("Adhan recordings", subtitle = "\"Beautiful adhan.ogg\" by Adam-synagda and \"Adhan.ogg\" by Aishatu98, Wikimedia Commons, CC0 (public domain)")
                RowDivider()
                ListRow("City database", subtitle = "World cities from GeoNames (geonames.org), licensed CC BY 4.0. Bundled offline.")
                RowDivider()
                ListRow("Privacy", subtitle = "No accounts, no analytics. Location stays on the device and is only used to calculate times.")
            }
            if (updateVm != null) com.kamalahmed.prayerapp.update.UpdateSection(updateVm)
            DiagnosticsSection()
            SectionHeader("Manage")
            CardGroup {
                ListRow("Show onboarding again", onClick = onOnboarding)
                RowDivider()
                ListRow("Reset all settings", subtitle = "Prayer log and widgets are kept", onClick = { confirmReset = true })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all settings?") },
            text = { Text("Calculation, location, notification and sound settings go back to defaults (Sharjah, Authority of Dubai).") },
            confirmButton = { TextButton(onClick = { vm.resetAll(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

/** Settings > App > Diagnostics: last crash and recent handled errors, stored only on this device. */
@Composable
fun DiagnosticsSection() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var version by remember { mutableStateOf(0) }
    val last = remember(version) { com.kamalahmed.prayerapp.core.safety.CrashLog.lastCrash(ctx) }
    var show by remember { mutableStateOf(false) }
    SectionHeader("Diagnostics")
    CardGroup {
        ListRow(
            "Last crash report",
            subtitle = last?.lineSequence()?.firstOrNull()?.removePrefix("CRASH at ") ?: "No crashes recorded",
            onClick = if (last != null) ({ show = true }) else null,
        )
        RowDivider()
        ListRow("Copy diagnostics", subtitle = "Copies the crash and error log (no location or personal data) to share with the developer", onClick = {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(com.kamalahmed.prayerapp.core.safety.CrashLog.diagnostics(ctx)))
            Toast.makeText(ctx, "Diagnostics copied", Toast.LENGTH_SHORT).show()
        })
        RowDivider()
        ListRow("Clear diagnostics", onClick = { com.kamalahmed.prayerapp.core.safety.CrashLog.clear(ctx); version++ })
    }
    if (show && last != null) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("Last crash") },
            text = { Text(last.take(3_000), modifier = Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Close") } },
        )
    }
}
