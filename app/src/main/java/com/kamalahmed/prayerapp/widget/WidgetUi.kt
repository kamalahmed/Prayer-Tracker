package com.kamalahmed.prayerapp.widget

import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.R
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.StreakCalculator
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Resolved style helpers shared by all widget layouts. */
class WidgetUiState(val style: WidgetStyle, val data: WidgetData, val interactive: Boolean, val scale: Float = 1f) {
    val pal = WidgetPalette.of(style)
    val use24h = style.use24h(data.appUse24h)
    val abbr = style.abbr(data.appAbbr)
    private val fw = when (style.weight) {
        WidgetWeight.NORMAL -> FontWeight.Normal
        WidgetWeight.MEDIUM -> FontWeight.Medium
        WidgetWeight.BOLD -> FontWeight.Bold
    }

    fun icon(base: Int): Dp = (base * style.iconScale * scale).dp
    fun box(base: Int): Dp = (base * style.boxScale * scale).dp
    val gap: Dp get() = (style.spacing * scale).dp

    fun ts(size: Float, color: Int = pal.text, bold: Boolean = false) = TextStyle(
        color = ColorProvider(Color(color)),
        fontSize = (size * style.textScale * scale).sp,
        fontWeight = if (bold) FontWeight.Bold else fw,
    )

    fun time(t: ZonedDateTime) = TimeFormat.time(t, use24h, style.showAmPm)
    val next: com.kamalahmed.prayerapp.core.prayer.PrayerEvent get() = if (style.timeMode == WidgetTimeMode.IQAMAH) data.iqamahEvents.firstOrNull { it.time > data.now } ?: data.next else data.next
    fun iqamahForNext(): ZonedDateTime? = data.iqamahEvents.firstOrNull { it.prayer == next.prayer && it.time >= next.time }?.time
    fun rowTime(r: WidgetRow): String = when (style.timeMode) {
        WidgetTimeMode.START -> time(r.time)
        WidgetTimeMode.IQAMAH -> r.iqamah?.let(::time) ?: "—"
        WidgetTimeMode.BOTH -> "${time(r.time)}\nIqamah ${r.iqamah?.let(::time) ?: "—"}"
    }
    fun label(p: Prayer) = p.label(abbr)
    val progress: Float get() {
        if (style.timeMode != WidgetTimeMode.IQAMAH) return data.progress
        val previous = data.iqamahEvents.lastOrNull { it.time <= data.now }?.time ?: return 0f
        val total = next.time.toEpochSecond() - previous.toEpochSecond()
        return if (total <= 0) 0f else ((data.now.toEpochSecond() - previous.toEpochSecond()).toFloat()/total).coerceIn(0f,1f)
    }
    fun passed(r: WidgetRow) = if (style.timeMode == WidgetTimeMode.IQAMAH) (r.iqamah ?: r.time) <= data.now else r.passed
    fun isNext(r: WidgetRow) = r.prayer == next.prayer && (if (style.timeMode == WidgetTimeMode.IQAMAH) r.iqamah else r.time) == next.time
    fun rowLabel(r: WidgetRow) = if (abbr) r.abbr else r.name

    @Composable
    fun compact(kind: WidgetKind): Boolean {
        val size = LocalSize.current
        return WidgetLayout.compact(kind, size.width.value, size.height.value, style)
    }

    /** Rows for list layouts honouring the Sunrise / Midnight toggles. */
    fun listRows(): List<WidgetRow> = buildList {
        // Keep today's Isha visible until its iqamah when adhan lists already rolled to tomorrow.
        val rows = if (style.timeMode != WidgetTimeMode.START && data.listDate > data.today && data.todayIqamah.values.any { it > data.now })
            data.todayTimes.entries.sortedBy { it.value }.map { (p,t) -> WidgetRow(p,p.displayName,p.abbr,t,WidgetData.icon(p),false,t <= data.now,data.todayIqamah[p]) }
            else data.rows
        addAll(rows.filter { it.prayer != Prayer.SUNRISE || style.showSunrise })
        if (style.showMidnight) add(data.midnight)
    }
}

