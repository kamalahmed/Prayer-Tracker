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
import androidx.glance.unit.ColorProvider
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.R
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.tracker.PrayerStatus
import com.kamalahmed.prayerapp.tracker.StreakCalculator
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Resolved style helpers shared by all widget layouts. */
class WidgetUiState(val style: WidgetStyle, val data: WidgetData, val interactive: Boolean) {
    val pal = WidgetPalette.of(style)
    val use24h = style.use24h(data.appUse24h)
    val abbr = style.abbr(data.appAbbr)
    private val fw = when (style.weight) {
        WidgetWeight.NORMAL -> FontWeight.Normal
        WidgetWeight.MEDIUM -> FontWeight.Medium
        WidgetWeight.BOLD -> FontWeight.Bold
    }

    fun icon(base: Int): Dp = (base * style.iconScale).dp
    fun box(base: Int): Dp = (base * style.boxScale).dp
    val gap: Dp get() = style.spacing.dp

    fun ts(size: Float, color: Int = pal.text, bold: Boolean = false) = TextStyle(
        color = ColorProvider(Color(color)),
        fontSize = (size * style.textScale).sp,
        fontWeight = if (bold) FontWeight.Bold else fw,
    )

    fun time(t: ZonedDateTime) = TimeFormat.time(t, use24h, style.showAmPm)
    fun label(p: Prayer) = p.label(abbr)
    fun rowLabel(r: WidgetRow) = if (abbr) r.abbr else r.name

    /** Rows for list layouts honouring the Sunrise / Midnight toggles. */
    fun listRows(): List<WidgetRow> = buildList {
        addAll(data.rows.filter { it.prayer != Prayer.SUNRISE || style.showSunrise })
        if (style.showMidnight) add(data.midnight)
    }
}

@Composable
fun WidgetContent(kind: WidgetKind, style: WidgetStyle, data: WidgetData, interactive: Boolean = true) {
    val ui = WidgetUiState(style, data, interactive)
    var root = GlanceModifier.fillMaxSize().then(backgroundModifier(ui)).padding(style.padding.dp)
    if (interactive) root = root.clickable(actionStartActivity<MainActivity>())
    Box(modifier = root, contentAlignment = Alignment.Center) {
        when (kind) {
            WidgetKind.NEXT_SMALL -> NextSmall(ui)
            WidgetKind.ALL_WIDE -> AllWide(ui)
            WidgetKind.NEXT_STREAK -> NextStreak(ui)
            WidgetKind.TRACKER -> Tracker(ui)
            WidgetKind.LARGE -> Large(ui)
            WidgetKind.MINIMAL -> Minimal(ui)
        }
    }
}

@android.annotation.SuppressLint("LocalContextResourcesRead") // Glance context, not a Compose UI context
@Composable
private fun backgroundModifier(ui: WidgetUiState): GlanceModifier {
    val st = ui.style
    if (st.opacity == 0) return GlanceModifier
    if (st.theme != WidgetTheme.GRADIENT && Build.VERSION.SDK_INT >= 31) {
        return GlanceModifier.background(ColorProvider(Color(ui.pal.bg))).cornerRadius(st.corner.dp)
    }
    val size = LocalSize.current
    val density = LocalContext.current.resources.displayMetrics.density
    val bmp = WidgetBitmaps.background(size.width.value, size.height.value, density, ui.pal, st.corner, st.theme == WidgetTheme.GRADIENT)
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
        val base = SystemClock.elapsedRealtime() + (target.toInstant().toEpochMilli() - System.currentTimeMillis())
        setChronometer(R.id.chrono, base, if (prefix.isEmpty()) null else "$prefix%s", true)
        setChronometerCountDown(R.id.chrono, true)
        setTextColor(R.id.chrono, color)
        setTextViewTextSize(R.id.chrono, TypedValue.COMPLEX_UNIT_SP, size * ui.style.textScale)
    }
    AndroidRemoteViews(rv)
}

@Composable
private fun HijriBadge(ui: WidgetUiState) {
    Box(
        modifier = GlanceModifier.size(ui.box(32)).background(
            ImageProvider(R.drawable.w_pill), ContentScale.FillBounds, ColorFilter.tint(ColorProvider(Color(ui.pal.badge))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text("${ui.data.hijri.day}", style = ui.ts(15f, ui.pal.gold, bold = true), maxLines = 1)
    }
}

private fun highlight(ui: WidgetUiState): GlanceModifier =
    GlanceModifier.background(ImageProvider(R.drawable.w_pill), ContentScale.FillBounds, ColorFilter.tint(ColorProvider(Color(ui.pal.highlight))))

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
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ui.style.showIcons) {
                Icon(WidgetData.icon(d.next.prayer), ui.icon(18), ui.pal.accent)
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(d.next.prayer.displayName, style = ui.ts(15f), maxLines = 1)
        }
        Text(ui.time(d.next.time), style = ui.ts(30f, bold = true), maxLines = 1, modifier = GlanceModifier.padding(top = ui.gap))
        if (ui.style.showCountdown) {
            Box(GlanceModifier.padding(top = ui.gap / 2)) { Countdown(ui, d.next.time, 13f, ui.pal.accent, "in ") }
        }
        if (ui.style.showHijri) {
            Text("${d.hijri.day} ${d.hijri.monthName}", style = ui.ts(11f, ui.pal.gold), maxLines = 1, modifier = GlanceModifier.padding(top = ui.gap))
        }
    }
}

