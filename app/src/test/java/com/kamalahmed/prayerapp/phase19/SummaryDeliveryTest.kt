package com.kamalahmed.prayerapp.phase19

import android.Manifest
import android.app.*
import android.content.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.summary.*
import com.kamalahmed.prayerapp.data.prayer.PrayerRepository
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import com.kamalahmed.prayerapp.tracker.*
import com.kamalahmed.prayerapp.nafl.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],application=Application::class)
class SummaryDeliveryTest {
    private val ctx=ApplicationProvider.getApplicationContext<Application>()
    private val settings=SettingsRepository(ctx)
    private lateinit var db: TrackerDatabase
    private lateinit var notifier: SummaryNotifier
    private val now=ZonedDateTime.parse("2026-11-08T22:00:00+04:00[Asia/Dubai]")
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(ctx,TrackerDatabase::class.java).allowMainThreadQueries().build()
        notifier=SummaryNotifier(ctx,SummarySource(db),settings,PrayerRepository(settings))
        ctx.getSharedPreferences("summary_delivery",0).edit().clear().commit()
        Shadows.shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { settings.update { it.copy(summaryDaily=true,summaryWeekly=false) };db.nafl().saveItem(NaflItem("istighfar","Istighfar",type="COUNTER"));db.nafl().saveLog(NaflLog(now.toLocalDate().toString(),"istighfar",350)) }
    }
    @After fun close() { db.close() }
    @Test fun postsPrivateNotificationWithActionAndCorrectPeriodDeepLinkOnce()=runBlocking {
        assertEquals(1,notifier.check(now));assertEquals(0,notifier.check(now))
        val n=Shadows.shadowOf(ctx.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("350 dhikr"))
        assertEquals(Notification.VISIBILITY_PRIVATE,n.visibility)
        assertEquals("View summary",n.actions.single().title)
        val i=Shadows.shadowOf(n.contentIntent).savedIntent
        assertEquals(SummaryRequest(SummaryKind.DAY,now.toLocalDate()),SummaryNav.fromIntent(i))
    }
    @Test fun permissionDeniedDoesNotConsumeTheDeliveryKey()=runBlocking {
        Shadows.shadowOf(ctx).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(0,notifier.check(now));assertFalse(ctx.getSharedPreferences("summary_delivery",0).contains("DAY"))
        Shadows.shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(1,notifier.check(now))
    }
    @Test fun blockedChannelDoesNotConsumeTheDeliveryKey()=runBlocking {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(SummaryNotifier.CHANNEL,"Summaries",NotificationManager.IMPORTANCE_NONE))
        assertEquals(0,notifier.check(now));assertFalse(ctx.getSharedPreferences("summary_delivery",0).contains("DAY"))
    }
    @Test fun bothOptInsRemainIndependent()=runBlocking {
        settings.update { it.copy(summaryWeekly=true) }
        assertEquals(2,notifier.check(now));assertEquals(0,notifier.check(now))
    }
    @Test fun malformedAndFutureDeepLinksAreIgnored() {
        assertNull(SummaryNav.fromIntent(Intent().putExtra(SummaryNav.KIND,"WEEK").putExtra(SummaryNav.DATE,"bad")))
        assertNull(SummaryNav.fromIntent(Intent().putExtra(SummaryNav.KIND,"DAY").putExtra(SummaryNav.DATE,"2199-01-01")))
        assertNull(SummaryNav.fromIntent(Intent().putExtra(SummaryNav.KIND,"OTHER").putExtra(SummaryNav.DATE,"2026-11-08")))
    }
    @Test fun summaryPreferencesRoundTripAndInvalidClockValuesAreRejected()=runBlocking {
        settings.update { it.copy(summaryDaily=true,summaryDailyMinute=5,summaryWeekly=true,summaryWeeklyMinute=1439) }
        val json=settings.backupExport()
        settings.update { it.copy(summaryDaily=false,summaryWeekly=false) }
        settings.backupRestore(json,false)
        assertTrue(settings.current().summaryDaily);assertTrue(settings.current().summaryWeekly);assertEquals(1439,settings.current().summaryWeeklyMinute)
        json.getJSONObject("summary_daily_time").put("value",1440)
        assertTrue(runCatching { settings.validateBackup(json) }.isFailure)
    }
}
