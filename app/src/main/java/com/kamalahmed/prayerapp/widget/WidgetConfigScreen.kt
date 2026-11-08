package com.kamalahmed.prayerapp.widget

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.kamalahmed.prayerapp.ui.common.CardGroup
import com.kamalahmed.prayerapp.ui.common.RowDivider
import com.kamalahmed.prayerapp.ui.common.SectionHeader
import com.kamalahmed.prayerapp.ui.common.SliderRow
import com.kamalahmed.prayerapp.ui.common.SwitchRow
import kotlin.math.roundToInt

fun previewSize(kind: WidgetKind): DpSize = when (kind) {
    WidgetKind.NEXT_SMALL -> DpSize(170.dp, 170.dp)
    WidgetKind.ALL_WIDE -> DpSize(320.dp, 140.dp)
    WidgetKind.NEXT_STREAK -> DpSize(290.dp, 165.dp)
    WidgetKind.TRACKER -> DpSize(320.dp, 140.dp)
    WidgetKind.LARGE -> DpSize(320.dp, 330.dp)
    WidgetKind.TASBIH -> DpSize(230.dp, 220.dp)
    WidgetKind.IQAMAH_TABLE -> DpSize(320.dp, 300.dp)
    WidgetKind.MINIMAL -> DpSize(300.dp, 64.dp)
}

