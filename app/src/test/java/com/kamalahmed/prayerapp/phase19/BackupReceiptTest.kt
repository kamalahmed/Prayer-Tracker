package com.kamalahmed.prayerapp.phase19

import android.app.Application
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.backup.*
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.tracker.*
import com.kamalahmed.prayerapp.update.UpdatePrefs
import com.kamalahmed.prayerapp.widget.WidgetStyleStore
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=Application::class)
class BackupReceiptTest {
    private val ctx=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TrackerDatabase
    private lateinit var repo: BackupRepository
    private lateinit var provider: FilesProvider
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(ctx,TrackerDatabase::class.java).allowMainThreadQueries().build()
        repo=BackupRepository(ctx,db,SettingsRepository(ctx),WidgetStyleStore(ctx),UpdatePrefs(ctx))
        ctx.getSharedPreferences("backup_metadata",0).edit().clear().commit()
        provider=FilesProvider(File(ctx.cacheDir,"provider.json"))
        provider.attachInfo(ctx,android.content.pm.ProviderInfo().apply { authority="backups"; exported=true; grantUriPermissions=true })
        ShadowContentResolver.registerProviderInternal("backups",provider)
    }
    @After fun close() { db.close() }
    private fun folder() { ctx.getSharedPreferences("backup_metadata",0).edit().putString("auto_folder","content://backups/tree/Documents").commit() }
    @Test fun localOnlyReceiptHasOneVerifiedDestination()=runBlocking {
        val receipt=repo.snapshotWithReceipt()
        assertEquals(1,receipt.locations);assertTrue(receipt.localAt>0);assertEquals(0L,repo.status().folderAt)
        assertTrue(receipt.file.readText().contains("formatVersion"));assertEquals(receipt.localAt,repo.status().localAt)
    }
    @Test fun folderReceiptRequiresARealReadableFileAndHasTwoDestinations()=runBlocking {
        folder();val receipt=repo.snapshotWithReceipt()
        assertEquals(2,receipt.locations);assertTrue(provider.file.isFile);assertEquals(receipt.file.readText(),provider.file.readText())
        assertTrue(receipt.folderAt>0);assertEquals(receipt.folderAt,repo.status().folderAt);assertNotNull(repo.status().folderUri)
    }
    @Test fun failedFolderDoesNotAdvanceItsTimestampOrHideTheLocalSuccess()=runBlocking {
        folder();provider.fail=true
        val receipt=repo.snapshotWithReceipt()
        assertEquals(1,receipt.locations);assertTrue(receipt.file.isFile);assertTrue(repo.status().localAt>0)
        assertEquals(0L,repo.status().folderAt);assertNotNull(receipt.folderError);assertNotNull(repo.status().folderError)
        assertTrue(runCatching { repo.dailySnapshot() }.isFailure) // background worker can retry
    }
    @Test fun previousFolderSuccessIsNotFalselyStampedOnAFailedRetry()=runBlocking {
        folder();val first=repo.snapshotWithReceipt();provider.fail=true
        val retry=repo.snapshotWithReceipt()
        assertEquals(1,retry.locations);assertEquals(first.folderAt,repo.status().folderAt);assertNotNull(repo.status().folderError)
    }
    @Test fun aProviderThatSilentlyTruncatesIsNotReportedAsSuccessful()=runBlocking {
        folder();provider.truncate=true
        val receipt=repo.snapshotWithReceipt()
        assertEquals(1,receipt.locations);assertNotNull(receipt.folderError);assertEquals(0L,repo.status().folderAt)
    }
    class FilesProvider(val file: File): ContentProvider() {
        var fail=false;var truncate=false
        override fun onCreate()=true
        override fun call(method: String,arg: String?,extras: Bundle?): Bundle {
            if(fail) throw java.io.IOException("Folder unavailable")
            return Bundle().apply { putParcelable("uri",Uri.parse("content://backups/document/snapshot")) }
        }
        override fun openFile(uri: Uri,mode: String): ParcelFileDescriptor {
            if(fail) throw java.io.FileNotFoundException("Permission revoked")
            if(mode=="r" && truncate) file.writeText("truncated")
            return ParcelFileDescriptor.open(file,if(mode=="r") ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
        }
        override fun query(uri: Uri,projection: Array<out String>?,selection: String?,args: Array<out String>?,sort: String?): Cursor?=null
        override fun getType(uri: Uri)="application/json"
        override fun insert(uri: Uri,values: ContentValues?): Uri?=null
        override fun update(uri: Uri,values: ContentValues?,selection: String?,args: Array<out String>?)=0
        override fun delete(uri: Uri,selection: String?,args: Array<out String>?)=0
    }
}
