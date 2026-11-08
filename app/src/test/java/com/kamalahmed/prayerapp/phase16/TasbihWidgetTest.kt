package com.kamalahmed.prayerapp.phase16

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.widget.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class TasbihWidgetTest {
    @Test fun callbackCountsSelectedInstanceWithoutOpeningAppAndSupportsDecrement() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val deps = widgetDeps(ctx); deps.nafl().ensureDefaults()
        val am = AppWidgetManager.getInstance(ctx)
        for (id in listOf(41,42)) {
            val info = AppWidgetProviderInfo().apply { provider = ComponentName(ctx,TasbihReceiver::class.java) }
            Shadows.shadowOf(am).addBoundWidget(id,info)
        }
        deps.styles().save(41,WidgetStyle.defaults(WidgetKind.TASBIH).copy(counterId = "salawat"))
        deps.styles().save(42,WidgetStyle.defaults(WidgetKind.TASBIH).copy(counterId = "istighfar"))
        val today = LocalDate.now().toString()
        suspend fun count(id: String) = deps.nafl().logs.first().firstOrNull { it.date == today && it.itemId == id }?.count ?: 0
        val oldSalawat = count("salawat"); val oldIstighfar = count("istighfar")
        val manager = GlanceAppWidgetManager(ctx)
        val action = TasbihAction()
        action.onAction(ctx,manager.getGlanceIdBy(41),actionParametersOf(TasbihAction.Delta to 1))
        action.onAction(ctx,manager.getGlanceIdBy(41),actionParametersOf(TasbihAction.Delta to 1))
        action.onAction(ctx,manager.getGlanceIdBy(42),actionParametersOf(TasbihAction.Delta to 1))
        action.onAction(ctx,manager.getGlanceIdBy(41),actionParametersOf(TasbihAction.Delta to -1))
        assertEquals(oldSalawat+1,count("salawat")); assertEquals(oldIstighfar+1,count("istighfar"))
        assertNull(Shadows.shadowOf(ctx as Application).nextStartedActivity)
        deps.styles().delete(intArrayOf(41,42))
    }
}