@Composable
fun WidgetContent(kind: WidgetKind, style: WidgetStyle, data: WidgetData, interactive: Boolean = true) {
    val safe = style.sanitized()
    val size = LocalSize.current
    val scale = WidgetLayout.fitScale(kind, size.width.value, size.height.value, safe) * safe.contentScale
    val ui = WidgetUiState(safe, data, interactive, scale)
    val dims = WidgetDims.of(safe)
    // The launcher owns the cell; fill it by default. Wrap and margin are explicit per-instance choices.
    var root = GlanceModifier.fillMaxSize().padding(safe.outerMargin.dp)
    if (interactive && kind != WidgetKind.TASBIH) root = root.clickable(actionStartActivity<MainActivity>())
    val align = when (style.align) {
        WidgetAlign.TOP -> Alignment.TopCenter
        WidgetAlign.CENTER -> Alignment.Center
        WidgetAlign.BOTTOM -> Alignment.BottomCenter
    }
    Box(modifier = root, contentAlignment = align) {
        val panel = (if (safe.fillPanel) GlanceModifier.fillMaxSize() else GlanceModifier.fillMaxWidth()).then(backgroundModifier(ui))
            .padding(start = dims.padStart.dp, end = dims.padEnd.dp, top = dims.padTop.dp, bottom = dims.padBottom.dp)
        Box(modifier = panel, contentAlignment = align) {
            when (kind) {
                WidgetKind.NEXT_SMALL -> NextSmall(ui)
                WidgetKind.ALL_WIDE -> AllWide(ui)
                WidgetKind.NEXT_STREAK -> NextStreak(ui)
                WidgetKind.TRACKER -> Tracker(ui)
                WidgetKind.LARGE -> Large(ui)
                WidgetKind.TASBIH -> TasbihContent(ui)
                WidgetKind.IQAMAH_TABLE -> IqamahTable(ui)
                WidgetKind.MINIMAL -> Minimal(ui)
            }
        }
    }
}

@android.annotation.SuppressLint("LocalContextResourcesRead") // Glance context, not a Compose UI context
@Composable
private fun backgroundModifier(ui: WidgetUiState): GlanceModifier {
    val st = ui.style
    // Opacity 0: no background layer at all (no colour, bitmap, corner clip or tint).
    if (!WidgetDims.of(st).hasBackground) return GlanceModifier
    if (st.theme != WidgetTheme.GRADIENT && Build.VERSION.SDK_INT >= 31) {
        return GlanceModifier.background(ColorProvider(Color(ui.pal.bg))).cornerRadius(st.corner.dp)
    }
    val size = LocalSize.current
    val density = LocalContext.current.resources.displayMetrics.density
    if (Build.VERSION.SDK_INT >= 31) {
        // Gradient: square bitmap stretched to the panel, rounded by the system clip.
        val bmp = WidgetBitmaps.background(size.width.value, 80f, density, ui.pal, 0, true)
        return GlanceModifier.background(ImageProvider(bmp), ContentScale.FillBounds).cornerRadius(st.corner.dp)
    }
    val bmp = WidgetBitmaps.background(size.width.value, 80f, density, ui.pal, st.corner, st.theme == WidgetTheme.GRADIENT)
    return GlanceModifier.background(ImageProvider(bmp), ContentScale.FillBounds)
}

// ---------- building blocks ----------

@Composable
private fun Icon(res: Int, size: Dp, color: Int, description: String? = null) {
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(ColorProvider(Color(color))),
    )
}

