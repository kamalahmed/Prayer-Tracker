package com.kamalahmed.prayerapp.summary

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.*
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kamalahmed.prayerapp.MainActivity
import com.kamalahmed.prayerapp.R
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.safety.*
import com.kamalahmed.prayerapp.data.prayer.PrayerRepository
import com.kamalahmed.prayerapp.data.settings.*
import com.kamalahmed.prayerapp.tracker.*
import dagger.assisted.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.concurrent.TimeUnit
import javax.inject.*

object SummaryNav {
    const val KIND="worship_summary_kind";const val DATE="worship_summary_date"
    val openRequested=MutableStateFlow<SummaryRequest?>(null)
    fun fromIntent(intent: Intent?): SummaryRequest? { return try {
        val kind=intent?.getStringExtra(KIND)?.let { k -> SummaryKind.entries.firstOrNull { it.name==k } } ?: return null
        val date=LocalDate.parse(intent.getStringExtra(DATE))
        if(date.year !in 1900..2200 || date>LocalDate.now()) null else SummaryRequest(kind,date)
    } catch(_: Exception) { null } }
}

@Singleton
class SummaryNotifier @Inject constructor(@ApplicationContext private val ctx: Context,private val source: SummarySource,private val settings: SettingsRepository,private val prayers: PrayerRepository) {
    private val mutex=Mutex()
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun check(now: ZonedDateTime=ZonedDateTime.now()): Int=mutex.withLock {
        val s=settings.current()
        if(!s.summaryDaily && !s.summaryWeekly) return@withLock 0
        createChannel(ctx)
        val nm=NotificationManagerCompat.from(ctx)
        if(!nm.areNotificationsEnabled() || Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(ctx,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return@withLock 0
        if(ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE) return@withLock 0
        val prefs=ctx.getSharedPreferences("summary_delivery",Context.MODE_PRIVATE)
        val delivered=SummaryKind.entries.mapNotNull { kind -> prefs.getString(kind.name,null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { kind to it } }.toMap()
        val due=SummaryTiming.due(now,s,delivered)
        if(due.isEmpty()) return@withLock 0
        val history=source.read();val schedule=prayers.currentSchedule();val today=now.toLocalDate();val day=schedule.day(today)
        val passed=Prayer.obligatory.filter { day[it].toInstant()<=now.toInstant() }
        var sent=0
        due.forEach { request ->
            val end=if(request.kind==SummaryKind.DAY) request.anchor else request.anchor.plusDays(6)
            val r=history.report(request.anchor,end,today,passed)
            val title=(if(request.kind==SummaryKind.DAY) "Your day" else "Your week")+if(end>=today) " · so far" else ""
            val body="${r.fard.prayed} fard · ${r.sunnah} Sunnah rakaat · ${r.otherPrayers} other nafl · ${r.otherWorship} other worship · ${r.dhikr} dhikr · ${r.quranPages} Quran pages (${r.quranDays} days) · ${r.qada} Qada recovered"
            val intent=Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(SummaryNav.KIND,request.kind.name).putExtra(SummaryNav.DATE,request.anchor.toString())
            val pending=PendingIntent.getActivity(ctx,NOTIFICATION_BASE+request.kind.ordinal,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification=NotificationCompat.Builder(ctx,CHANNEL).setSmallIcon(R.drawable.ic_stat_prayer).setContentTitle(title).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pending).addAction(0,"View summary",pending)
                .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            // Do not consume a key when permission/channel/delivery fails. Stable IDs make a rare retry non-spammy.
            nm.notify(NOTIFICATION_BASE+request.kind.ordinal,notification)
            check(prefs.edit().putString(request.kind.name,request.anchor.toString()).commit()) { "Could not record summary delivery" }
            sent++
        }
        sent
    }
    companion object {
        const val CHANNEL="worship_summary_v1";const val NOTIFICATION_BASE=1800
        fun createChannel(ctx: Context) { ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Daily & weekly summaries",NotificationManager.IMPORTANCE_DEFAULT).apply { description="Optional private reflections on recorded worship";lockscreenVisibility=Notification.VISIBILITY_PRIVATE }) }
    }
}

@HiltWorker
class SummaryWorker @AssistedInject constructor(@Assisted context: Context,@Assisted params: WorkerParameters,private val notifier: SummaryNotifier): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result=try { notifier.check();Result.success() } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch(e: Exception) {
        CrashLog.nonFatal("SummaryWorker","Summary notification failed",e);if(runAttemptCount<3) Result.retry() else Result.failure()
    }
    companion object {
        const val WORK="worship-summaries"
        fun schedule(ctx: Context,s: AppSettings) {
            guard("Summaries","schedule") {
                val wm=WorkManager.getInstance(ctx)
                if(!s.summaryDaily && !s.summaryWeekly) { wm.cancelUniqueWork(WORK);wm.cancelUniqueWork("summary-resume-check");return@guard }
                wm.enqueueUniquePeriodicWork(WORK,ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<SummaryWorker>(15,TimeUnit.MINUTES).build())
            }
        }
        fun checkOnResume(ctx: Context) { guard("Summaries","resume") { WorkManager.getInstance(ctx).enqueueUniqueWork("summary-resume-check",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<SummaryWorker>().build()) } }
    }
}
