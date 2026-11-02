package com.kamalahmed.prayerapp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import com.kamalahmed.prayerapp.core.model.Prayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One GlanceAppWidget implementation drives all six kinds; only the layout differs. */
abstract class PrayerWidget(val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = widgetDeps(context)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val style = deps.styles().get(appWidgetId, kind)
        val data = deps.loadWidgetData()
        provideContent { WidgetContent(kind, style, data) }
    }
}

class NextPrayerWidget : PrayerWidget(WidgetKind.NEXT_SMALL)
class AllPrayersWidget : PrayerWidget(WidgetKind.ALL_WIDE)
class NextStreakWidget : PrayerWidget(WidgetKind.NEXT_STREAK)
class TrackerWidget : PrayerWidget(WidgetKind.TRACKER)
class LargeWidget : PrayerWidget(WidgetKind.LARGE)
class MinimalWidget : PrayerWidget(WidgetKind.MINIMAL)

abstract class PrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.ensureScheduled(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { widgetDeps(context).styles().delete(appWidgetIds) } finally { pending.finish() }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.ensureScheduled(context)
    }
}

class NextPrayerReceiver : PrayerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget() }
class AllPrayersReceiver : PrayerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AllPrayersWidget() }
class NextStreakReceiver : PrayerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NextStreakWidget() }
class TrackerReceiver : PrayerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TrackerWidget() }
class LargeReceiver : PrayerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LargeWidget() }
class MinimalReceiver : PrayerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MinimalWidget() }

/** Kind ↔ widget/receiver lookup. */
object WidgetKinds {
    fun widget(kind: WidgetKind): PrayerWidget = when (kind) {
        WidgetKind.NEXT_SMALL -> NextPrayerWidget()
        WidgetKind.ALL_WIDE -> AllPrayersWidget()
        WidgetKind.NEXT_STREAK -> NextStreakWidget()
        WidgetKind.TRACKER -> TrackerWidget()
        WidgetKind.LARGE -> LargeWidget()
        WidgetKind.MINIMAL -> MinimalWidget()
    }

    fun receiver(kind: WidgetKind): Class<out PrayerWidgetReceiver> = when (kind) {
        WidgetKind.NEXT_SMALL -> NextPrayerReceiver::class.java
        WidgetKind.ALL_WIDE -> AllPrayersReceiver::class.java
        WidgetKind.NEXT_STREAK -> NextStreakReceiver::class.java
        WidgetKind.TRACKER -> TrackerReceiver::class.java
        WidgetKind.LARGE -> LargeReceiver::class.java
        WidgetKind.MINIMAL -> MinimalReceiver::class.java
    }

    fun component(context: Context, kind: WidgetKind) = ComponentName(context, receiver(kind))

    fun kindOf(context: Context, appWidgetId: Int): WidgetKind? {
        val cls = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className ?: return null
        return WidgetKind.entries.firstOrNull { receiver(it).name == cls }
    }

    fun ids(context: Context, kind: WidgetKind): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context, kind))

    fun anyPlaced(context: Context) = WidgetKind.entries.any { ids(context, it).isNotEmpty() }
}

/** Tap on a prayer circle: toggles prayed / not prayed (on-time vs late is decided automatically). */
class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prayer = Prayer.entries.firstOrNull { it.name == parameters[KeyPrayer] } ?: return
        val date = parameters[KeyDate]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        widgetDeps(context).tracker().togglePrayed(date, prayer)
        WidgetUpdater.updateAll(context)
    }

    companion object {
        val KeyPrayer = ActionParameters.Key<String>("prayer")
        val KeyDate = ActionParameters.Key<String>("date")
    }
}
