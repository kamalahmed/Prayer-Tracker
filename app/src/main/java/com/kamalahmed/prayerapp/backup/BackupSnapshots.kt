package com.kamalahmed.prayerapp.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

object BackupSnapshots {
    private fun dir(ctx: Context)=File(ctx.filesDir,"backups").apply { check(mkdirs() || isDirectory) { "Backup storage unavailable" } }
    @Synchronized fun list(ctx: Context): List<File> = dir(ctx).listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    @Synchronized fun write(ctx: Context,name: String,text: String): File {
        require(name.matches(Regex("[A-Za-z0-9_-]+\\.json")))
        val file=File(dir(ctx),name); val atomic=AtomicFile(file); val stream=atomic.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) } catch(e: Exception) { atomic.failWrite(stream); throw e }
        check(file.readText(Charsets.UTF_8)==text) { "Backup could not be verified on disk" }
        // Seven rolling daily snapshots plus ten independent pre-import/migration recovery points.
        val files=list(ctx)
        val newest=files.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
        newest.filter { it.name.startsWith("daily-") && it != file }.drop(if(name.startsWith("daily-")) 6 else 7).forEach { check(it.delete()) }
        newest.filter { !it.name.startsWith("daily-") && it != file }.drop(if(name.startsWith("daily-")) 10 else 9).forEach { check(it.delete()) }
        return file
    }
    fun exportToFolder(ctx: Context,tree: Uri,name: String,text: String) {
        val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        val document=DocumentsContract.createDocument(ctx.contentResolver,parent,"application/json",name) ?: error("Could not create backup in selected folder")
        ctx.contentResolver.openOutputStream(document,"w")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Could not write backup folder")
    }
    fun migrations(ctx: Context, originals: Array<Migration>): Array<Migration> = originals.map { original -> object : Migration(original.startVersion,original.endVersion) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Fail closed: never mutate a database if its recovery snapshot cannot be committed to disk.
            val prefs=runBlocking(Dispatchers.IO) { BackupUserPrefs.capture(ctx) }
            val text=BackupCodec.capture(db,original.startVersion,prefs)
            write(ctx,"before-migration-v${original.startVersion}-${System.currentTimeMillis()}.json",text)
            original.migrate(db)
        }
    } }.toTypedArray()
}
