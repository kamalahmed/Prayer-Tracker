package com.kamalahmed.prayerapp.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamalahmed.prayerapp.alarm.AlarmScheduler
import com.kamalahmed.prayerapp.nafl.QuranReminders
import com.kamalahmed.prayerapp.qada.QadaConfig
import com.kamalahmed.prayerapp.qada.QadaReminderWorker
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import com.kamalahmed.prayerapp.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

data class BackupUi(val busy: Boolean=false,val message: String?=null,val preview: String?=null,val snapshots: List<File> = emptyList(),val lastBackup: Long=0,val folder: String?=null,val status: BackupStatus=BackupStatus(),val error: Boolean=false,val operation: String?=null)

@HiltViewModel
class BackupViewModel @Inject constructor(private val app: Application,private val repo: BackupRepository,private val db: TrackerDatabase,private val scheduler: AlarmScheduler,private val quran: QuranReminders): AndroidViewModel(app) {
    val state=MutableStateFlow(BackupUi())
    private var pending: String?=null
    init { refresh() }
    private fun task(label: String="Working…",block: suspend () -> Unit) {
        if(state.value.busy) return
        state.value=state.value.copy(busy=true,message=null,error=false,operation=label)
        viewModelScope.launch {
            try { block() } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch(e: Exception) {
                com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal("Backup","Operation failed",e)
                state.value=state.value.copy(message="Backup operation failed: ${e.message ?: "please try again"}",error=true)
            } finally { state.value=state.value.copy(busy=false,operation=null) }
        }
    }
    fun pickerError(e: Exception) { state.value=state.value.copy(message="Could not open document picker: ${e.message}",error=true) }
    fun refresh() = task {
        val files=withContext(Dispatchers.IO) { repo.snapshots() }
        val error=app.getSharedPreferences("backup_metadata",Application.MODE_PRIVATE).getString("last_error",null)
        state.value=state.value.copy(snapshots=files,lastBackup=repo.lastBackup,folder=repo.folder,status=withContext(Dispatchers.IO) { repo.status() },message=error,error=error!=null)
    }
    fun export(uri: Uri, snapshot: File?=null)=task("Exporting backup…") {
        withContext(Dispatchers.IO) {
            val text=if(snapshot==null) repo.exportJson() else snapshot.readText()
            app.contentResolver.openOutputStream(uri,"wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() } ?: error("Cannot write selected document")
            repo.markExported()
        }
        state.value=state.value.copy(lastBackup=repo.lastBackup,message="Backup exported successfully. Keep this personal file somewhere safe.")
    }
    private fun readLimited(input: java.io.InputStream): String = input.use {
        val out=ByteArrayOutputStream(); val buffer=ByteArray(8192)
        while(true) { val n=it.read(buffer); if(n<0) break; require(out.size()+n<=BackupCodec.MAX_BYTES) { "Backup exceeds 32 MB" }; out.write(buffer,0,n) }
        out.toString(Charsets.UTF_8.name())
    }
    fun open(uri: Uri)=task("Reading and validating backup…") {
        val text=withContext(Dispatchers.IO) { readLimited(app.contentResolver.openInputStream(uri) ?: error("Cannot read selected document")) }
        val summary=withContext(Dispatchers.Default) { repo.preview(text) }
        pending=text; state.value=state.value.copy(preview=summary)
    }
    fun openSnapshot(file: File)=task {
        val text=withContext(Dispatchers.IO) { file.inputStream().use(::readLimited) }
        val summary=withContext(Dispatchers.Default) { repo.preview(text) }
        pending=text; state.value=state.value.copy(preview=summary)
    }
    fun dismissPreview() { if(!state.value.busy) { pending=null; state.value=state.value.copy(preview=null) } }
    fun restore(mode: RestoreMode)=task("Saving recovery point and restoring…") {
        // An accepted restore must also finish alarm/widget reconciliation if navigation clears this VM.
        withContext(kotlinx.coroutines.NonCancellable) {
            val text=pending ?: error("Select a backup first")
            val oldIds=withContext(Dispatchers.IO) { db.nafl().reminders().map { it.id } }
            repo.restore(text,mode)
            pending=null
            var warning=false
            withContext(Dispatchers.IO) {
                // Old deleted reminder IDs are no longer present in the DB, so cancel them explicitly.
                try { val am=app.getSystemService(android.app.AlarmManager::class.java); oldIds.forEach { am.cancel(quran.pending(it)) } } catch(e: Exception) { warning=true }
                try { scheduler.rescheduleAll() } catch(e: Exception) { warning=true }
                try { quran.reschedule() } catch(e: Exception) { warning=true }
                try { QadaReminderWorker.schedule(app,db.qada().config() ?: QadaConfig()) } catch(e: Exception) { warning=true }
                try { com.kamalahmed.prayerapp.summary.SummaryWorker.schedule(app,com.kamalahmed.prayerapp.data.settings.SettingsRepository(app).current()) } catch(e: Exception) { warning=true }
                try { WidgetUpdater.updateAll(app) } catch(e: Exception) { warning=true }
            }
            state.value=state.value.copy(preview=null,snapshots=withContext(Dispatchers.IO) { repo.snapshots() },lastBackup=repo.lastBackup,status=withContext(Dispatchers.IO) { repo.status() },error=warning,
                message=if(warning) "Data restored. Some reminders/widgets need a refresh; open their settings." else "Data restored; alarms rescheduled and widgets refreshed.")
        }
    }
    private suspend fun takeSnapshot() {
        val receipt=repo.snapshotWithReceipt()
        val message=if(receipt.folderError!=null) "Saved in 1 location · on this phone. Folder copy failed: ${receipt.folderError}"
            else "Saved in ${receipt.locations} ${if(receipt.locations==1) "location · on this phone" else "locations · this phone + chosen folder"}."
        state.value=state.value.copy(snapshots=withContext(Dispatchers.IO) { repo.snapshots() },lastBackup=repo.lastBackup,
            folder=repo.folder,status=withContext(Dispatchers.IO) { repo.status() },message=message,error=receipt.folderError!=null)
    }
    fun snapshotNow()=task("Saving and verifying snapshot…") { takeSnapshot() }
    fun chooseFolder(uri: Uri?)=task(if(uri==null) "Disabling folder export…" else "Saving and verifying both copies…") {
        withContext(Dispatchers.IO) { repo.setFolder(uri) }
        state.value=state.value.copy(folder=repo.folder,status=withContext(Dispatchers.IO) { repo.status() })
        if(uri!=null) takeSnapshot() else state.value=state.value.copy(message="Folder auto-export disabled. Phone snapshots remain enabled.")
    }
}