/** Live countdown: a RemoteViews Chronometer ticks on its own without widget updates. */
@Composable
private fun Countdown(ui: WidgetUiState, target: ZonedDateTime, size: Float, color: Int, prefix: String = "") {
    val ctx = LocalContext.current
    val layout = when (ui.style.weight) {
        WidgetWeight.NORMAL -> R.layout.widget_chrono_normal
        WidgetWeight.MEDIUM -> R.layout.widget_chrono_medium
        WidgetWeight.BOLD -> R.layout.widget_chrono_bold
    }
    val rv = RemoteViews(ctx.packageName, layout).apply {
        val base = SystemClock.elapsedRealtime() + (target.toInstant().toEpochMilli() - if (ui.interactive) System.currentTimeMillis() else ui.data.now.toInstant().toEpochMilli())
        setChronometer(R.id.chrono, base, if (prefix.isEmpty()) null else "$prefix%s", true)
        setChronometerCountDown(R.id.chrono, true)
        setTextColor(R.id.chrono, color)
        setTextViewTextSize(R.id.chrono, TypedValue.COMPLEX_UNIT_SP, size * ui.style.textScale * ui.scale)
    }
    AndroidRemoteViews(rv)
}

@Composable
private fun HijriBadge(ui: WidgetUiState) {
    var bm = GlanceModifier.size(ui.box(32))
    if (ui.pal.badge != 0) bm = bm.background(
        ImageProvider(R.drawable.w_pill), ContentScale.FillBounds, ColorFilter.tint(ColorProvider(Color(ui.pal.badge))),
    )
    Box(
        modifier = bm,
        contentAlignment = Alignment.Center,
    ) {
        Text("${ui.data.hijri.day}", style = ui.ts(15f, ui.pal.gold, bold = true), maxLines = 1)
    }
}

private fun highlight(ui: WidgetUiState): GlanceModifier =
    // A tint over the opaque white pill blends toward white instead of preserving alpha.
    // A direct ARGB background keeps muted timing text legible on dark themes.
    GlanceModifier.background(ColorProvider(Color(ui.pal.highlight))).cornerRadius(10.dp)

private fun statusText(s: PrayerStatus?) = when (s) {
    PrayerStatus.ON_TIME -> "prayed on time"
    PrayerStatus.LATE -> "prayed late"
    PrayerStatus.MISSED -> "missed"
    null -> "not marked"
}

/** Tap-to-mark circle for one of today's prayers. */
@Composable
private fun StatusDot(ui: WidgetUiState, p: Prayer, size: Dp) {
    val st = ui.data.todayStatus[p]
    val prayed = StreakCalculator.prayed(st)
    val markable = p in ui.data.markable
    val color = when {
        st == PrayerStatus.ON_TIME -> ui.pal.accent
        st == PrayerStatus.LATE -> ui.pal.gold
        st == PrayerStatus.MISSED -> ui.pal.danger
        markable -> ui.pal.sub
        else -> WidgetPalette.withAlpha(ui.pal.text, 0.25f)
    }
    var m = GlanceModifier.size(size)
    if (ui.interactive && markable) {
        m = m.clickable(
            actionRunCallback<ToggleAction>(
                actionParametersOf(ToggleAction.KeyPrayer to p.name, ToggleAction.KeyDate to ui.data.today.toString()),
            ),
        )
    }
    Box(modifier = m, contentAlignment = Alignment.Center) {
        Icon(if (prayed) R.drawable.w_dot else R.drawable.w_ring, size, color, "${p.displayName}: ${statusText(st)}")
        if (prayed) {
            Icon(R.drawable.w_check, size * 0.62f, if (ui.style.theme == WidgetTheme.LIGHT) 0xFFFFFFFF.toInt() else 0xFF101412.toInt())
        }
    }
}

@Composable
private fun Streak(ui: WidgetUiState, size: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(R.drawable.w_flame, ui.icon((size * 0.8f).toInt()), ui.pal.gold, "Streak")
        Spacer(GlanceModifier.width(3.dp))
        Text("${ui.data.streakDays}", style = ui.ts(size, ui.pal.gold, bold = true), maxLines = 1)
    }
}

