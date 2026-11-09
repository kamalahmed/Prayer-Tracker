package com.kamalahmed.prayerapp.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import com.kamalahmed.prayerapp.update.UpdatePrefs
import com.kamalahmed.prayerapp.widget.WidgetStyleStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class RestoreMode { REPLACE, MERGE }
data class SnapshotReceipt(val file: File,val localAt: Long,val folderAt: Long=0,val folderUri: String?=null,val folderError: String?=null) {
    val locations get()=if(folderUri!=null) 2 else 1
}
data class BackupStatus(val localAt: Long=0,val localName: String?=null,val folderAt: Long=0,val folderUri: String?=null,val folderError: String?=null)


object BackupUserPrefs {
    suspend fun capture(ctx: Context): JSONObject {
        val updates=UpdatePrefs(ctx)
        val tasbih=ctx.getSharedPreferences("tasbih",Context.MODE_PRIVATE)
        val settingJson=SettingsRepository(ctx).backupExport()
        val sounds=JSONObject()
        val soundPath=settingJson.optJSONObject("sound_uri")?.optString("value")
        if(settingJson.optJSONObject("sound_source")?.optString("value")=="CUSTOM_FILE" && soundPath!=null) {
            val name=soundPath.substringAfterLast('/')
            require(name.matches(Regex("custom\\.[A-Za-z0-9]{1,5}"))) { "Invalid custom sound name" }
            // Absolute paths can change after Android restores to a different user/profile.
            val file=File(ctx.filesDir,"sounds/$name")
            if(file.isFile) {
                require(file.length()<=16L*1024*1024) { "Custom sound is too large for a JSON backup" }
                sounds.put(file.name,android.util.Base64.encodeToString(file.readBytes(),android.util.Base64.NO_WRAP))
            }
        }
        return JSONObject().put("sounds",sounds).put("settings",settingJson).put("widgets",WidgetStyleStore(ctx).backupExport())
            .put("tasbih",JSONObject().put("volume",tasbih.getBoolean("volume",false)).put("haptics",tasbih.getBoolean("haptics",true)))
            .put("insights",JSONObject().put("coach_hidden",ctx.getSharedPreferences("insights",Context.MODE_PRIVATE).getBoolean("coach_hidden",false)))
            .put("updates",JSONObject().put("auto",updates.autoCheck.value).put("wifi",updates.wifiOnly.value).put("beta",updates.includeBeta.value))
    }
}

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: TrackerDatabase,
    private val settings: SettingsRepository,
    private val styles: WidgetStyleStore,
    private val updates: UpdatePrefs,
) {
    fun status(): BackupStatus {
        val legacy=if(meta.getLong("last_local",0)==0L) snapshots().firstOrNull() else null
        return BackupStatus(meta.getLong("last_local",legacy?.lastModified() ?: 0),meta.getString("local_name",legacy?.name),
            meta.getLong("last_folder",0),meta.getString("folder_document",null),meta.getString("folder_error",null))
    }
    suspend fun snapshotWithReceipt(day: LocalDate=LocalDate.now()): SnapshotReceipt=withContext(Dispatchers.IO) {
        mutex.withLock {
            val text=exportUnlocked()
            val file=BackupSnapshots.write(ctx,"daily-${day.format(DateTimeFormatter.BASIC_ISO_DATE)}.json",text)
            val localAt=status().localAt
            val destination=folder
            if(destination==null) { check(meta.edit().remove("last_error").commit()); return@withLock SnapshotReceipt(file,localAt) }
            try {
                val document=BackupSnapshots.exportToFolder(ctx,Uri.parse(destination),"prayer-backup-${day.format(DateTimeFormatter.BASIC_ISO_DATE)}-${System.currentTimeMillis()}.json",text)
                val at=System.currentTimeMillis()
                check(meta.edit().putLong("last_folder",at).putString("folder_document",document.toString()).remove("folder_error").remove("last_error").commit()) { "Copy saved, but its receipt could not be recorded" }
                SnapshotReceipt(file,localAt,at,document.toString())
            } catch(e: Exception) {
                com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal("Backup","Folder copy failed; local snapshot is safe",e)
                val message=e.message?.take(180) ?: "Folder unavailable. Choose the folder again."
                check(meta.edit().putString("folder_error",message).commit())
                SnapshotReceipt(file,localAt,folderError=message)
            }
        }
    }
    private val mutex=Mutex()
    private val meta get()=ctx.getSharedPreferences("backup_metadata",Context.MODE_PRIVATE)
    val lastBackup: Long get()=meta.getLong("last_backup",0)
    val folder: String? get()=meta.getString("auto_folder",null)
    fun markExported() { check(meta.edit().putLong("last_backup",System.currentTimeMillis()).remove("last_error").commit()) { "Could not save backup status" } }
    fun setFolder(uri: Uri?) {
        if(uri!=null) {
            require(uri.scheme=="content" && android.provider.DocumentsContract.isTreeUri(uri)) { "Choose a document folder" }
            ctx.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val edit=meta.edit().putString("auto_folder",uri?.toString())
        if(folder!=uri?.toString()) edit.remove("last_folder").remove("folder_document").remove("folder_error")
        check(edit.commit())
    }
    private suspend fun exportUnlocked(): String = db.withTransaction { BackupCodec.capture(db.openHelper.writableDatabase,4,BackupUserPrefs.capture(ctx)) }
    suspend fun exportJson(): String=withContext(Dispatchers.IO) { mutex.withLock { exportUnlocked() } }
    fun preview(text: String): String {
        val root=BackupCodec.decode(text); validatePreferences(root.getJSONObject("preferences"))
        return BackupCodec.summary(root)
    }
    private fun validatePreferences(p: JSONObject) {
        require(p.keys().asSequence().all { it in setOf("settings","widgets","tasbih","updates","sounds","insights") && p.get(it) is JSONObject })
        p.optJSONObject("sounds")?.let { sounds ->
            require(sounds.length()<=3)
            sounds.keys().forEach { name ->
                require(name.matches(Regex("custom\\.[A-Za-z0-9]{1,5}")))
                require(android.util.Base64.decode(sounds.getString(name),android.util.Base64.NO_WRAP).size<=16*1024*1024)
            }
        }
        settings.validateBackup(p.optJSONObject("settings") ?: JSONObject())
        styles.validateBackup(p.optJSONObject("widgets") ?: JSONObject())
        listOf("tasbih" to setOf("volume","haptics"),"updates" to setOf("auto","wifi","beta"),"insights" to setOf("coach_hidden")).forEach { (key,allowed) ->
            p.optJSONObject(key)?.let { obj -> require(obj.keys().asSequence().all { it in allowed && obj.get(it) is Boolean }) }
        }
    }
    private suspend fun applyPreferences(p: JSONObject, merge: Boolean) {
        val settingsJson=JSONObject((p.optJSONObject("settings") ?: JSONObject()).toString())
        if(!merge || !settings.backupExport().has("sound_source")) {
            val sounds=p.optJSONObject("sounds") ?: JSONObject()
            sounds.keys().forEach { name ->
                val file=File(ctx.filesDir,"sounds/$name"); check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
                val atomic=android.util.AtomicFile(file); val stream=atomic.startWrite()
                try { stream.write(android.util.Base64.decode(sounds.getString(name),android.util.Base64.NO_WRAP)); atomic.finishWrite(stream) } catch(e: Exception) { atomic.failWrite(stream); throw e }
            }
            if(settingsJson.optJSONObject("sound_source")?.optString("value")=="CUSTOM_FILE") {
                val name=settingsJson.optJSONObject("sound_uri")?.optString("value")?.substringAfterLast('/')
                if(name!=null && sounds.has(name)) settingsJson.getJSONObject("sound_uri").put("value",File(ctx.filesDir,"sounds/$name").absolutePath)
                else { settingsJson.put("sound_source",JSONObject().put("type","string").put("value","BUNDLED")); settingsJson.remove("sound_uri") }
            }
        }
        settings.backupRestore(settingsJson,merge)
        styles.backupRestore(p.optJSONObject("widgets") ?: JSONObject(),merge)
        p.optJSONObject("tasbih")?.let { obj ->
            val sp=ctx.getSharedPreferences("tasbih",Context.MODE_PRIVATE); val edit=sp.edit()
            if(!merge) edit.clear()
            obj.keys().forEach { k -> if(!merge || !sp.contains(k)) edit.putBoolean(k,obj.getBoolean(k)) }
            check(edit.commit()) { "Could not restore counter settings" }
        }
        p.optJSONObject("insights")?.let { obj ->
            val sp=ctx.getSharedPreferences("insights",Context.MODE_PRIVATE)
            if(obj.has("coach_hidden") && (!merge || !sp.contains("coach_hidden"))) check(sp.edit().putBoolean("coach_hidden",obj.getBoolean("coach_hidden")).commit()) { "Could not restore coach settings" }
        }
        if(!merge) p.optJSONObject("updates")?.let { obj ->
            if(obj.has("auto")) updates.setAutoCheck(obj.getBoolean("auto"))
            if(obj.has("wifi")) updates.setWifiOnly(obj.getBoolean("wifi"))
            if(obj.has("beta")) updates.setIncludeBeta(obj.getBoolean("beta"))
        }
    }
    suspend fun restore(text: String, mode: RestoreMode)=withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
        mutex.withLock {
            // Fully validate BEFORE snapshot or destructive writes. Never fall back to empty data on parse/I/O errors.
            val root=BackupCodec.decode(text); val prefs=root.getJSONObject("preferences"); validatePreferences(prefs)
            val safety=exportUnlocked()
            BackupSnapshots.write(ctx,"before-import-${System.currentTimeMillis()}-${System.nanoTime()}.json",safety)
            val oldPrefs=JSONObject(safety).getJSONObject("preferences")
            try {
                db.withTransaction {
                    val sql=db.openHelper.writableDatabase
                    if(mode==RestoreMode.REPLACE) BackupCodec.columns.keys.reversed().forEach { sql.execSQL("DELETE FROM `$it`") }
                    BackupCodec.columns.keys.forEach { table ->
                        val a=root.getJSONObject("tables").getJSONArray(table)
                        for(i in 0 until a.length()) {
                            val row=a.getJSONObject(i)
                            if(mode==RestoreMode.MERGE && mergeExisting(table,row)) continue
                            val keys=row.keys().asSequence().filterNot { mode==RestoreMode.MERGE && table in setOf("qada_entry","quran_reminder") && it=="id" }.toList()
                            val names=keys.joinToString(",") { "`$it`" }
                            val bind=keys.joinToString(",") { "?" }
                            sql.execSQL("INSERT OR REPLACE INTO `$table` ($names) VALUES ($bind)",keys.map { row.get(it) }.toTypedArray())
                        }
                    }
                    db.nafl().insertDefaults(com.kamalahmed.prayerapp.nafl.NaflDefaults.items)
                    // DataStore failure rolls back the DB transaction; original preferences are restored below.
                    applyPreferences(prefs,mode==RestoreMode.MERGE)
                }
            } catch(e: Exception) {
                try { applyPreferences(oldPrefs,false) } catch(recovery: Exception) { e.addSuppressed(recovery) }
                throw e
            }
            db.invalidationTracker.refreshVersionsAsync()
        }
    }
    private fun mergeExisting(table: String,row: JSONObject): Boolean {
        val sql=db.openHelper.writableDatabase
        fun exists(where: String,args: Array<Any>)=sql.query("SELECT 1 FROM `$table` WHERE $where",args).use { it.moveToFirst() }
        return when(table) {
            "prayer_log" -> exists("date=? AND prayer=? AND updatedAt>=?",arrayOf(row.getString("date"),row.getString("prayer"),row.getLong("updatedAt")))
            "nafl_log" -> {
                val date=row.getString("date"); val id=row.getString("itemId")
                if(!exists("date=? AND itemId=?",arrayOf(date,id))) false else {
                    sql.execSQL("UPDATE nafl_log SET count=MAX(count,?), pages=MAX(pages,?), minutes=MAX(minutes,?) WHERE date=? AND itemId=?",arrayOf(row.getLong("count"),row.getLong("pages"),row.getLong("minutes"),date,id)); true
                }
            }
            "qada_entry" -> exists("prayer=? AND count=? AND date=? AND at=?",arrayOf(row.getString("prayer"),row.getLong("count"),row.getString("date"),row.getLong("at")))
            "quran_reminder" -> exists("minute=? AND alarmSound=?",arrayOf(row.getLong("minute"),row.getLong("alarmSound")))
            "qada_debt" -> {
                val prayer=row.getString("prayer")
                if(!exists("prayer=?",arrayOf(prayer))) false else { sql.execSQL("UPDATE qada_debt SET owed=MAX(owed,?) WHERE prayer=?",arrayOf(row.getLong("owed"),prayer)); true }
            }
            else -> exists("id=?",arrayOf(row.get("id")))
        }
    }
    suspend fun ensureTodaySnapshot() {
        val name="daily-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
        if(withContext(Dispatchers.IO) { snapshots().none { it.name==name } }) dailySnapshot()
    }
    fun snapshots(): List<File> = BackupSnapshots.list(ctx)
    /** Existing worker API: retry a failed folder copy while preserving the successful phone snapshot. */
    suspend fun dailySnapshot(day: LocalDate=LocalDate.now()): File {
        val receipt=snapshotWithReceipt(day)
        receipt.folderError?.let { throw java.io.IOException("Phone snapshot saved; folder copy failed: $it") }
        return receipt.file
    }
}