@Composable
private fun AllWide(ui: WidgetUiState) {
    val d = ui.data
    val compact = LocalSize.current.height < 100.dp
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(d.next.prayer.displayName, style = ui.ts(16f, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(8.dp))
            if (ui.style.showCountdown) Countdown(ui, d.next.time, 15f, ui.pal.sub)
            else Text(ui.time(d.next.time), style = ui.ts(15f, ui.pal.sub), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            if (ui.style.showHijri) HijriBadge(ui)
        }
        Spacer(GlanceModifier.height(ui.gap))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ui.listRows().take(8).forEach { r -> WideCell(ui, r, showIcon = ui.style.showIcons && !compact) }
        }
    }
}

@Composable
private fun RowScope.WideCell(ui: WidgetUiState, r: WidgetRow, showIcon: Boolean) {
    val c = if (r.isNext) ui.pal.accent else ui.pal.sub
    var m = GlanceModifier.defaultWeight().padding(vertical = 4.dp)
    if (r.isNext) m = m.then(highlight(ui))
    Column(modifier = m, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showIcon) {
            Icon(r.icon, ui.icon(16), c)
            Spacer(GlanceModifier.height(2.dp))
        }
        Text(ui.rowLabel(r), style = ui.ts(11f, c), maxLines = 1)
        Text(ui.time(r.time), style = ui.ts(13f, if (r.isNext) ui.pal.accent else ui.pal.text), maxLines = 1)
    }
}

@Composable
private fun NextStreak(ui: WidgetUiState) {
    val d = ui.data
    val compact = LocalSize.current.height < 130.dp
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ui.style.showIcons) {
                        Icon(WidgetData.icon(d.next.prayer), ui.icon(16), ui.pal.accent)
                        Spacer(GlanceModifier.width(5.dp))
                    }
                    Text(d.next.prayer.displayName, style = ui.ts(14f), maxLines = 1)
                }
                Text(ui.time(d.next.time), style = ui.ts(26f, bold = true), maxLines = 1)
                if (ui.style.showCountdown) Countdown(ui, d.next.time, 12f, ui.pal.accent, "in ")
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
                    StatusDot(ui, p, ui.box(24))
                    if (!compact) Text(p.abbr, style = ui.ts(9f, ui.pal.sub), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Tracker(ui: WidgetUiState) {
    val d = ui.data
    val compact = LocalSize.current.height < 100.dp
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
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
                    StatusDot(ui, p, ui.box(if (compact) 26 else 32))
                    Text(ui.label(p), style = ui.ts(10f, ui.pal.sub), maxLines = 1, modifier = GlanceModifier.padding(top = 2.dp))
                    if (!compact) d.todayTimes[p]?.let { Text(ui.time(it), style = ui.ts(10f, ui.pal.text), maxLines = 1) }
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
    Column(modifier = GlanceModifier.fillMaxSize()) {
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
                    ringDp.value, ctx.resources.displayMetrics.density, d.progress,
                    WidgetPalette.withAlpha(ui.pal.text, 0.12f), ui.pal.accent, 7f * ui.style.boxScale,
                )
                Image(ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.size(ringDp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.label(d.next.prayer), style = ui.ts(12f, ui.pal.sub), maxLines = 1)
                    Text(ui.time(d.next.time), style = ui.ts(16f, bold = true), maxLines = 1)
                    if (ui.style.showCountdown) Countdown(ui, d.next.time, 11f, ui.pal.accent)
                }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Streak(ui, 24f)
                Text("day streak · best ${d.bestDays}", style = ui.ts(11f, ui.pal.sub), maxLines = 1)
                Row(modifier = GlanceModifier.padding(top = 6.dp)) {
                    Prayer.obligatory.forEach { p ->
                        Box(GlanceModifier.padding(end = 4.dp)) { StatusDot(ui, p, ui.box(22)) }
                    }
                }
            }
        }
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            ui.listRows().take(8).forEach { r -> LargeRow(ui, r) }
        }
    }
}

@Composable
private fun LargeRow(ui: WidgetUiState, r: WidgetRow) {
    var m = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = (2 + ui.style.spacing / 3).dp)
    if (r.isNext) m = m.then(highlight(ui))
    val c = when {
        r.isNext -> ui.pal.accent
        r.passed -> ui.pal.sub
        else -> ui.pal.text
    }
    Row(modifier = m, verticalAlignment = Alignment.CenterVertically) {
        if (ui.style.showIcons) {
            Icon(r.icon, ui.icon(16), if (r.isNext) ui.pal.accent else ui.pal.sub)
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(ui.rowLabel(r), style = ui.ts(13f, c), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Text(ui.time(r.time), style = ui.ts(13f, c, bold = r.isNext), maxLines = 1)
    }
}

@Composable
private fun Minimal(ui: WidgetUiState) {
    val d = ui.data
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ui.style.showIcons) {
                Icon(WidgetData.icon(d.next.prayer), ui.icon(16), ui.pal.accent)
                Spacer(GlanceModifier.width(6.dp))
            }
            Text("${ui.label(d.next.prayer)} ${ui.time(d.next.time)}", style = ui.ts(16f), maxLines = 1)
            if (ui.style.showCountdown) {
                Text("  ·  ", style = ui.ts(14f, ui.pal.sub), maxLines = 1)
                Countdown(ui, d.next.time, 14f, ui.pal.sub)
            }
        }
        if (ui.style.showHijri) Text(d.hijri.format(), style = ui.ts(11f, ui.pal.gold), maxLines = 1)
    }
}