/** Renders the real Glance widget composition to RemoteViews and shows it — a true live preview. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
fun WidgetPreview(kind: WidgetKind, style: WidgetStyle, data: WidgetData, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val size = previewSize(kind)
    var rv by remember { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(kind, style, data) {
        rv = runCatching {
            GlanceRemoteViews().compose(ctx, size) { WidgetContent(kind, style, data, interactive = false) }.remoteViews
        }.getOrNull()
    }
    AndroidView(
        factory = { FrameLayout(it) },
        update = { fl -> rv?.let { r -> fl.removeAllViews(); runCatching { fl.addView(r.apply(fl.context, fl)) } } },
        modifier = modifier.size(size.width, size.height).semantics { contentDescription = "Widget preview" },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WidgetConfigScreen(
    kind: WidgetKind,
    style: WidgetStyle,
    data: WidgetData,
    isNew: Boolean,
    onChange: (WidgetStyle) -> Unit,
    onSave: (WidgetStyle) -> Unit,
    onClose: () -> Unit,
) {
    var lightWallpaper by remember { mutableStateOf(false) }
    fun set(f: WidgetStyle.() -> WidgetStyle) = onChange(style.f().sanitized())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kind.label) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } },
                actions = { TextButton(onClick = { onChange(WidgetStyle.defaults(kind)) }) { Text("Reset") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                Button(onClick = { onSave(style) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(if (isNew) "  Add widget" else "  Save", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Live preview on a fake wallpaper so transparency is visible.
            val wall = if (lightWallpaper) listOf(Color(0xFFE9D8B8), Color(0xFF9CC5E8)) else listOf(Color(0xFF1B3B2F), Color(0xFF3A2A4F))
            Box(
                Modifier.fillMaxWidth().height(previewSize(kind).height + 40.dp)
                    .background(Brush.linearGradient(wall)).clickable { lightWallpaper = !lightWallpaper },
                contentAlignment = Alignment.Center,
            ) {
                WidgetPreview(kind, style, data)
                Text(
                    "Tap to switch wallpaper", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                )
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                if (kind == WidgetKind.TASBIH) {
                    SectionHeader("Counter item")
                    CardGroup {
                        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            data.counterItems.filter { it.isCounter && it.enabled }.forEach { item ->
                                FilterChip(selected = style.counterId == item.id, onClick = { set { copy(counterId = item.id) } }, label = { Text(item.name) })
                            }
                        }
                        Text("Add custom counters in Streaks → Nafl → Customize worship.", Modifier.padding(12.dp))
                    }
                } else if (kind != WidgetKind.IQAMAH_TABLE) {
                    SectionHeader("Times to show")
                    CardGroup {
                        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WidgetTimeMode.entries.forEach { mode ->
                                FilterChip(selected = style.timeMode == mode, onClick = { set { copy(timeMode = mode) } }, label = { Text(mode.label) })
                            }
                        }
                    }
                }
                SectionHeader("Theme")
                CardGroup {
                    FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetTheme.entries.forEach { t ->
                            FilterChip(selected = style.theme == t, onClick = {
                                set { copy(theme = t, opacity = if (t == WidgetTheme.MINIMAL) 0 else if (opacity == 0) 80 else opacity) }
                            }, label = { Text(t.label) })
                        }
                    }
                    RowDivider()
                    Text("Accent", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                    FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentPalette.forEach { c ->
                            val selected = style.accent == c.argb
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(c.argb))
                                    .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                    .clickable { set { copy(accent = c.argb) } }
                                    .semantics { contentDescription = "${c.name} accent" + if (selected) ", selected" else "" },
                            )
                        }
                    }
                    RowDivider()
                    SliderRow("Opacity", style.opacity.toFloat(), 0f..100f, "${style.opacity}%") { set { copy(opacity = it.roundToInt()) } }
                }

                SectionHeader("Layout")
                CardGroup {
                    SliderRow("Corner radius", style.corner.toFloat(), 0f..40f, "${style.corner} dp") { set { copy(corner = it.roundToInt()) } }
                    SliderRow("Vertical padding (top/bottom)", style.padV.toFloat(), 0f..32f, "${style.padV} dp") { set { copy(padV = it.roundToInt()) } }
                    SliderRow("Horizontal padding (start/end)", style.padH.toFloat(), 0f..32f, "${style.padH} dp") { set { copy(padH = it.roundToInt()) } }
                    SliderRow("Spacing between items", style.spacing.toFloat(), 0f..24f, "${style.spacing} dp") { set { copy(spacing = it.roundToInt()) } }
                    RowDivider()
                    Text("Position in cell", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetAlign.entries.forEach { a ->
                            FilterChip(selected = style.align == a, onClick = { set { copy(align = a) } }, label = { Text(a.label) })
                        }
                    }
                    SliderRow("Box size", style.boxScale, 0.6f..1.5f, pct(style.boxScale)) { set { copy(boxScale = step(it)) } }
                }

                SectionHeader("Text & icons")
                CardGroup {
                    SliderRow("Text size", style.textScale, 0.6f..1.8f, pct(style.textScale)) { set { copy(textScale = step(it)) } }
                    SliderRow("Icon size", style.iconScale, 0.5f..1.8f, pct(style.iconScale)) { set { copy(iconScale = step(it)) } }
                    RowDivider()
                    Text("Font weight", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetWeight.entries.forEach { w ->
                            FilterChip(selected = style.weight == w, onClick = { set { copy(weight = w) } }, label = { Text(w.label) })
                        }
                    }
                }

                SectionHeader("Show")
                CardGroup {
                    SwitchRow("Hijri date", style.showHijri) { v -> set { copy(showHijri = v) } }
                    RowDivider()
                    SwitchRow("Countdown", style.showCountdown) { v -> set { copy(showCountdown = v) } }
                    RowDivider()
                    SwitchRow("Icons", style.showIcons) { v -> set { copy(showIcons = v) } }
                    RowDivider()
                    SwitchRow("am / pm", style.showAmPm, subtitle = "12-hour clock only") { v -> set { copy(showAmPm = v) } }
                    if (kind == WidgetKind.ALL_WIDE || kind == WidgetKind.LARGE) {
                        RowDivider()
                        SwitchRow("Sunrise", style.showSunrise) { v -> set { copy(showSunrise = v) } }
                        RowDivider()
                        SwitchRow("Islamic midnight", style.showMidnight) { v -> set { copy(showMidnight = v) } }
                    }
                    if (kind == WidgetKind.ALL_WIDE || kind == WidgetKind.NEXT_STREAK || kind == WidgetKind.TRACKER) {
                        RowDivider()
                        SwitchRow("Always show full layout", style.alwaysFull,
                            subtitle = "All prayers with icons and times, even when the launcher reports a small size") { v -> set { copy(alwaysFull = v) } }
                    }
                }

                SectionHeader("Format")
                CardGroup {
                    TriRow("24-hour clock", style.clock24) { set { copy(clock24 = it) } }
                    RowDivider()
                    TriRow("Abbreviated names", style.abbreviations) { set { copy(abbreviations = it) } }
                }
            }
        }
    }
}

private fun pct(f: Float) = "${(f * 100).roundToInt()}%"
private fun step(f: Float) = (f * 20).roundToInt() / 20f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriRow(title: String, value: Tri, onChange: (Tri) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tri.entries.forEach { t ->
                FilterChip(selected = value == t, onClick = { onChange(t) }, label = { Text(t.label) }, shape = RoundedCornerShape(8.dp))
            }
        }
    }
}
