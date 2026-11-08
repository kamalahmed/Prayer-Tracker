package com.kamalahmed.prayerapp.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: BackupViewModel,onBack: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var exportSnapshot by remember { mutableStateOf<File?>(null) }
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null) vm.export(uri,exportSnapshot) }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.open(uri) }
    val tree=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if(uri!=null) vm.chooseFolder(uri) }
    fun export(file: File?=null) {
        exportSnapshot=file
        try { create.launch("prayer-backup-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json") } catch(e: Exception) { vm.pickerError(e) }
    }
    val snackbar=remember { SnackbarHostState() }
    val ctx=androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it,withDismissAction=true) } }
    fun date(at: Long)=if(at==0L) "Not yet recorded" else DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a z").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))
    fun backupSettings() {
        com.kamalahmed.prayerapp.core.safety.guard("Backup","device backup settings") {
            val candidates=listOf(android.content.Intent("android.settings.BACKUP_SETTINGS"),android.content.Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS),android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
            val intent=candidates.firstOrNull { it.resolveActivity(ctx.packageManager)!=null } ?: candidates.last()
            ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    Scaffold(snackbarHost={ SnackbarHost(snackbar) },topBar={ TopAppBar(title={ Text("Backup & restore") },navigationIcon={ TextButton(onClick=onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Know what is saved. Know where.",style=MaterialTheme.typography.titleLarge)
            Text("Your prayer logs, Qada, Nafl, counters and all app/widget settings are included. Streaks are recalculated from the logs.",style=MaterialTheme.typography.bodySmall)
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("On this phone",style=MaterialTheme.typography.titleMedium)
                Text(if(ui.status.localAt>0) "Saved · ${date(ui.status.localAt)}" else "No local snapshot recorded yet",color=MaterialTheme.colorScheme.primary)
                ui.status.localName?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
                Text("7 daily snapshots plus recovery points before imports and database upgrades. Phone copies are removed on uninstall.",style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Your chosen folder",style=MaterialTheme.typography.titleMedium)
                Text(ui.folder?.let(::backupFolderLabel) ?: "Not configured",style=MaterialTheme.typography.bodyMedium)
                Text(if(ui.status.folderAt>0) "Last verified copy · ${date(ui.status.folderAt)}" else "No verified folder copy recorded",color=if(ui.status.folderError!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                ui.status.folderError?.let { Text("Copy failed: $it. Phone snapshots are kept. Re-select the folder to renew access.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                Text("Folder copies survive uninstall. Grant access again after reinstalling.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={ try { tree.launch(null) } catch(e: Exception) { vm.pickerError(e) } },enabled=!ui.busy) { Text("Choose automatic export folder") }
                if(ui.folder!=null) TextButton(onClick={ vm.chooseFolder(null) },enabled=!ui.busy) { Text("Disable folder auto-export") }
            } }
            Button(onClick=vm::snapshotNow,enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) { Text(if(ui.busy) ui.operation ?: "Working…" else "Take snapshot now") }
            Text(if(ui.folder==null) "Saves a verified copy on this phone" else "Saves on this phone + your chosen folder",style=MaterialTheme.typography.bodySmall)
            // Keep progress and the lasting receipt at the action, not off-screen above it.
            if(ui.busy) Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth()); Text(ui.operation ?: "Working…",style=MaterialTheme.typography.bodySmall)
            }
            ui.message?.let { Text(it,color=if(ui.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium) }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Android Auto Backup · Android managed",style=MaterialTheme.typography.titleMedium)
                Text("Last successful Android backup",style=MaterialTheme.typography.bodySmall)
                Text("Not reported to this app",style=MaterialTheme.typography.titleLarge)
                Text("Android schedules cloud/device backup. This app cannot confirm its last successful upload. A local snapshot is not cloud confirmation.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=::backupSettings) { Text("Open device backup settings") }
            } }
            Text("Recovery options",style=MaterialTheme.typography.titleMedium)
            Text("Last backup export / snapshot: ${date(ui.lastBackup)}",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={ export() },enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) { Text("Export backup") }
            OutlinedButton(onClick={ try { open.launch(arrayOf("application/json","text/plain","application/octet-stream")) } catch(e: Exception) { vm.pickerError(e) } },enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) { Text("Import backup") }
            Text("Restore from snapshot",style=MaterialTheme.typography.titleMedium)
            var showSnapshots by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick={ showSnapshots=!showSnapshots }) { Text(if(showSnapshots) "Hide phone snapshots" else "View ${ui.snapshots.size} saved phone snapshots") }
            if(showSnapshots) {
                if(ui.snapshots.isEmpty()) Text("No snapshots yet. Take one now, or Android will run the daily job when permitted.")
                ui.snapshots.forEach { file -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(file.name,style=MaterialTheme.typography.bodySmall)
                    Text(date(file.lastModified()),style=MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={ vm.openSnapshot(file) },enabled=!ui.busy) { Text("Restore") }
                        TextButton(onClick={ export(file) },enabled=!ui.busy) { Text("Export copy") }
                    }
                } } }
            }
            Text("Backups are unencrypted personal JSON files (up to 32 MB, including custom audio). Save them only to trusted storage. Android may delay scheduled work; cloud backup depends on device settings, network and quota. No Google Drive sign-in is required.",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
    ui.preview?.let { summary ->
        AlertDialog(onDismissRequest=vm::dismissPreview,title={ Text("Review backup") },text={ Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(summary)
            Text("Widget appearance is included, but launcher placements and external ringtone permissions may need reconfiguration.",style=MaterialTheme.typography.bodySmall)
            RestoreMode.entries.forEach { choice -> Row {
                RadioButton(selected=mode==choice,onClick={mode=choice},enabled=!ui.busy)
                TextButton(onClick={mode=choice},enabled=!ui.busy) { Text(if(choice==RestoreMode.MERGE) "Merge" else "Replace") }
            } }
            Text(if(mode==RestoreMode.MERGE) "Merge keeps current settings, newer prayer marks and larger daily counts. Separate Qada events are combined without doubling identical entries." else "Replace removes current data and settings, then restores this file. A safety snapshot is saved first.")
        } },confirmButton={ TextButton(onClick={vm.restore(mode)},enabled=!ui.busy) { Text("Restore now") } },dismissButton={ TextButton(onClick=vm::dismissPreview,enabled=!ui.busy) { Text("Cancel") } })
    }
}

fun backupFolderLabel(value: String): String=try {
    android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(value)).substringAfter(':').replace("/"," / ").ifBlank { "Chosen document folder" }
} catch(_: Exception) { "Chosen document folder" }
