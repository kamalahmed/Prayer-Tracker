package com.kamalahmed.prayerapp.phase19

import android.app.Application
import android.content.Context
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.nafl.*
import com.kamalahmed.prayerapp.tracker.TrackerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=Application::class)
class CounterVisualTest {
    @Test fun ringHasTransparentCentreAndColoursChangeAcrossLaps() {
        val a=CounterRingPainter.bitmap(200f,1f,CounterProgress.of(50,100),0x22444444)
        val b=CounterRingPainter.bitmap(200f,1f,CounterProgress.of(150,100),0x22444444)
        assertEquals(0,Color.alpha(a.getPixel(100,100)));assertEquals(0,Color.alpha(a.getPixel(0,0)))
        assertNotEquals(a.getPixel(185,100),b.getPixel(185,100))
        assertEquals(720,CounterRingPainter.bitmap(10000f,4f,CounterProgress.of(1000000,1),Color.GRAY).width)
    }
    @Test fun freeCountOnlyHasANeutralTrack() {
        val a=CounterRingPainter.bitmap(100f,1f,CounterProgress.of(0,0),Color.GRAY)
        val b=CounterRingPainter.bitmap(100f,1f,CounterProgress.of(999,0),Color.GRAY)
        assertTrue(a.sameAs(b))
    }
    @Test fun everyNewTargetLapSignalsFeedbackAndUndoDoesNot()=runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,TrackerDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.nafl().saveItem(NaflItem("test","Test",type="COUNTER",dailyTarget=100))
            db.nafl().saveLog(NaflLog("2026-11-08","test",199))
            assertTrue(db.nafl().adjustCounter("2026-11-08","test",1).targetReached)
            assertFalse(db.nafl().adjustCounter("2026-11-08","test",1).targetReached)
            assertFalse(db.nafl().adjustCounter("2026-11-08","test",-1).targetReached)
            assertEquals(200,db.nafl().log("2026-11-08","test")!!.count)
        } finally { db.close() }
    }
}
