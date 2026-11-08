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
    @javax.inject.Inject lateinit var updater: com.kamalahmed.prayerapp.update.UpdateManager
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            PrayerTheme {
                androidx.compose.foundation.layout.Box {
                    PrayerAppRoot()
                    com.kamalahmed.prayerapp.update.UpdateInstallHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: android.content.Intent?) {
        com.kamalahmed.prayerapp.summary.SummaryNav.fromIntent(i)?.let { request ->
            i?.removeExtra(com.kamalahmed.prayerapp.summary.SummaryNav.KIND);i?.removeExtra(com.kamalahmed.prayerapp.summary.SummaryNav.DATE)
            com.kamalahmed.prayerapp.summary.SummaryNav.openRequested.value=request
        }
        i?.getStringExtra(EXTRA_ACCEPT_UPDATE)?.let { tag ->
            i.removeExtra(EXTRA_ACCEPT_UPDATE)
            updater.acceptNotification(tag)
        }
        if (i?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true) {
            i.removeExtra(EXTRA_OPEN_UPDATES)
            com.kamalahmed.prayerapp.update.UpdateNav.openRequested.value = true
        }
    }

    companion object { const val EXTRA_ACCEPT_UPDATE = "accept_update"; const val EXTRA_OPEN_UPDATES = "open_updates" }
}
