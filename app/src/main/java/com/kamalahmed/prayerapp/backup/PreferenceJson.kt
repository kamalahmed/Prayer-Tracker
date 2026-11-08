package com.kamalahmed.prayerapp.backup

import androidx.datastore.preferences.core.*
import org.json.JSONObject

/** Explicit types prevent a malicious file turning a boolean preference into a crashing String. */
object PreferenceJson {
    fun encode(p: Preferences): JSONObject = JSONObject().also { out -> p.asMap().forEach { (key,value) ->
        val type=when(value) { is String -> "string"; is Boolean -> "boolean"; is Int -> "int"; is Long -> "long"; is Double -> "double"; is Float -> "float"; else -> error("Unsupported preference") }
        out.put(key.name,JSONObject().put("type",type).put("value",value))
    } }
    fun decode(obj: JSONObject): Preferences {
        require(obj.length()<=500) { "Too many preferences" }
        val p=mutablePreferencesOf()
        obj.keys().forEach { name ->
            require(name.length<=100)
            val entry=obj.getJSONObject(name); val value=entry.get("value")
            when(entry.getString("type")) {
                "string" -> { require(value is String && value.length<=8192); p[stringPreferencesKey(name)]=value }
                "boolean" -> { require(value is Boolean); p[booleanPreferencesKey(name)]=value }
                "int" -> { require(value is Number && value.toDouble()==value.toInt().toDouble()); p[intPreferencesKey(name)]=value.toInt() }
                "long" -> { require(value is Number && value.toDouble()==value.toLong().toDouble()); p[longPreferencesKey(name)]=value.toLong() }
                "double" -> { require(value is Number && value.toDouble().isFinite()); p[doublePreferencesKey(name)]=value.toDouble() }
                "float" -> { require(value is Number && value.toFloat().isFinite()); p[floatPreferencesKey(name)]=value.toFloat() }
                else -> error("Unsupported preference type")
            }
        }
        return p
    }
    @Suppress("UNCHECKED_CAST")
    fun apply(target: MutablePreferences, source: Preferences, merge: Boolean) {
        if (!merge) target.clear()
        source.asMap().forEach { (key,value) -> if (!merge || key !in target.asMap()) target[key as Preferences.Key<Any>]=value }
    }
}
