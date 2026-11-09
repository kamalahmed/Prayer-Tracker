package com.kamalahmed.prayerapp.phase21

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.qada.QadaHistory
import com.kamalahmed.prayerapp.qada.QadaPrayer
import com.kamalahmed.prayerapp.qada.QadaRepository
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QadaAggregateDbTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: TrackerDatabase
    private lateinit var repo: QadaRepository
    private val today = LocalDate.now()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ctx, TrackerDatabase::class.java).allowMainThreadQueries().build()
        repo = QadaRepository(db.qada())
    }
    @After fun close() = db.close()

    @Test fun eighteenThousandEntriesUseAggregatesOnly() = runBlocking {
        val w = db.openHelper.writableDatabase
        w.beginTransaction()
        try {
            for (i in 0 until 18_250) w.execSQL("INSERT INTO qada_entry(prayer,count,date,at) VALUES(?,?,?,?)",
                arrayOf<Any>(QadaPrayer.fard[i % 5].name, 1, today.minusDays((i / 5).toLong()).toString(), i.toLong()))
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
        val t0 = System.nanoTime()
        val totals = repo.totals.first()
        val days = repo.dayTotals.first()
        val breakdown = repo.breakdown(QadaHistory.dayWindowStart(today)).first()
        val rows = QadaHistory.build(days, breakdown, today)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(5, totals.size); assertEquals(18_250, totals.values.sum())
        assertEquals(3650, days.size)
        assertEquals(14, breakdown.size) // only the recent window is expanded per prayer
        assertTrue("rows=${rows.size}", rows.size < 40)
        assertTrue("took $ms ms", ms < 5_000)
        assertEquals(18_250, db.qada().entryCount())
    }

    @Test fun editDayRaisesAndTrimsNewestFirstWithoutTouchingOtherDays() = runBlocking {
        val day = today.minusDays(2)
        repo.log(QadaPrayer.FAJR, 2, day); repo.log(QadaPrayer.FAJR, 3, day); repo.log(QadaPrayer.DHUHR, 4, day); repo.log(QadaPrayer.FAJR, 7, today)
        assertTrue(repo.setDayCount(day, QadaPrayer.FAJR, 7) != null)
        assertEquals(7, db.qada().entriesOn(day.toString(), "FAJR").sumOf { it.count })
        assertNull(repo.setDayCount(day, QadaPrayer.FAJR, 1)) // trims the +2 edit and the newest batch
        val left = db.qada().entriesOn(day.toString(), "FAJR")
        assertEquals(1, left.sumOf { it.count }); assertEquals(1, left.size)
        repo.setDayCount(day, QadaPrayer.FAJR, 0)
        assertTrue(db.qada().entriesOn(day.toString(), "FAJR").isEmpty())
        assertEquals(4, db.qada().entriesOn(day.toString(), "DHUHR").sumOf { it.count })
        assertEquals(7, db.qada().entriesOn(today.toString(), "FAJR").sumOf { it.count })
        assertTrue(runCatching { repo.setDayCount(today.plusDays(1), QadaPrayer.FAJR, 1) }.isFailure)
    }

    @Test fun undoRemovesExactlyOneActionAndSameTimeCount() = runBlocking {
        repo.log(QadaPrayer.ASR, 1)
        val day = repo.logAll(QadaPrayer.fard.map { it to 1 })
        assertEquals(5, day.size)
        assertEquals(5, repo.deleteIds(day))
        assertEquals(1, db.qada().entryCount())
        assertEquals(0, repo.deleteIds(emptyList()))
        assertEquals(1, repo.countOnUntil(today, Long.MAX_VALUE))
        assertEquals(0, repo.countOnUntil(today, 0))
    }

    /** beta.12 databases (schema v4) open unchanged: no migration is needed or run, and every row is kept. */
    @Test fun beta12DatabaseOpensWithoutMigrationAndKeepsEveryRow() = runBlocking {
        val name = "beta12-v4.db"
        ctx.deleteDatabase(name)
        val schema = org.json.JSONObject(File("schemas/com.kamalahmed.prayerapp.tracker.TrackerDatabase/4.json").readText()).getJSONObject("database")
        val path = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { raw ->
            val ents = schema.getJSONArray("entities")
            for (i in 0 until ents.length()) {
                val e = ents.getJSONObject(i)
                raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                e.optJSONArray("indices")?.let { idx -> for (j in 0 until idx.length()) raw.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))) }
            }
            for (i in 0 until schema.getJSONArray("setupQueries").length()) raw.execSQL(schema.getJSONArray("setupQueries").getString(i))
            raw.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.getString("identityHash")}')")
            raw.execSQL("INSERT INTO qada_debt VALUES ('FAJR', 3650)")
            raw.execSQL("INSERT INTO qada_entry(prayer,count,date,at) VALUES ('FAJR', 3, '2026-11-01', 1), ('FAJR', 2, '2026-11-02', 2), ('ISHA', 1, '2026-11-02', 3)")
            raw.execSQL("INSERT INTO qada_config VALUES (0, 0, 5, 1, 1200, 1)")
            raw.execSQL("INSERT INTO prayer_log VALUES ('2026-11-01','FAJR','ON_TIME',1)")
            raw.version = 4
        }
        val room = Room.databaseBuilder(ctx, TrackerDatabase::class.java, name).addMigrations(*TrackerDatabase.MIGRATIONS).allowMainThreadQueries().build()
        try {
            val r = QadaRepository(room.qada())
            assertEquals(mapOf(QadaPrayer.FAJR to 5, QadaPrayer.ISHA to 1), r.totals.first())
            assertEquals(mapOf(LocalDate.parse("2026-11-01") to 3, LocalDate.parse("2026-11-02") to 3), r.dayTotals.first())
            assertEquals(3, room.qada().entries().size)
            assertEquals(5, room.qada().config()!!.dailyGoal)
            assertEquals(1, room.dao().all().size)
            assertEquals(4, room.openHelper.readableDatabase.version)
        } finally { room.close() }
    }
}
