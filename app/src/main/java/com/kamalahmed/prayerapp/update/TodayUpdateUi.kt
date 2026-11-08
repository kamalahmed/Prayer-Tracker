package com.kamalahmed.prayerapp.update

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.safety.guard

@Composable
fun TodayUpdateBanner(vm: UpdateViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val later by vm.prefs.later.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifications = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(UpdateWorker.CHANNEL)?.importance != android.app.NotificationManager.IMPORTANCE_NONE
    val rel = state.updateRelease() ?: return
    if (later == rel.key && vm.prefs.isDeferred(rel.key) && state !is UpdateState.Downloading && state !is UpdateState.Installing) return
    TodayUpdateCard(state,{ vm.manager.acceptUpdate(rel) },{ vm.manager.later(rel) },notifications) {
        guard("TodayUpdate","notifications") {
            context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))
        }
    }
}

internal fun UpdateState.updateRelease(): ReleaseInfo? = when (this) {
    is UpdateState.Available -> release
    is UpdateState.Downloaded -> release
    is UpdateState.Downloading -> release
    is UpdateState.Installing -> release
    is UpdateState.Error -> release
    else -> null
}

/** Stateless card exercised with Compose/Robolectric, independent of navigation and Hilt. */
@Composable
fun TodayUpdateCard(state: UpdateState,update: ()->Unit,later: ()->Unit,notifications: Boolean = true,enableNotifications: ()->Unit = {}) {
    val rel = state.updateRelease() ?: return
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp,vertical = 6.dp),color = MaterialTheme.colorScheme.primaryContainer,shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp),verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Update available · ${rel.version}",fontWeight = FontWeight.Bold)
            when (state) {
                is UpdateState.Downloading -> {
                    LinearProgressIndicator(progress = { state.fraction },modifier = Modifier.fillMaxWidth())
                    Text("Downloading ${(state.fraction*100).toInt()}% · you can leave the app",style = MaterialTheme.typography.bodySmall)
                }
                is UpdateState.Installing -> Text("Confirm the update in Android's installer",style = MaterialTheme.typography.bodySmall)
                else -> {
                    if (state is UpdateState.Error) Text(state.message,style = MaterialTheme.typography.bodySmall)
                    else Text(if (state is UpdateState.Downloaded) "Verified download ready to install" else "Accept to download in the background, then install securely.",style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = update) { Text("Update") }
                        TextButton(onClick = later) { Text("Later") }
                    }
                }
            }
            if (!notifications) TextButton(onClick = enableNotifications) { Text("Enable update notifications") }
        }
    }
}

/** Foreground-only continuation: never tries to bypass Android background activity restrictions. */
@Composable
fun UpdateInstallHost(vm: UpdateViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accepted by vm.prefs.accepted.collectAsStateWithLifecycle()
    val confirmation by vm.manager.pendingConfirmation.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val phase by lifecycle.currentStateFlow.collectAsState()
    val context = LocalContext.current
    var permissionFor by remember { mutableStateOf<UpdateState.Downloaded?>(null) }
    LaunchedEffect(confirmation,phase) {
        val confirm = confirmation
        if (confirm != null && phase == Lifecycle.State.RESUMED) {
            if (guard("UpdateInstall","system confirmation") { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } == true) vm.manager.consumeConfirmation()
        }
    }
    LaunchedEffect(state,phase,accepted) {
        if (accepted == null) permissionFor = null
        val s = state as? UpdateState.Downloaded
        if (s != null && phase == Lifecycle.State.RESUMED && vm.prefs.acceptedRelease()?.key == s.release.key) {
            if (guard("UpdateInstall","permission") { vm.manager.canInstall() } == true) {
                permissionFor = null
                vm.manager.install(s.release,s.file)
            } else permissionFor = s
        }
    }
    permissionFor?.let {
        Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.large,tonalElevation = 12.dp,shadowElevation = 8.dp) {
                Column(Modifier.widthIn(max = 420.dp).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Allow installing updates",style = MaterialTheme.typography.titleLarge)
                    Text("Your download is verified. On the next Android screen, enable ‘Allow from this source’, then press Back. The installer will continue automatically. If you decline, your file stays ready for later.")
                    Button(onClick = { guard("UpdateInstall","unknown sources") { context.startActivity(vm.manager.unknownSourcesIntent()) } }) { Text("Open settings") }
                    TextButton(onClick = { vm.manager.postponeInstall(); permissionFor = null }) { Text("Later") }
                }
            }
        }
    }
}
