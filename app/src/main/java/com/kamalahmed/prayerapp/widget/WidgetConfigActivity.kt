package com.kamalahmed.prayerapp.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.kamalahmed.prayerapp.ui.theme.PrayerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Opened by the launcher when a widget is added (or reconfigured on Android 12+), from the in-app
 * Widget studio, and after a pin request. Edits the style of exactly one appWidgetId.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var styles: WidgetStyleStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val kind = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) null else WidgetKinds.kindOf(this, id)
        if (kind == null) { finish(); return }

        setContent {
            PrayerTheme {
                var style by remember { mutableStateOf<WidgetStyle?>(null) }
                var data by remember { mutableStateOf<WidgetData?>(null) }
                var isNew by remember { mutableStateOf(false) }
                var failed by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    try {
                        isNew = !styles.has(id)
                        style = com.kamalahmed.prayerapp.core.safety.guard(TAG, "style") { styles.get(id, kind) } ?: WidgetStyle.decode(null, kind)
                        data = widgetDeps(this@WidgetConfigActivity).loadWidgetData()
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal(TAG, "Widget setup failed", e)
                        failed = true
                    }
                }
                val s = style
                val d = data
                if (failed) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Text("Couldn't load the widget settings. Close and try again.")
                    }
                } else if (s == null || d == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    WidgetConfigScreen(
                        kind = kind, style = s, data = d, isNew = isNew,
                        onChange = { style = it },
                        onSave = { save(id, kind, it) },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    private fun save(id: Int, kind: WidgetKind, style: WidgetStyle) {
        lifecycleScope.launch(com.kamalahmed.prayerapp.core.safety.safeHandler(TAG)) {
            styles.save(id, style)
            com.kamalahmed.prayerapp.core.safety.guard(TAG, "update #$id") {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(id)
                WidgetKinds.widget(kind).update(this@WidgetConfigActivity, glanceId)
            }
            com.kamalahmed.prayerapp.core.safety.guard(TAG, "schedule") { WidgetUpdater.ensureScheduled(applicationContext) }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
    }

    private companion object { const val TAG = "WidgetConfig" }
}