// ---------- layouts ----------

@Composable
private fun NextSmall(ui: WidgetUiState) {
    val d = ui.data
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ui.style.showIcons) {
                Icon(WidgetData.icon(ui.next.prayer), ui.icon(18), ui.pal.accent)
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(ui.next.prayer.displayName, style = ui.ts(15f), maxLines = 1)
        }
        Text(ui.time(ui.next.time), style = ui.ts(30f, bold = true), maxLines = 1, modifier = GlanceModifier.padding(top = ui.gap))
        NextIqamah(ui)
        if (ui.style.showCountdown) {
            Box(GlanceModifier.padding(top = ui.gap / 2)) { Countdown(ui, ui.next.time, 13f, ui.pal.accent, "in ") }
        }
        if (ui.style.showHijri) {
            Text("${d.hijri.day} ${d.hijri.monthName}", style = ui.ts(11f, ui.pal.gold), maxLines = 1, modifier = GlanceModifier.padding(top = ui.gap))
        }
    }
}

@Composable
private fun AllWide(ui: WidgetUiState) {
    val d = ui.data
    val compact = ui.compact(WidgetKind.ALL_WIDE)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (!compact) Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ui.next.prayer.displayName, style = ui.ts(16f, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(8.dp))
            Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterStart) {
                if (ui.style.showCountdown) Countdown(ui, ui.next.time, 13f, ui.pal.sub, "in ")
                else Text(ui.time(ui.next.time), style = ui.ts(13f, ui.pal.sub), maxLines = 1)
            }
            if (ui.style.showHijri) HijriBadge(ui)
        }
        if (!compact) Spacer(GlanceModifier.height(ui.gap))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ui.listRows().take(WidgetLayout.cellCount(WidgetKind.ALL_WIDE, ui.listRows().size)).forEach { r -> WideCell(ui, r, showIcon = ui.style.showIcons && !compact) }
        }
    }
}

@Composable
private fun RowScope.WideCell(ui: WidgetUiState, r: WidgetRow, showIcon: Boolean) {
    val c = if (ui.isNext(r)) ui.pal.accent else ui.pal.sub
    var m = GlanceModifier.defaultWeight().padding(vertical = ui.gap / 2)
    if (ui.isNext(r) && ui.pal.highlight != 0) m = m.then(highlight(ui))
    Column(modifier = m, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showIcon) {
            Icon(r.icon, ui.icon(16), c)
            Spacer(GlanceModifier.height((ui.style.spacing / 3).dp))
        }
        Text(ui.rowLabel(r), style = ui.ts(11f, c), maxLines = 1)
        RowTimes(ui, r, 12f, if (ui.isNext(r)) ui.pal.accent else ui.pal.text)
    }
}

/** Separate text nodes keep each primary time aligned, with a muted secondary iqamah line. */
@Composable
private fun RowTimes(ui: WidgetUiState, r: WidgetRow, size: Float, color: Int = ui.pal.text, inlineLabel: Boolean = false) {
    val primary = if (ui.style.timeMode == WidgetTimeMode.IQAMAH) r.iqamah?.let(ui::time) ?: "—" else ui.time(r.time)
    Text(primary, GlanceModifier.fillMaxWidth(), style = ui.ts(size, color).copy(textAlign = TextAlign.Center), maxLines = 1)
    if (ui.style.timeMode == WidgetTimeMode.BOTH) {
        if (!inlineLabel) Text("Iqamah", GlanceModifier.fillMaxWidth(), style = ui.ts(size * 0.68f, ui.pal.sub).copy(textAlign = TextAlign.Center), maxLines = 1)
        Text((if (inlineLabel) "Iqamah " else "") + (r.iqamah?.let(ui::time) ?: "—"), GlanceModifier.fillMaxWidth(), style = ui.ts(size * 0.85f, ui.pal.sub).copy(textAlign = TextAlign.Center), maxLines = 1)
    }
}

