package com.kamalahmed.prayerapp.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.widgetStyles by preferencesDataStore(name = "widget_styles")

/** Appearance settings stored per appWidgetId. */
@Singleton
class WidgetStyleStore @Inject constructor(@ApplicationContext private val context: Context) {

    private fun key(id: Int) = stringPreferencesKey("w$id")

    suspend fun get(appWidgetId: Int, kind: WidgetKind): WidgetStyle =
        WidgetStyle.decode(safeData().first()[key(appWidgetId)], kind)

    suspend fun has(appWidgetId: Int): Boolean = safeData().first()[key(appWidgetId)] != null

    private fun safeData() = context.widgetStyles.data.catch { e ->
        if (e is java.io.IOException) { com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal("WidgetStyleStore", "read failed", e); emit(androidx.datastore.preferences.core.emptyPreferences()) } else throw e
    }

    suspend fun save(appWidgetId: Int, style: WidgetStyle) {
        context.widgetStyles.edit { it[key(appWidgetId)] = style.sanitized().encode() }
    }

    suspend fun backupExport() = com.kamalahmed.prayerapp.backup.PreferenceJson.encode(context.widgetStyles.data.first())
    fun validateBackup(obj: org.json.JSONObject) {
        com.kamalahmed.prayerapp.backup.PreferenceJson.decode(obj).asMap().forEach { (k,v) ->
            require(k.name.matches(Regex("w[0-9]+")) && v is String && v.length <= 4096) { "Invalid widget style" }
        }
    }
    suspend fun backupRestore(obj: org.json.JSONObject, merge: Boolean) {
        validateBackup(obj)
        val source = com.kamalahmed.prayerapp.backup.PreferenceJson.decode(obj)
        context.widgetStyles.edit { com.kamalahmed.prayerapp.backup.PreferenceJson.apply(it, source, merge) }
    }

    suspend fun delete(ids: IntArray) {
        context.widgetStyles.edit { p -> ids.forEach { p.remove(key(it)) } }
    }
}
