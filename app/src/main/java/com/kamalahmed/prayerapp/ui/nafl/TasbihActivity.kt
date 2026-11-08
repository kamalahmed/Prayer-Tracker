package com.kamalahmed.prayerapp.ui.nafl

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kamalahmed.prayerapp.core.safety.guard
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TasbihActivity : ComponentActivity() {
    private val vm: TasbihViewModel by viewModels()
    private val prefs by lazy { getSharedPreferences("tasbih",MODE_PRIVATE) }
    private var useVolume = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) vm.select(intent.getStringExtra("counterId"))
        useVolume = guard("Tasbih","volume preference") { prefs.getBoolean("volume",false) } ?: false
        setContent {
            PrayerTheme {
                val ui by vm.state.collectAsStateWithLifecycle()
                val error by com.kamalahmed.prayerapp.core.safety.AppErrors.latest.collectAsStateWithLifecycle()
                var haptics by remember { mutableStateOf(guard("Tasbih","haptics preference") { prefs.getBoolean("haptics",true) } ?: true) }
                var volume by remember { mutableStateOf(useVolume) }
                LaunchedEffect(haptics) {
                    vm.feedback.collect { target ->
                        guard("Tasbih","vibration") {
                            if (target) getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,80,60,160),-1))
                            else if (haptics) window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }
                }
                Surface(Modifier.fillMaxSize()) {
                    TasbihContent(ui,vm::select,{ vm.change(1) },{ vm.change(-1) },{ vm.change(0,true) },
                        haptics,{ haptics = it; guard("Tasbih","save haptics") { prefs.edit().putBoolean("haptics",it).apply() } },
                        volume,{ volume = it; useVolume = it; guard("Tasbih","save volume keys") { prefs.edit().putBoolean("volume",it).apply() } },{ finish() },error)
                }
            }
        }
    }
    override fun onKeyDown(keyCode: Int,event: KeyEvent?): Boolean {
        if (useVolume && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event?.repeatCount == 0) vm.change(1)
            return true
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun onKeyUp(keyCode: Int,event: KeyEvent?): Boolean =
        if (useVolume && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) true else super.onKeyUp(keyCode,event)
}

@Composable
fun TasbihContent(ui: TasbihUi?,select: (String)->Unit,plus: ()->Unit,minus: ()->Unit,reset: ()->Unit,
    haptics: Boolean,onHaptics: (Boolean)->Unit,volume: Boolean,onVolume: (Boolean)->Unit,back: ()->Unit,error: String? = null) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text("Tasbih",Modifier.weight(1f),style = MaterialTheme.typography.headlineMedium,fontWeight = FontWeight.Bold)
            TextButton(onClick = back) { Text("Back") }
        }
        if (error != null) Text(error,color = MaterialTheme.colorScheme.error,style = MaterialTheme.typography.bodySmall)
        if (ui == null) { Text("Loading counters…"); return@Column }
        if (ui.item == null) { Text("No enabled counters. Add or enable a counter in Streaks → Nafl → Customize worship."); return@Column }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ui.items.forEach { item -> FilterChip(selected = item.id == ui.item.id,onClick = { confirmReset = false; select(item.id) },label = { Text(item.name) }) }
        }
        Text("${ui.day} · daily total",style = MaterialTheme.typography.labelLarge)
        if (ui.item.dailyTarget > 0) {
            LinearProgressIndicator(progress = { (ui.count.toFloat()/ui.item.dailyTarget).coerceIn(0f,1f) },modifier = Modifier.fillMaxWidth())
            Text("${ui.count} / ${ui.item.dailyTarget}" + if (ui.count >= ui.item.dailyTarget) " · Target reached ✓" else " · daily target")
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 240.dp).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(32.dp))
            .clickable(onClick = plus).semantics { contentDescription = "Count ${ui.item.name}, ${ui.count} today. Tap to add one" },contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,modifier = Modifier.padding(24.dp)) {
                Text("${ui.count}",fontSize = 64.sp,fontWeight = FontWeight.Bold,color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("TAP ANYWHERE HERE  +1",style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = minus,modifier = Modifier.weight(1f),enabled = ui.count > 0) { Text("Undo / −1") }
            OutlinedButton(onClick = { confirmReset = true },modifier = Modifier.weight(1f),enabled = ui.session > 0) { Text("Reset session") }
        }
        Text("Session: ${ui.session} · Reset removes only this session's net taps, not widget taps or earlier counts. A new session starts after closing this screen or restarting the process.",style = MaterialTheme.typography.bodySmall)
        if (confirmReset) {
            Text("Remove ${ui.session} taps from this session? Daily history is otherwise kept.")
            Row { Button(onClick = { reset(); confirmReset = false }) { Text("Confirm reset") }; TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = haptics,onCheckedChange = onHaptics); Text("Tap haptics") }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = volume,onCheckedChange = onVolume); Text("Count with volume keys") }
        Text("Both volume keys add one while this screen is open. Targets give a distinct vibration. Counts are stored on your device, per day.",style = MaterialTheme.typography.bodySmall)
    }
}
