package com.kamalahmed.prayerapp.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.ui.theme.Gold
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import java.time.Instant
import java.time.ZoneId

/** Lock-screen alarm UI shown via full-screen intent (optional, off by default). */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } }
        val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        val iqamah = intent.getBooleanExtra(EXTRA_IQAMAH, false)
        val is24 = android.text.format.DateFormat.is24HourFormat(this)
        setContent {
            PrayerTheme {
                val playing by AdhanService.playing.collectAsState()
                val seen = remember { mutableStateOf(false) }
                LaunchedEffect(playing) {
                    if (playing != null) seen.value = true
                    else if (seen.value) finish()
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.NotificationsActive, null, tint = Gold, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(24.dp))
                        Text(prayer?.displayName ?: "Prayer", fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Text(
                            TimeFormat.time(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()), is24),
                            fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(if (iqamah) "Iqamah is coming up" else "It's time for prayer", color = Gold, modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(48.dp))
                        Row {
                            if (!iqamah) {
                                OutlinedButton(onClick = { startService(AdhanService.snoozeIntent(this@AlarmActivity)); finish() }, modifier = Modifier.height(56.dp)) {
                                    Text("Snooze")
                                }
                                Spacer(Modifier.width(16.dp))
                            }
                            Button(
                                onClick = { startService(AdhanService.stopIntent(this@AlarmActivity)); finish() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(56.dp),
                            ) { Text("Stop") }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_TIME = "time"
        private const val EXTRA_IQAMAH = "iqamah"
        fun intent(ctx: Context, prayer: Prayer, time: Long, iqamah: Boolean = false) = Intent(ctx, AlarmActivity::class.java)
            .putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_TIME, time).putExtra(EXTRA_IQAMAH, iqamah)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
