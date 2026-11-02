package com.kamalahmed.prayerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kamalahmed.prayerapp.ui.nav.PrayerAppRoot
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            PrayerTheme {
                PrayerAppRoot()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: android.content.Intent?) {
        if (i?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true) {
            i.removeExtra(EXTRA_OPEN_UPDATES)
            com.kamalahmed.prayerapp.update.UpdateNav.openRequested.value = true
        }
    }

    companion object { const val EXTRA_OPEN_UPDATES = "open_updates" }
}
