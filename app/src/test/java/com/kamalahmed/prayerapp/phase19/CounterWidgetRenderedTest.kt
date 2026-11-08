package com.kamalahmed.prayerapp.phase19

import android.app.Application
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import androidx.compose.ui.unit.*
import androidx.glance.appwidget.*
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.toCalcParams
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.widget.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.time.*

/** Actual production Glance -> RemoteViews -> native Skia renders, not HTML illustrations. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=Application::class,qualifiers="mdpi")
class CounterWidgetRenderedTest {
    private val ctx=ApplicationProvider.getApplicationContext<Context>()
    private val now=ZonedDateTime.parse("2026-11-08T14:00:00+04:00[Asia/Dubai]")
    private val settings=AppSettings()
    private val base=WidgetData.build(PrayerSchedule(settings.toCalcParams()),settings,emptyMap(),now)
    private val out=File("build/widget-renders/beta11").apply { mkdirs() }
    @Before fun freeze() { android.os.SystemClock.setCurrentTimeMillis(now.toInstant().toEpochMilli()) }
    private fun nodes(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { nodes(v.getChildAt(it)) } else emptyList()
    private fun top(v: View): Int=v.top+((v.parent as? View)?.let(::top) ?: 0)
    private fun render(w: Int,h: Int,count: Int,target: Int=100,style: WidgetStyle=WidgetStyle.defaults(WidgetKind.TASBIH),name: String="Istighfar",suffix: String=""): Pair<View,Bitmap> = runBlocking {
        val data=base.copy(counterItems=listOf(NaflItem("istighfar",name,type="COUNTER",dailyTarget=target)),counterLogs=listOf(NaflLog(now.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate().toString(),"istighfar",count)))
        val rv=withTimeout(20_000) { GlanceRemoteViews().compose(ctx,DpSize(w.dp,h.dp)) { WidgetContent(WidgetKind.TASBIH,style,data,interactive=false) }.remoteViews }
        val root=FrameLayout(ctx);root.addView(rv.apply(ctx,root))
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
        File(out,"counter_${w}x${h}_${count}_target${target}_${style.theme.name.lowercase()}$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        val texts=nodes(root).filterIsInstance<TextView>()
        assertTrue(texts.any { it.text.toString()==count.toString() });if(w>=180 && h>=64 || !WidgetLayout.wideCounter(w.toFloat(),h.toFloat())) assertTrue(texts.any { it.text.toString()=="−1" });assertTrue(texts.any { it.text.toString()=="Open" })
        texts.filter { it.text.isNotEmpty() }.forEach { assertTrue("Text outside ${w}x${h}: ${it.text}",it.height>0 && top(it)>=0 && top(it)+it.height<=h) }
        texts.filter { it.text.toString()==count.toString() || it.text.startsWith("Target") }.forEach { v ->
            assertNotNull(v.layout);for(i in 0 until v.layout.lineCount) assertEquals("Count / target clipped: ${v.text}",0,v.layout.getEllipsisCount(i))
        }
        root to bitmap
    }
    @Test fun squareAndWideRingsRemainVisibleAtZeroTargetAndMultipleLaps() {
        listOf(170 to 170,320 to 80,220 to 220,420 to 100).forEach { (w,h) -> listOf(0,33,100,101,350).forEach { count ->
            val (v,_)=render(w,h,count)
            assertTrue(nodes(v).filterIsInstance<ImageView>().isNotEmpty())
            if(count>=100) assertTrue(nodes(v).filterIsInstance<TextView>().any { it.text.contains("×${count/100}") })
        } }
    }
    @Test fun lightAndTransparentVariantsKeepTheCentreFreeOfOpaqueBackground() {
        listOf(WidgetTheme.LIGHT,WidgetTheme.SOLID).forEach { theme ->
            render(220,220,350,style=WidgetStyle.defaults(WidgetKind.TASBIH).copy(theme=theme))
            listOf(170 to 170,320 to 80).forEach { (w,h) ->
                val (_,b)=render(w,h,350,style=WidgetStyle.defaults(WidgetKind.TASBIH).copy(theme=theme,opacity=0),suffix="_transparent")
                assertEquals(0,Color.alpha(b.getPixel(w/2,1)))
            }
        }
    }
    @Test fun minimumSizedCounterStillHasAReadableCount() {
        val (root,_)=render(110,48,1000000,target=100)
        assertTrue("A resized counter must not become microscopic",nodes(root).filterIsInstance<TextView>().first { it.text.toString()=="1000000" }.textSize>=10f)
    }
    @Test fun extremeCountsFreeModeAndMinimumSizesStayWithinTheCell() {
        listOf(170 to 170,320 to 80,110 to 48,140 to 140).forEach { (w,h) ->
            render(w,h,1_000_000,target=1,name="A long custom daily counter name")
            render(w,h,98765,target=0,suffix="_free")
        }
    }
}
