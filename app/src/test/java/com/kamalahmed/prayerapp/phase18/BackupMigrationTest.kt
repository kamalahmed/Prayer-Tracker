package com.kamalahmed.prayerapp.phase18

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.backup.*
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=Application::class)
class BackupMigrationTest {
    @Test fun snapshotPrecedesActualRoomMigrationAndRetainsOldSchemaData() = runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<Context>(); val name="phase18-migration.db"; ctx.deleteDatabase(name)
        val schema=JSONObject(File("schemas/com.kamalahmed.prayerapp.tracker.TrackerDatabase/3.json").readText()).getJSONObject("database")
        val path=ctx.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path,null).use { sql ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) { val ent=entities.getJSONObject(i); sql.execSQL(ent.getString("createSql").replace("\${TABLE_NAME}",ent.getString("tableName")))
                val idx=ent.optJSONArray("indices"); if(idx!=null) for(j in 0 until idx.length()) sql.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",ent.getString("tableName"))) }
            val setup=schema.getJSONArray("setupQueries"); for(i in 0 until setup.length()) sql.execSQL(setup.getString(i))
            sql.execSQL("INSERT INTO prayer_log VALUES ('2026-11-01','FAJR','ON_TIME',1)")
            sql.execSQL("INSERT INTO nafl_item VALUES ('dhikr','Dhikr','WORSHIP',0,0,1,9)")
            sql.execSQL("INSERT INTO nafl_log VALUES ('2026-11-01','dhikr',345,0,0)")
            sql.version=3
        }
        val db=Room.databaseBuilder(ctx,TrackerDatabase::class.java,name).addMigrations(*BackupSnapshots.migrations(ctx,TrackerDatabase.MIGRATIONS)).allowMainThreadQueries().build()
        try {
            assertEquals(345,db.nafl().logs().single().count)
            assertTrue(db.nafl().items().single().isCounter)
            val file=BackupSnapshots.list(ctx).first { it.name.startsWith("before-migration-v3-") }
            val old=JSONObject(file.readText())
            assertEquals(3,old.getInt("databaseVersion"))
            assertFalse(old.getJSONObject("tables").getJSONArray("nafl_item").getJSONObject(0).has("type"))
            assertEquals(345,BackupCodec.decode(file.readText()).getJSONObject("tables").getJSONArray("nafl_log").getJSONObject(0).getInt("count"))
        } finally { db.close(); ctx.deleteDatabase(name) }
    }
    @Test fun androidBackupExplicitlyIncludesDatabaseDataStoreAndSnapshotsNotUpdater() {
        val base=listOf(File("src/main"),File("app/src/main")).first { it.exists() }
        assertTrue(File(base,"AndroidManifest.xml").readText().contains("allowBackup=\"true\""))
        for(name in listOf("backup_rules.xml","data_extraction_rules.xml")) {
            val xml=File(base,"res/xml/$name").readText()
            for(p in listOf("tracker.db","datastore/","backups/")) assertTrue(xml.contains(p))
            assertFalse(xml.contains("updater.xml")); assertFalse(xml.contains("path=\"updates/\""))
        }
    }
}
