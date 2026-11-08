package com.kamalahmed.prayerapp.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    Scaffold(topBar={ TopAppBar(title={ Text("Backup & restore") },navigationIcon={ TextButton(onClick=onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Last backup: " + if(ui.lastBackup==0L) "Never" else DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ui.lastBackup)))
            Text("Your prayer, Qada, Nafl, counter history and all app/widget settings are included. Streaks are recalculated from the restored logs.")
            Text("Backups are unencrypted personal JSON files (up to 32 MB, including custom audio). Only save them to storage you trust.",style=MaterialTheme.typography.bodySmall)
            if(ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.message?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
            Button(onClick={ export() },enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) { Text("Export backup") }
            OutlinedButton(onClick={ try { open.launch(arrayOf("application/json","text/plain","application/octet-stream")) } catch(e: Exception) { vm.pickerError(e) } },enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) { Text("Import backup") }
            HorizontalDivider()
            Text("Automatic protection",style=MaterialTheme.typography.titleMedium)
            Text("Daily local snapshots keep the last 7 days, plus recovery points before imports and database upgrades. Android Auto Backup includes your database, settings and snapshots when enabled by your device.")
            Text("Local snapshots are deleted if you uninstall or clear app data. Choose a Documents folder or export a backup to keep an independent copy.",color=MaterialTheme.colorScheme.error)
            Text(if(ui.folder==null) "Folder auto-export: Off" else "Folder auto-export: On\n${ui.folder}",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={ try { tree.launch(null) } catch(e: Exception) { vm.pickerError(e) } },enabled=!ui.busy) { Text("Choose automatic export folder") }
            if(ui.folder!=null) TextButton(onClick={ vm.chooseFolder(null) },enabled=!ui.busy) { Text("Disable folder auto-export") }
            OutlinedButton(onClick=vm::snapshotNow,enabled=!ui.busy) { Text("Take snapshot now") }
            Text("Restore from snapshot",style=MaterialTheme.typography.titleMedium)
            if(ui.snapshots.isEmpty()) Text("No snapshots yet. The first daily job runs when Android permits it.")
            ui.snapshots.forEach { file ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(file.name,style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={ vm.openSnapshot(file) },enabled=!ui.busy) { Text("Restore") }
                        TextButton(onClick={ export(file) },enabled=!ui.busy) { Text("Export copy") }
                    }
                } }
            }
            Text("Android may delay scheduled work. Cloud/device backup depends on device settings, network and quota. No Google Drive sign-in is required.",style=MaterialTheme.typography.bodySmall)
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