@Composable
private fun TickTiming(ui: WidgetUiState, p: Prayer, position: TickTimes) {
    if (ui.style.tickTimes == position) ui.data.todayTimes[p]?.let { at ->
        RowTimes(ui, WidgetRow(p, p.displayName, p.abbr, at, 0, false, false, ui.data.todayIqamah[p]), 9f)
    }
}

@Composable
private fun NextStreak(ui: WidgetUiState) {
    val d = ui.data
    val compact = ui.compact(WidgetKind.NEXT_STREAK)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ui.style.showIcons) {
                        Icon(WidgetData.icon(ui.next.prayer), ui.icon(16), ui.pal.accent)
                        Spacer(GlanceModifier.width(5.dp))
                    }
                    Text(ui.next.prayer.displayName, style = ui.ts(14f), maxLines = 1)
                }
                Text(ui.time(ui.next.time), style = ui.ts(26f, bold = true), maxLines = 1)
                NextIqamah(ui)
                if (ui.style.showCountdown) Countdown(ui, ui.next.time, 12f, ui.pal.accent, "in ")
            }
            Column(horizontalAlignment = Alignment.End) {
                Streak(ui, 24f)
                Text(if (d.streakDays == 1) "day streak" else "days streak", style = ui.ts(10f, ui.pal.sub), maxLines = 1)
                if (ui.style.showHijri) Text("${d.hijri.day} ${d.hijri.monthName}", style = ui.ts(10f, ui.pal.gold), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(ui.gap))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Prayer.obligatory.forEach { p ->
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TickTiming(ui, p, TickTimes.ABOVE)
                    StatusDot(ui, p, ui.box(24))
                    TickTiming(ui, p, TickTimes.BELOW)
                    if (!compact) Text(p.abbr, style = ui.ts(9f, ui.pal.sub), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Tracker(ui: WidgetUiState) {
    val d = ui.data
    val compact = ui.compact(WidgetKind.TRACKER)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = ui.ts(14f, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            Text("${d.prayedToday}/5", style = ui.ts(12f, ui.pal.sub), maxLines = 1)
            if (ui.style.showHijri) {
                Spacer(GlanceModifier.width(8.dp))
                Text("${d.hijri.day} ${d.hijri.monthName}", style = ui.ts(11f, ui.pal.gold), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            Streak(ui, 15f)
        }
        Spacer(GlanceModifier.height(ui.gap))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Prayer.obligatory.forEach { p ->
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TickTiming(ui, p, TickTimes.ABOVE)
                    StatusDot(ui, p, ui.box(if (compact) 26 else 32))
                    TickTiming(ui, p, TickTimes.BELOW)
                    Text(ui.label(p), style = ui.ts(10f, ui.pal.sub), maxLines = 1, modifier = GlanceModifier.padding(top = (ui.style.spacing / 3).dp))
                }
            }
        }
    }
}


@android.annotation.SuppressLint("LocalContextResourcesRead")
@Composable
private fun Large(ui: WidgetUiState) {
    val d = ui.data
    val ctx = LocalContext.current
    val ringDp = ui.box(108)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(d.today.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())), style = ui.ts(14f, bold = true), maxLines = 1)
                if (ui.style.showHijri) Text(d.hijri.format(), style = ui.ts(12f, ui.pal.gold), maxLines = 1)
            }
            Text(d.city, style = ui.ts(11f, ui.pal.sub), maxLines = 1)
        }
        Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = ui.gap), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = GlanceModifier.size(ringDp), contentAlignment = Alignment.Center) {
                val ring = WidgetBitmaps.ring(
                    ringDp.value, ctx.resources.displayMetrics.density, ui.progress,
                    WidgetPalette.withAlpha(ui.pal.text, 0.12f), ui.pal.accent, 7f * ui.style.boxScale,
                )
                Image(ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.size(ringDp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.label(ui.next.prayer), style = ui.ts(12f, ui.pal.sub), maxLines = 1)
                    Text(ui.time(ui.next.time), style = ui.ts(16f, bold = true), maxLines = 1)
                    NextIqamah(ui)
                    if (ui.style.showCountdown) Countdown(ui, ui.next.time, 11f, ui.pal.accent)
                }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Streak(ui, 24f)
                Text("day streak · best ${d.bestDays}", style = ui.ts(11f, ui.pal.sub), maxLines = 1)
                Row(modifier = GlanceModifier.padding(top = ui.gap)) {
                    Prayer.obligatory.forEach { p ->
                        Box(GlanceModifier.padding(end = 4.dp)) { StatusDot(ui, p, ui.box(22)) }
                    }
                }
            }
        }
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            ui.listRows().take(WidgetLayout.cellCount(WidgetKind.ALL_WIDE, ui.listRows().size)).forEach { r -> LargeRow(ui, r) }
        }
    }
}

