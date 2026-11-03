package com.kamalahmed.prayerapp.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.ListRow
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SectionHeader
import com.kamalahmed.prayerapp.ui.common.SubScreen
import com.kamalahmed.prayerapp.widget.WidgetConfigActivity
import com.kamalahmed.prayerapp.widget.WidgetData
import com.kamalahmed.prayerapp.widget.WidgetKind
import com.kamalahmed.prayerapp.widget.WidgetKinds
import com.kamalahmed.prayerapp.widget.WidgetPreview
import com.kamalahmed.prayerapp.widget.WidgetStyle
import com.kamalahmed.prayerapp.widget.loadWidgetData
import com.kamalahmed.prayerapp.widget.widgetDeps

/** Settings → Widgets: gallery of all widget kinds, pin to home screen, and edit placed widgets. */
@Composable
fun WidgetStudioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<WidgetData?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        data = com.kamalahmed.prayerapp.core.safety.guard("WidgetStudio", "loadWidgetData") { widgetDeps(ctx).loadWidgetData() }
            ?: data.also { com.kamalahmed.prayerapp.core.safety.AppErrors.report("Couldn't load widget previews. Try again later.") }
    }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val pinSupported = remember { AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported }

    SubScreen("Widget studio", onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                "Add a widget from your home screen (long-press → Widgets → Prayer App) or with the button below. " +
                    "Each widget has its own theme, transparency, sizes and spacing.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            WidgetKind.entries.forEach { kind ->
                val placed = remember(refresh) { WidgetKinds.ids(ctx, kind).toList() }
                SectionHeader(kind.label)
                CardGroup {
                    data?.let { d ->
                        Box(
                            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1B3B2F), Color(0xFF3A2A4F))))
                                .horizontalScroll(rememberScrollState()).padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { WidgetPreview(kind, WidgetStyle.defaults(kind), d, Modifier.padding(horizontal = 16.dp)) }
                    }
                    Text(kind.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                    placed.forEachIndexed { i, id ->
                        RowDivider()
                        ListRow("Placed widget ${i + 1}", subtitle = "Customize", leading = Icons.Filled.Tune, onClick = { openConfig(ctx, id) })
                    }
                    if (pinSupported) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                            FilledTonalButton(onClick = { pin(ctx, kind) }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Text(" Add to home screen")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openConfig(ctx: Context, id: Int) {
    ctx.startActivity(
        Intent(ctx, WidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun pin(ctx: Context, kind: WidgetKind) {
    // The launcher fills in EXTRA_APPWIDGET_ID on the callback, so it must be mutable.
    val callback = PendingIntent.getActivity(
        ctx, 7_000 + kind.ordinal,
        Intent(ctx, WidgetConfigActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val ok = AppWidgetManager.getInstance(ctx).requestPinAppWidget(WidgetKinds.component(ctx, kind), null, callback)
    if (!ok) Toast.makeText(ctx, "Your launcher doesn't support adding widgets from apps", Toast.LENGTH_LONG).show()
}
