package com.kamalahmed.prayerapp.phase18

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.nafl.NaflDefaults
import com.kamalahmed.prayerapp.nafl.NaflLog
import com.kamalahmed.prayerapp.widget.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** Real Glance -> RemoteViews -> Android Views -> native Skia PNGs, not illustrative mockups. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderedTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val settings = AppSettings()
    private val base = WidgetData.build(PrayerSchedule(settings.toCalcParams()), settings, emptyMap(), ZonedDateTime.parse("2026-11-08T14:00:00+04:00[Asia/Dubai]"))
    private val data = base.copy(
        rows = base.rows.map { it.copy(iqamah = if (it.prayer == Prayer.SUNRISE) null else it.time.plusMinutes(20)) },
        todayIqamah = base.todayTimes.filterKeys { it != Prayer.SUNRISE }.mapValues { it.value.plusMinutes(20) },
        counterItems = NaflDefaults.items,
        counterLogs = listOf(NaflLog(base.now.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate().toString(), "istighfar", 33)),
    )
    private val out = File(System.getProperty("prayer.widget.renders", "build/widget-renders/beta10")).apply { mkdirs() }
    @Before fun freezePreviewClock() { android.os.SystemClock.setCurrentTimeMillis(base.now.toInstant().toEpochMilli()) }
    private fun nodes(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { nodes(v.getChildAt(it)) } else emptyList()
    private fun texts(v: View) = nodes(v).filterIsInstance<TextView>()
    private fun top(v: View): Int = v.top + ((v.parent as? View)?.let(::top) ?: 0)
    private fun left(v: View): Int = v.left + ((v.parent as? View)?.let(::left) ?: 0)
    private fun render(k: WidgetKind, w: Int, h: Int, suffix: String = "", st: WidgetStyle = WidgetStyle.defaults(k)): Pair<FrameLayout, Bitmap> = runBlocking {
        val rv = withTimeout(20_000) { GlanceRemoteViews().compose(ctx, DpSize(w.dp, h.dp)) { WidgetContent(k, st, data, interactive = false) }.remoteViews }
        val root = FrameLayout(ctx)
        root.addView(rv.apply(ctx, root))
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(out, "${k.name.lowercase()}_${w}x${h}$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$k contains text", texts(root).any { it.text.isNotEmpty() })
        root to bitmap
    }
    @Test fun everyWidgetFillsItsActualCellAtMultipleSizes() {
        val sizes = mapOf(
            WidgetKind.NEXT_SMALL to listOf(150 to 150, 260 to 220),
            WidgetKind.ALL_WIDE to listOf(300 to 56, 280 to 100, 420 to 160),
            WidgetKind.NEXT_STREAK to listOf(280 to 170, 420 to 230),
            WidgetKind.TRACKER to listOf(280 to 100, 420 to 170),
            WidgetKind.LARGE to listOf(320 to 360, 480 to 500),
            WidgetKind.MINIMAL to listOf(300 to 64, 420 to 100),
            WidgetKind.TASBIH to listOf(170 to 170, 320 to 80),
            WidgetKind.IQAMAH_TABLE to listOf(300 to 270, 440 to 360),
        )
        sizes.forEach { (k, variants) -> variants.forEach { (w,h) ->
            val (_, b) = render(k, w, h, "_fill", WidgetStyle.defaults(k).copy(theme = WidgetTheme.SOLID, opacity = 100, corner = 0))
            assertEquals("$k fills top", 255, b.getPixel(w/2, 1) ushr 24)
            assertEquals("$k fills bottom", 255, b.getPixel(w/2, h-2) ushr 24)
        } }
    }
    @Test fun marginAndWrapAreDistinctAndOpacityZeroStaysTransparent() {
        val k = WidgetKind.NEXT_SMALL
        val st = WidgetStyle.defaults(k).copy(opacity = 100, corner = 0, outerMargin = 10)
        val (_, fill) = render(k, 200, 300, "_margin", st)
        assertEquals(0, fill.getPixel(100, 2) ushr 24)
        assertEquals(255, fill.getPixel(100, 12) ushr 24)
        val (_, wrap) = render(k, 200, 400, "_wrap", st.copy(fillPanel = false))
        assertEquals(0, wrap.getPixel(100, 12) ushr 24)
        val (_, transparent) = render(k, 200, 300, "_transparent", st.copy(opacity = 0))
        assertEquals(0, transparent.getPixel(100, 12) ushr 24)
    }
    @Test fun counterSquareAndWideShowCounterNotPrayer() {
        listOf(170 to 170, 320 to 80).forEach { (w,h) ->
            val (root, _) = render(WidgetKind.TASBIH, w, h)
            val labels = texts(root).map { it.text.toString() }
            assertTrue(labels.contains("Istighfar"))
            assertTrue(labels.any { it.contains("33") })
            assertTrue(labels.any { it.contains("100") })
            assertFalse(labels.any { it.contains("Asr") })
            assertTrue(labels.contains("−1")); assertTrue(labels.contains("Open"))
            texts(root).forEach { assertTrue("counter text within cell: ${it.text}", top(it) >= 0 && top(it)+it.height <= h) }
            texts(root).filter { it.text.contains("33") }.forEach { tv ->
                val layout = tv.layout
                assertNotNull(layout)
                for (line in 0 until layout.lineCount) assertEquals("count/target must not be ellipsized", 0, layout.getEllipsisCount(line))
            }
        }
    }
    @Test fun bothTimesHaveAlignedPrimaryRowsAndMutedIqamahWithoutDivider() {
        val st = WidgetStyle.defaults(WidgetKind.ALL_WIDE).copy(timeMode = WidgetTimeMode.BOTH, showCountdown = true)
        val (root, _) = render(WidgetKind.ALL_WIDE, 420, 160, "_both", st)
        val views = texts(root)
        val primary = data.rows.filter { it.prayer != Prayer.SUNRISE }.map { row -> views.first { it.text.toString() == WidgetUiState(st,data,false).time(row.time) } }
        assertEquals(5, primary.size)
        assertEquals(1, primary.map { top(it) }.distinct().size)
        assertTrue(primary.maxOf { it.width } - primary.minOf { it.width } <= 2)
        views.forEach { tv -> assertTrue("Both-mode text is not clipped: ${tv.text}", top(tv) >= 0 && top(tv)+tv.height <= root.height) }
        val iqamah = views.filter { it.text.toString() == "Iqamah" }
        assertEquals(5, iqamah.size)
        assertTrue(iqamah.all { it.textSize < primary.first().textSize })
        assertFalse(views.any { it.text.contains('|') || it.text.startsWith("A ") || it.text.startsWith("I ") })
        val gaps = primary.map { left(it)+it.width/2 }.zipWithNext { a,b -> b-a }
        assertTrue(gaps.max() - gaps.min() <= 2)
    }
    @Test fun tableHasThreeAlignedCenteredColumns() {
        val (root, _) = render(WidgetKind.IQAMAH_TABLE, 360, 300)
        val tv = texts(root)
        val headers = listOf("Prayer", "Adhan", "Iqamah").map { name -> tv.first { it.text.toString() == name } }
        val widths = headers.map { it.width }
        assertTrue(widths.max() - widths.min() <= 1)
        val fajr = tv.first { it.text.toString() == "Fajr" }
        assertEquals(left(headers[0]), left(fajr))
        assertEquals(headers[0].width, fajr.width)
    }
    @Test fun tickTimesCanBeOffAboveOrBelowEvenInCompactCells() {
        listOf(WidgetKind.TRACKER, WidgetKind.NEXT_STREAK).forEach { k -> TickTimes.entries.forEach { placement ->
            val st = WidgetStyle.defaults(k).copy(tickTimes = placement, timeMode = WidgetTimeMode.BOTH)
            val (root, _) = render(k, 420, 190, "_${placement.name.lowercase()}_both", st)
            val t = WidgetUiState(st,data,false).time(data.todayTimes.getValue(Prayer.FAJR))
            val times = texts(root).filter { it.text.toString() == t }
            if (placement == TickTimes.OFF) assertTrue(times.isEmpty()) else {
                assertEquals(1, times.size)
                val tick = nodes(root).first { it.contentDescription?.toString()?.startsWith("Fajr:") == true }
                if (placement == TickTimes.ABOVE) assertTrue(top(times[0]) < top(tick)) else assertTrue(top(times[0]) >= top(tick)+tick.height)
            }
        } }
    }
    @Test fun overallScaleChangesActualRenderedTextSize() {
        val k = WidgetKind.TASBIH
        val (small, _) = render(k, 220, 220, "_scale75", WidgetStyle.defaults(k).copy(contentScale = 0.75f))
        val (big, _) = render(k, 220, 220, "_scale125", WidgetStyle.defaults(k).copy(contentScale = 1.25f))
        assertTrue(texts(small).first { it.text.toString() == "33" }.textSize < texts(big).first { it.text.toString() == "33" }.textSize)
    }
    @Test fun everyPrayerWidgetKeepsBothTimesInsideItsCell() {
        val sizes=mapOf(WidgetKind.NEXT_SMALL to (170 to 170),WidgetKind.ALL_WIDE to (320 to 140),WidgetKind.NEXT_STREAK to (290 to 165),WidgetKind.TRACKER to (320 to 140),WidgetKind.LARGE to (320 to 360),WidgetKind.MINIMAL to (300 to 64),WidgetKind.IQAMAH_TABLE to (320 to 300))
        sizes.forEach { (k,wh) ->
            val (root,bitmap)=render(k,wh.first,wh.second,"_both_bounds",WidgetStyle.defaults(k).copy(timeMode=WidgetTimeMode.BOTH))
            if(k==WidgetKind.LARGE) {
                val asr=texts(root).last { it.text.toString()=="Asr" }
                assertTrue("Translucent highlight must not wash out muted iqamah text", android.graphics.Color.red(bitmap.getPixel(16,top(asr)+asr.height/2))<100)
            }
            if(k==WidgetKind.MINIMAL) {
                val chrono=texts(root).first { it is android.widget.Chronometer }
                val title=texts(root).first { it.text.toString().startsWith("Asr ") }
                assertTrue("Minimal countdown must not overlap the title", left(chrono)>=left(title)+title.width)
            }
            texts(root).filter { it.text.isNotEmpty() }.forEach { tv -> assertTrue("$k Both-mode text outside cell: ${tv.text} at ${top(tv)} + ${tv.height} / ${wh.second}",tv.height>0 && top(tv)>=0 && top(tv)+tv.height<=wh.second) }
        }
    }

}