@Composable
private fun LargeRow(ui: WidgetUiState, r: WidgetRow) {
    var m = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = (ui.style.spacing / 2).dp)
    if (ui.isNext(r) && ui.pal.highlight != 0) m = m.then(highlight(ui))
    val c = when {
        ui.isNext(r) -> ui.pal.accent
        ui.passed(r) -> ui.pal.sub
        else -> ui.pal.text
    }
    Row(modifier = m, verticalAlignment = Alignment.CenterVertically) {
        if (ui.style.showIcons) {
            Icon(r.icon, ui.icon(16), if (ui.isNext(r)) ui.pal.accent else ui.pal.sub)
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(ui.rowLabel(r), style = ui.ts(13f, c), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Column(GlanceModifier.width(ui.box(110)), horizontalAlignment = Alignment.CenterHorizontally) { RowTimes(ui, r, 12f, c, inlineLabel = true) }
    }
}

@Composable
private fun Minimal(ui: WidgetUiState) {
    val d = ui.data
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ui.style.showIcons) {
                Icon(WidgetData.icon(ui.next.prayer), ui.icon(16), ui.pal.accent)
                Spacer(GlanceModifier.width(6.dp))
            }
            Text("${ui.label(ui.next.prayer)} ${if (ui.style.timeMode == WidgetTimeMode.IQAMAH) "Iqamah " else ""}${ui.time(ui.next.time)}", style = ui.ts(14f), maxLines = 2)
            if (ui.style.showCountdown) {
                Text("  ·  ", style = ui.ts(14f, ui.pal.sub), maxLines = 1)
                Countdown(ui, ui.next.time, 14f, ui.pal.sub)
            }
        }
        NextIqamah(ui)
        if (ui.style.showHijri) Text(d.hijri.format(), style = ui.ts(11f, ui.pal.gold), maxLines = 1)
    }
}

@Composable
private fun NextIqamah(ui: WidgetUiState) {
    if (ui.style.timeMode == WidgetTimeMode.BOTH) Text("Iqamah ${ui.iqamahForNext()?.let(ui::time) ?: "—"}", style = ui.ts(11f,ui.pal.sub), maxLines = 1)
    else if (ui.style.timeMode == WidgetTimeMode.IQAMAH) Text("Iqamah time", style = ui.ts(10f,ui.pal.sub), maxLines = 1)
}

