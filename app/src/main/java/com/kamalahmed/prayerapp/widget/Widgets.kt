package com.kamalahmed.prayerapp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.clickable
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.safety.launchAsync
import java.time.LocalDate

/** One GlanceAppWidget implementation drives all six kinds; only the layout differs. */
abstract class PrayerWidget(val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = widgetDeps(context)
        com.kamalahmed.prayerapp.core.safety.guard("PrayerWidget","seed counters") { deps.nafl().ensureDefaults() }
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        // The layout kind comes from the instance's real provider (receiver), not from this object, so an
        // update routed through the wrong GlanceAppWidget can never render another widget's layout.
        val kind = WidgetKinds.kindOf(context, appWidgetId) ?: kind
        val initial = com.kamalahmed.prayerapp.core.safety.guard("PrayerWidget","load #$appWidgetId") { loadSnapshot(deps, appWidgetId, kind) }
        if (initial == null) {
            provideContent { androidx.glance.text.Text("Could not load widget. Open the app to retry.",modifier = androidx.glance.GlanceModifier.clickable(androidx.glance.action.actionStartActivity<com.kamalahmed.prayerapp.MainActivity>())) }
            return
        }
        // Root cause of "widget not live" (beta.6): data used to be loaded once, outside provideContent. While a
        // Glance session is alive (it stays up after every tap), update() only recomposes the running session and
        // never re-runs provideGlance, so the widget kept rendering the stale snapshot. Now the content observes the
        // tracker DB, settings and an explicit refresh signal, so every change re-renders with fresh data.
        val live = WidgetLive.snapshots(deps, appWidgetId, kind) { loadSnapshot(deps, appWidgetId, kind) }
        provideContent {
            val snap by live.collectAsState(initial)
            WidgetContent(kind, snap.style, snap.data)
        }
    }

    private suspend fun loadSnapshot(deps: WidgetEntryPoint, appWidgetId: Int, kind: WidgetKind): WidgetSnapshot {
        // A corrupt style entry must not leave the widget blank: fall back to the kind's default look.
        val style = com.kamalahmed.prayerapp.core.safety.guard("PrayerWidget", "style #$appWidgetId") { deps.styles().get(appWidgetId, kind) }
            ?: WidgetStyle.decode(null, kind)
        return WidgetSnapshot(style, deps.loadWidgetData())
    }
}

class NextPrayerWidget : PrayerWidget(WidgetKind.NEXT_SMALL)
class AllPrayersWidget : PrayerWidget(WidgetKind.ALL_WIDE)
class NextStreakWidget : PrayerWidget(WidgetKind.NEXT_STREAK)
class TrackerWidget : PrayerWidget(WidgetKind.TRACKER)
class LargeWidget : PrayerWidget(WidgetKind.LARGE)
class TasbihWidget : PrayerWidget(WidgetKind.TASBIH)
class IqamahTableWidget : PrayerWidget(WidgetKind.IQAMAH_TABLE)
class MinimalWidget : PrayerWidget(WidgetKind.MINIMAL)

abstract class PrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.ensureScheduled(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        launchAsync("WidgetReceiver") { widgetDeps(context).styles().delete(appWidgetIds) }
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
class TasbihReceiver : PrayerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TasbihWidget() }
class IqamahTableReceiver : PrayerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = IqamahTableWidget() }
class MinimalReceiver : PrayerWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MinimalWidget() }

/** Kind ↔ widget/receiver lookup. */
object WidgetKinds {
    fun widget(kind: WidgetKind): PrayerWidget = when (kind) {
        WidgetKind.NEXT_SMALL -> NextPrayerWidget()
        WidgetKind.ALL_WIDE -> AllPrayersWidget()
        WidgetKind.NEXT_STREAK -> NextStreakWidget()
        WidgetKind.TRACKER -> TrackerWidget()
        WidgetKind.LARGE -> LargeWidget()
        WidgetKind.TASBIH -> TasbihWidget()
        WidgetKind.IQAMAH_TABLE -> IqamahTableWidget()
        WidgetKind.MINIMAL -> MinimalWidget()
    }

    fun receiver(kind: WidgetKind): Class<out PrayerWidgetReceiver> = when (kind) {
        WidgetKind.NEXT_SMALL -> NextPrayerReceiver::class.java
        WidgetKind.ALL_WIDE -> AllPrayersReceiver::class.java
        WidgetKind.NEXT_STREAK -> NextStreakReceiver::class.java
        WidgetKind.TRACKER -> TrackerReceiver::class.java
        WidgetKind.LARGE -> LargeReceiver::class.java
        WidgetKind.TASBIH -> TasbihReceiver::class.java
        WidgetKind.IQAMAH_TABLE -> IqamahTableReceiver::class.java
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
        // The DB write is observed by every running widget session (instant feedback); updateAll also covers
        // widgets whose session has ended.
        com.kamalahmed.prayerapp.core.safety.guard("ToggleAction", "toggle") { widgetDeps(context).tracker().togglePrayed(date, prayer) }
        WidgetUpdater.updateAll(context)
    }

    companion object {
        val KeyPrayer = ActionParameters.Key<String>("prayer")
        val KeyDate = ActionParameters.Key<String>("date")
    }
}

/** Uses persisted instance selection (not a stale render date/item), never opens an Activity. */
class TasbihAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        com.kamalahmed.prayerapp.core.safety.guard("TasbihAction", "count") {
            val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            if (WidgetKinds.kindOf(context,id) != WidgetKind.TASBIH) return@guard
            val deps = widgetDeps(context)
            val item = deps.styles().get(id,WidgetKind.TASBIH).counterId
            deps.nafl().adjustCounter(item, (parameters[Delta] ?: 1).coerceIn(-1,1))
            WidgetUpdater.updateAll(context)
        }
    }
    companion object { val Delta = ActionParameters.Key<Int>("delta") }
}
