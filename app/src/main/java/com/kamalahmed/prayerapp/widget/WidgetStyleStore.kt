package com.kamalahmed.prayerapp.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.widgetStyles by preferencesDataStore(name = "widget_styles")

/** Appearance settings stored per appWidgetId. */
@Singleton
class WidgetStyleStore @Inject constructor(@ApplicationContext private val context: Context) {

    private fun key(id: Int) = stringPreferencesKey("w$id")

    suspend fun get(appWidgetId: Int, kind: WidgetKind): WidgetStyle =
        WidgetStyle.decode(context.widgetStyles.data.first()[key(appWidgetId)], kind)

    suspend fun has(appWidgetId: Int): Boolean = context.widgetStyles.data.first()[key(appWidgetId)] != null

    suspend fun save(appWidgetId: Int, style: WidgetStyle) {
        context.widgetStyles.edit { it[key(appWidgetId)] = style.sanitized().encode() }
    }

    suspend fun delete(ids: IntArray) {
        context.widgetStyles.edit { p -> ids.forEach { p.remove(key(it)) } }
    }
}