@Composable
private fun IqamahTable(ui: WidgetUiState) {
    Column(GlanceModifier.fillMaxWidth()) {
        Text("Adhan + Iqamah", style = ui.ts(16f,bold = true), maxLines = 1)
        Text(ui.data.listDate.toString(), style = ui.ts(10f,ui.pal.sub))
        Row(GlanceModifier.fillMaxWidth().padding(vertical = ui.gap)) {
            Text("Prayer", GlanceModifier.defaultWeight(), style = ui.ts(11f,ui.pal.sub).copy(textAlign = TextAlign.Center), maxLines = 1)
            Text("Adhan", GlanceModifier.defaultWeight(), style = ui.ts(11f,ui.pal.sub).copy(textAlign = TextAlign.Center), maxLines = 1)
            Text("Iqamah", GlanceModifier.defaultWeight(), style = ui.ts(11f,ui.pal.sub).copy(textAlign = TextAlign.Center), maxLines = 1)
        }
        ui.listRows().filter { it.prayer?.isPrayer == true }.forEach { row ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = ui.gap)) {
                Text(ui.rowLabel(row), GlanceModifier.defaultWeight(), style = ui.ts(12f).copy(textAlign = TextAlign.Center), maxLines = 1)
                Text(ui.time(row.time), GlanceModifier.defaultWeight(), style = ui.ts(12f).copy(textAlign = TextAlign.Center), maxLines = 1)
                Text(row.iqamah?.let(ui::time) ?: "—", GlanceModifier.defaultWeight(), style = ui.ts(12f,ui.pal.sub).copy(textAlign = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

@Composable
private fun TasbihContent(ui: WidgetUiState) {
    val item = ui.data.counterItems.firstOrNull { it.id == ui.style.counterId && it.isCounter && it.enabled }
    val count = ui.data.counterLogs.firstOrNull { it.itemId == item?.id && it.date == ui.data.now.withZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate().toString() }?.count ?: 0
    val size = LocalSize.current
    val wide = WidgetLayout.wideCounter(size.width.value, size.height.value)
    var tap = if (ui.style.fillPanel) GlanceModifier.fillMaxSize() else GlanceModifier.fillMaxWidth()
    if (ui.interactive && item != null) tap = tap.clickable(actionRunCallback<TasbihAction>(actionParametersOf(TasbihAction.Delta to 1)))
    val target = if ((item?.dailyTarget ?: 0) > 0) " / ${item!!.dailyTarget}" else ""
    if (wide) {
        Row(tap, verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item?.name ?: "Select a counter", style = ui.ts(14f, bold = true), maxLines = 1)
                Text("Today · tap +1", style = ui.ts(9f, ui.pal.sub), maxLines = 1)
            }
            val countText = "$count$target"
            Text(countText, GlanceModifier.defaultWeight(), style = ui.ts(minOf(25f, 145f / countText.length), ui.pal.accent, true).copy(textAlign = TextAlign.Center), maxLines = 1)
            CounterActions(ui, item != null, wide = true)
        }
    } else {
        Column(tap, horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Text(item?.name ?: "Select a counter", style = ui.ts(15f, bold = true), maxLines = 1)
            Text("Today · tap +1", style = ui.ts(9f, ui.pal.sub), maxLines = 1)
            Text("$count", style = ui.ts(38f, ui.pal.accent, true), maxLines = 1)
            Text(if (target.isNotEmpty()) "Target ${item!!.dailyTarget}" else "No target", style = ui.ts(10f, ui.pal.sub), maxLines = 1)
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                CounterActions(ui, item != null, wide = false)
            }
        }
    }
}

@Composable
private fun CounterActions(ui: WidgetUiState, selected: Boolean, wide: Boolean) {
    var minus = GlanceModifier.width(ui.box(if (wide) 36 else 54)).height(ui.box(36))
    if (ui.interactive && selected) minus = minus.clickable(actionRunCallback<TasbihAction>(actionParametersOf(TasbihAction.Delta to -1)))
    Box(minus, contentAlignment = Alignment.Center) { Text("−1", style = ui.ts(16f)) }
    var open = GlanceModifier.width(ui.box(if (wide) 40 else 54)).height(ui.box(36))
    if (ui.interactive) open = open.clickable(actionStartActivity<com.kamalahmed.prayerapp.ui.nafl.TasbihActivity>(actionParametersOf(ActionParameters.Key<String>("counterId") to ui.style.counterId)))
    Box(open, contentAlignment = Alignment.Center) { Text("Open", style = ui.ts(10f)) }
}
