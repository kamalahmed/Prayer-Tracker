package com.kamalahmed.prayerapp.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Renders every widget kind through the real Glance -> RemoteViews pipeline into a bitmap and checks pixels:
 * opacity 0 leaves no background, opacity 100 is opaque, the panel wraps its content in a tall cell, and
 * padding 0 lets content touch the panel edge. PNGs go to the app's external files dir for inspection.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = AppSettings()
    private val data = WidgetData.build(
        PrayerSchedule(settings.toCalcParams()), settings, emptyMap(),
        ZonedDateTime.of(LocalDate.of(2026, 11, 2).atTime(14, 0), ZoneId.of("Asia/Dubai")),
    )
    private val outDir = File(ctx.getExternalFilesDir(null), "widget-render").apply { mkdirs() }

    private fun px(dp: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), ctx.resources.displayMetrics).toInt()

    private fun render(kind: WidgetKind, style: WidgetStyle, w: Int, h: Int, name: String): Bitmap = runBlocking {
        val rv = GlanceRemoteViews().compose(ctx, DpSize(w.dp, h.dp)) { WidgetContent(kind, style, data, interactive = false) }.remoteViews
        val bmp = Bitmap.createBitmap(px(w), px(h), Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fl = FrameLayout(ctx)
            fl.addView(rv.apply(ctx, fl))
            fl.measure(View.MeasureSpec.makeMeasureSpec(bmp.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(bmp.height, View.MeasureSpec.EXACTLY))
            fl.layout(0, 0, bmp.width, bmp.height)
            fl.draw(Canvas(bmp))
        }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp
    }

    private fun alphaCount(b: Bitmap, y0: Int, y1: Int): Int {
        var n = 0
        for (y in y0 until y1) for (x in 0 until b.width) if ((b.getPixel(x, y) ushr 24) != 0) n++
        return n
    }

    private val sizes = mapOf(
        WidgetKind.NEXT_SMALL to (170 to 170), WidgetKind.ALL_WIDE to (320 to 110), WidgetKind.NEXT_STREAK to (290 to 165),
        WidgetKind.TRACKER to (320 to 120), WidgetKind.LARGE to (320 to 360), WidgetKind.MINIMAL to (300 to 64),
        WidgetKind.TASBIH to (170 to 170), WidgetKind.IQAMAH_TABLE to (320 to 300),
    )

    @Test fun opacityZeroHasNoBackgroundAndHundredIsOpaque() {
        for (k in WidgetKind.entries) for (t in WidgetTheme.entries) {
            val (w, h) = sizes.getValue(k)
            val base = WidgetStyle.defaults(k).copy(theme = t, padV = 8, padH = 8)
            val zero = render(k, base.copy(opacity = 0), w, h, "${k}_${t}_op0")
            // Corners of the panel are padding area: must be fully transparent at opacity 0.
            assertEquals("$k/$t corner", 0, zero.getPixel(px(3), px(3)) ushr 24)
            val full = render(k, base.copy(opacity = 100, corner = 0, align = WidgetAlign.TOP), w, h, "${k}_${t}_op100")
            assertEquals("$k/$t opaque", 255, full.getPixel(px(3), px(3)) ushr 24)
        }
    }

    @Test fun panelWrapsContentInTallCellAndPaddingReachesZero() {
        for (k in listOf(WidgetKind.ALL_WIDE, WidgetKind.TRACKER, WidgetKind.NEXT_STREAK, WidgetKind.MINIMAL, WidgetKind.NEXT_SMALL, WidgetKind.LARGE)) {
            val (w, _) = sizes.getValue(k)
            val tall = 520
            for (a in WidgetAlign.entries) {
                val st = WidgetStyle.defaults(k).copy(fillPanel = false, theme = WidgetTheme.SOLID, opacity = 100, corner = 0, padV = 0, padH = 0, spacing = 0, align = a)
                val b = render(k, st, w, tall, "${k}_tall_${a}_pad0")
                val rows = (0 until b.height).filter { y -> (b.getPixel(b.width / 2, y) ushr 24) != 0 }
                assertTrue("$k drew something", rows.isNotEmpty())
                val panelH = rows.last() - rows.first() + 1
                assertTrue("$k/$a panel ${panelH}px should be much shorter than cell ${b.height}px", k == WidgetKind.LARGE || panelH < b.height * 0.6)
                when (a) {
                    WidgetAlign.TOP -> assertTrue("$k top", rows.first() <= 1)
                    WidgetAlign.BOTTOM -> assertTrue("$k bottom", rows.last() >= b.height - 2)
                    WidgetAlign.CENTER -> if (k != WidgetKind.LARGE) assertTrue("$k center", rows.first() > 0 && rows.last() < b.height - 1)
                }
            }
            // Max padding makes the panel taller than zero padding by ~2*32dp.
            val st0 = WidgetStyle.defaults(k).copy(fillPanel = false, theme = WidgetTheme.SOLID, opacity = 100, corner = 0, padV = 0, padH = 0, align = WidgetAlign.TOP)
            val h0 = alphaRows(render(k, st0, w, tall, "${k}_tall_padV0"))
            val h32 = alphaRows(render(k, st0.copy(padV = 32, padH = 32), w, tall, "${k}_tall_padV32"))
            if (k != WidgetKind.LARGE) assertTrue("$k padV grows panel: $h0 -> $h32", h32 - h0 >= px(60))
        }
    }

    private fun alphaRows(b: Bitmap) = (0 until b.height).count { y -> (b.getPixel(b.width / 2, y) ushr 24) != 0 }
}
