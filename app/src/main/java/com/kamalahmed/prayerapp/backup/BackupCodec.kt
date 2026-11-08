package com.kamalahmed.prayerapp.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A portable whitelist format: no SQL, filenames, classes or executable instructions from the input. */
object BackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    val columns = linkedMapOf(
        "prayer_log" to "date:S,prayer:S,status:S,updatedAt:N",
        "qada_debt" to "prayer:S,owed:N",
        "qada_entry" to "id:N,prayer:S,count:N,date:S,at:N",
        "qada_config" to "id:N,witr:N,dailyGoal:N,reminder:N,reminderMinute:N,setupDone:N",
        "nafl_item" to "id:S,name:S,group:S,rakaat:N,baseline:N,enabled:N,position:N,type:S,dailyTarget:N",
        "nafl_log" to "date:S,itemId:S,count:N,pages:N,minutes:N",
        "quran_reminder" to "id:N,minute:N,enabled:N,alarmSound:N",
    ).mapValues { (_,v) -> v.split(',').associate { it.substringBefore(':') to it.substringAfter(':') } }

    fun capture(db: SupportSQLiteDatabase, version: Int, preferences: JSONObject): String {
        val tables = JSONObject()
        columns.keys.forEach { table ->
            val exists = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }
            if (exists) {
                val rows = JSONArray()
                db.query("SELECT * FROM `$table`").use { c -> while (c.moveToNext()) {
                    val row=JSONObject()
                    c.columnNames.forEachIndexed { i,name -> row.put(name, if (c.getType(i)==android.database.Cursor.FIELD_TYPE_INTEGER) c.getLong(i) else c.getString(i)) }
                    rows.put(row)
                } }
                tables.put(table,rows)
            }
        }
        return JSONObject().put("format","prayer-app-backup").put("formatVersion",1).put("databaseVersion",version)
            .put("createdAt",System.currentTimeMillis()).put("tables",tables).put("preferences",preferences).toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds 32 MB" } }
    }

    fun decode(text: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds 32 MB" }
        val root=JSONObject(text)
        require(root.getString("format")=="prayer-app-backup") { "Not a Prayer App backup" }
        require(root.get("formatVersion") is Number && root.getDouble("formatVersion")==root.getInt("formatVersion").toDouble())
        require(root.get("databaseVersion") is Number && root.getDouble("databaseVersion")==root.getInt("databaseVersion").toDouble())
        require(root.getInt("formatVersion") in 0..1) { "Unsupported backup format version" }
        val version=root.getInt("databaseVersion")
        require(version in 1..4) { "Unsupported database version" }
        val tables=root.getJSONObject("tables")
        require(tables.keys().asSequence().all { it in columns }) { "Unknown table" }
        val required=when(version) { 1 -> setOf("prayer_log"); 2 -> columns.keys.take(4).toSet(); else -> columns.keys }
        require(required.all { tables.has(it) }) { "Backup is missing a table" }
        var total=0
        columns.forEach { (table,spec) ->
            val rows=if(tables.has(table)) tables.getJSONArray(table) else JSONArray().also { tables.put(table,it) }
            total += rows.length(); require(total<=250_000) { "Too many backup rows" }
            val seen=HashSet<String>()
            for (i in 0 until rows.length()) {
                val row=rows.getJSONObject(i)
                if (table=="nafl_item" && version<4) {
                    if (!row.has("type")) row.put("type",if(row.optString("id")=="dhikr") "COUNTER" else "CHECKBOX")
                    if (!row.has("dailyTarget")) row.put("dailyTarget",0)
                }
                require(row.keys().asSequence().toSet()==spec.keys) { "Invalid $table columns" }
                spec.forEach { (key,type) ->
                    val value=row.get(key)
                    if (type=="S") require(value is String && value.length<=2000) { "Invalid $table.$key" }
                    else {
                        require(value is Number && value.toDouble().isFinite() && value.toDouble()==value.toLong().toDouble() && value.toLong()>=0) { "Invalid $table.$key" }
                        if (key !in setOf("at","updatedAt","id")) require(value.toLong()<=1_000_000) { "Out of range $table.$key" }
                    }
                }
                if (row.has("date")) { require(row.getString("date").matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))); LocalDate.parse(row.getString("date")) }
                if (table=="qada_config") require(row.getLong("id")==0L)
                if (row.has("prayer")) require(row.getString("prayer") in if(table.startsWith("qada")) setOf("FAJR","DHUHR","ASR","MAGHRIB","ISHA","WITR") else com.kamalahmed.prayerapp.core.model.Prayer.entries.map { it.name }.toSet())
                if (table=="prayer_log") require(row.getString("status") in setOf("ON_TIME","LATE","MISSED"))
                if (table=="nafl_item") {
                    require(row.getString("type") in setOf("CHECKBOX","COUNTER"))
                    require(row.getString("group") in setOf("SUNNAH","WORSHIP"))
                    require(row.getString("id").matches(Regex("[A-Za-z0-9_-]{1,80}")))
                    require(row.getString("name").isNotBlank() && row.getString("name").length<=100)
                }
                for (key in listOf("witr","reminder","setupDone","baseline","enabled","alarmSound")) if(row.has(key)) require(row.getInt(key) in 0..1)
                for (key in listOf("minute","reminderMinute")) if(row.has(key)) require(row.getInt(key) in 0..1439)
                val pk=when(table) { "prayer_log" -> "${row.getString("date")}/${row.getString("prayer")}"; "nafl_log" -> "${row.getString("date")}/${row.getString("itemId")}"; "qada_debt" -> row.getString("prayer"); else -> row.get("id").toString() }
                require(seen.add(pk)) { "Duplicate $table record" }
            }
        }
        val itemIds=tables.getJSONArray("nafl_item").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") }.toSet() }
        tables.getJSONArray("nafl_log").let { a -> for(i in 0 until a.length()) require(a.getJSONObject(i).getString("itemId") in itemIds) { "Orphan worship log" } }
        require(root.optJSONObject("preferences") != null) { "Missing preferences" }
        return root
    }

    fun summary(root: JSONObject): String {
        val tables=root.getJSONObject("tables")
        val dates=columns.keys.flatMap { t -> val a=tables.getJSONArray(t); (0 until a.length()).mapNotNull { a.getJSONObject(it).optString("date").takeIf(String::isNotEmpty) } }.sorted()
        return "Database v${root.getInt("databaseVersion")} · format v${root.getInt("formatVersion")}\n" + columns.keys.joinToString("\n") { "$it: ${tables.getJSONArray(it).length()}" } +
            "\nDate range: ${dates.firstOrNull() ?: "none"} — ${dates.lastOrNull() ?: "none"}"
    }
}
