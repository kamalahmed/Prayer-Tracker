package com.kamalahmed.prayerapp.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SectionHeader
import com.kamalahmed.prayerapp.ui.common.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** One-shot request (e.g. from the update notification) to open Settings > App. */
object UpdateNav { val openRequested = MutableStateFlow(false) }

@HiltViewModel
class UpdateViewModel @Inject constructor(val manager: UpdateManager) : ViewModel() {
    val state = manager.state
    val prefs = manager.prefs
    fun check() = viewModelScope.launch(com.kamalahmed.prayerapp.core.safety.safeHandler("UpdateUi")) { manager.check() }
    fun setAutoCheck(ctx: android.content.Context, v: Boolean) { prefs.setAutoCheck(v); UpdateWorker.schedule(ctx, v, prefs.wifiOnly.value, replace = true) }
    fun setWifiOnly(ctx: android.content.Context, v: Boolean) { prefs.setWifiOnly(v); UpdateWorker.schedule(ctx, prefs.autoCheck.value, v, replace = true) }
    fun setIncludeBeta(v: Boolean) { prefs.setIncludeBeta(v); check() }
}

private fun UpdateState.release(): ReleaseInfo? = when (this) {
    is UpdateState.Available -> release
    is UpdateState.Downloading -> release
    is UpdateState.Downloaded -> release
    is UpdateState.Installing -> release
    is UpdateState.Error -> release
    else -> null
}

/** Small banner for Settings / More; hidden unless an update is known. */
@Composable
fun UpdateBanner(vm: UpdateViewModel, onOpen: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val rel = s.release() ?: return
    if (s is UpdateState.Error) return
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Update available", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Version ${rel.version}" + if (rel.prerelease) " (beta)" else "", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(if (s is UpdateState.Downloading) "Downloading…" else "View", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
    }
}

/** "Updates" section for Settings > App. */
@Composable
fun UpdateSection(vm: UpdateViewModel) {
    val ctx = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    val auto by vm.prefs.autoCheck.collectAsStateWithLifecycle()
    val wifi by vm.prefs.wifiOnly.collectAsStateWithLifecycle()
    val beta by vm.prefs.includeBeta.collectAsStateWithLifecycle()
    val promptShown by vm.prefs.installPromptShown.collectAsStateWithLifecycle()
    var showPermissionGuide by remember { mutableStateOf(false) }
    var pendingInstall by remember { mutableStateOf<UpdateState.Downloaded?>(null) }
    var showNotes by remember { mutableStateOf(false) }

    // Returning from the "Install unknown apps" screen: continue the install if allowed now.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        pendingInstall?.let { d -> if (vm.manager.canInstall()) { pendingInstall = null; vm.manager.install(d.release, d.file) } }
    }

    fun install(d: UpdateState.Downloaded) {
        if (vm.manager.canInstall()) vm.manager.install(d.release, d.file)
        else if (!promptShown) { pendingInstall = d; showPermissionGuide = true }
        else { pendingInstall = d; runCatching { ctx.startActivity(vm.manager.unknownSourcesIntent()) } }
    }

    SectionHeader("Updates")
    CardGroup {
        val last = vm.prefs.lastCheck
        ListRow("Current version", subtitle = "${vm.manager.currentVersion} (build ${vm.manager.currentCode})")
        RowDivider()
        val statusText = when (val st = s) {
            UpdateState.Idle -> if (last > 0) "Last checked ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))}" else "Not checked yet"
            UpdateState.Checking -> "Checking GitHub…"
            UpdateState.UpToDate -> "You're on the latest version"
            is UpdateState.Error -> st.message
            else -> null
        }
        ListRow("Check for updates", subtitle = statusText, onClick = if (s is UpdateState.Checking || s is UpdateState.Downloading || s is UpdateState.Installing) null else ({ vm.check() })) {
            if (s is UpdateState.Checking) CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp)
        }
        val rel = s.release()
        if (rel != null) {
            RowDivider()
            Column(Modifier.padding(16.dp)) {
                Text("Update available: ${rel.version}" + if (rel.prerelease) " (beta)" else "", fontWeight = FontWeight.SemiBold)
                rel.apk?.let { Text("%.1f MB".format(it.size / 1_048_576.0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (rel.notes.isNotBlank()) {
                    Spacer(Modifier.padding(top = 6.dp))
                    Text(rel.notes.take(280) + if (rel.notes.length > 280) "…" else "", style = MaterialTheme.typography.bodyMedium)
                    if (rel.notes.length > 280) TextButton(onClick = { showNotes = true }) { Text("Full release notes") }
                }
                Spacer(Modifier.padding(top = 10.dp))
                when (val st = s) {
                    is UpdateState.Downloading -> {
                        LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("%.1f / %.1f MB".format(st.bytes / 1_048_576.0, st.total / 1_048_576.0), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { vm.manager.cancelDownload() }) { Text("Pause") }
                            TextButton(onClick = { vm.manager.discardDownload(st.release) }) { Text("Cancel") }
                        }
                    }
                    is UpdateState.Downloaded -> Button(onClick = { install(st) }, Modifier.fillMaxWidth()) { Text("Install update") }
                    is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Installing… confirm in the system dialog")
                    }
                    else -> Button(onClick = { vm.manager.startDownload(rel) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (st is UpdateState.Error) "Retry download" else "Download & install")
                    }
                }
            }
        }
        RowDivider()
        SwitchRow("Auto-check daily", auto, subtitle = "Notify me when a new version is published") { vm.setAutoCheck(ctx, it) }
        if (auto) { RowDivider(); SwitchRow("Wi-Fi only", wifi, subtitle = "Check only on unmetered networks") { vm.setWifiOnly(ctx, it) } }
        RowDivider()
        SwitchRow("Include beta releases", beta, subtitle = "Get pre-release builds from GitHub") { vm.setIncludeBeta(it) }
    }

    if (showPermissionGuide) {
        AlertDialog(
            onDismissRequest = { showPermissionGuide = false },
            title = { Text("Allow installing updates") },
            text = { Text("To update itself, Prayer App needs Android's \"Install unknown apps\" permission. On the next screen, turn on \"Allow from this source\", then press Back. You only need to do this once.") },
            confirmButton = {
                Button(onClick = {
                    vm.prefs.markInstallPromptShown(); showPermissionGuide = false
                    runCatching { ctx.startActivity(vm.manager.unknownSourcesIntent()) }
                }) { Text("Open settings") }
            },
            dismissButton = { OutlinedButton(onClick = { showPermissionGuide = false; pendingInstall = null }) { Text("Not now") } },
        )
    }
    if (showNotes) {
        val r = s.release()
        AlertDialog(
            onDismissRequest = { showNotes = false },
            title = { Text(r?.name ?: "Release notes") },
            text = { Text(r?.notes.orEmpty(), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showNotes = false }) { Text("Close") } },
        )
    }
}
