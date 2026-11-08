package com.kamalahmed.prayerapp.phase17

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kamalahmed.prayerapp.update.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34],application = android.app.Application::class)
class UpdateDeliveryTest {
    private fun context() = ApplicationProvider.getApplicationContext<Context>()
    private fun sample() = javaClass.classLoader!!.getResource("releases_sample.json")!!.readText()
    @Test fun autoPreferenceMigratesOnceThenRespectsSubsequentOptOut() {
        val ctx = context(); val sp = ctx.getSharedPreferences("updater",0)
        sp.edit().clear().putBoolean("auto_check",false).putString("notified_tag","old").commit()
        val first = UpdatePrefs(ctx)
        assertTrue(first.autoCheck.value); assertNull(first.notifiedTag)
        first.setAutoCheck(false)
        assertFalse(UpdatePrefs(ctx).autoCheck.value)
    }
    @Test fun concurrentResumeChecksAndFailedAttemptsAreThrottledButManualCheckBypasses() = runBlocking {
        val ctx = context(); ctx.getSharedPreferences("updater",0).edit().clear().commit()
        val prefs = UpdatePrefs(ctx); val manager = UpdateManager(ctx,prefs)
        var calls = 0
        manager.releasesSource = { calls++; throw java.io.IOException("offline") }
        coroutineScope { repeat(12) { launch { manager.check(force = false) } } }
        assertEquals(1,calls); assertTrue(prefs.lastAttempt > 0)
        manager.check(); assertEquals(2,calls)
        prefs.lastAttempt = System.currentTimeMillis() - UpdateThrottle.INTERVAL_MS
        manager.check(force = false); assertEquals(3,calls)
    }
    @Test fun blockedNotificationDoesNotConsumeTagAndCanBeRetriedAfterPermissionGrant() = runBlocking {
        val ctx = context(); ctx.getSharedPreferences("updater",0).edit().clear().commit()
        val prefs = UpdatePrefs(ctx); val manager = UpdateManager(ctx,prefs)
        Shadows.shadowOf(ctx as android.app.Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.releasesSource = { sample() }
        val release = (manager.check() as UpdateState.Available).release
        assertNull(prefs.notifiedTag)
        Shadows.shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.check(force = false) // throttled: retry notification without making another HTTP request
        assertEquals(release.key,prefs.notifiedTag)
        val notification = Shadows.shadowOf(ctx.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Update",notification.actions.single().title.toString())
        val action = Shadows.shadowOf(notification.actions.single().actionIntent).savedIntent
        assertEquals(release.tag,action.getStringExtra(com.kamalahmed.prayerapp.MainActivity.EXTRA_ACCEPT_UPDATE))
    }
    @Test fun transientCheckFailureKeepsKnownUpdateVisible() = runBlocking {
        val ctx = context(); ctx.getSharedPreferences("updater",0).edit().clear().commit()
        val manager = UpdateManager(ctx,UpdatePrefs(ctx)); manager.releasesSource = { sample() }
        val available = manager.check()
        manager.releasesSource = { throw java.io.IOException("offline") }
        assertEquals(available,manager.check())
    }
    @Test fun periodicWorkUsesConnectedFifteenMinutesWithoutInitialDelayOrBatteryGate() {
        val spec = UpdateWorker.request(false).workSpec
        assertEquals(900_000L,spec.intervalDuration); assertEquals(0L,spec.initialDelay)
        assertEquals(androidx.work.NetworkType.CONNECTED,spec.constraints.requiredNetworkType)
        assertFalse(spec.constraints.requiresBatteryNotLow())
        assertEquals(androidx.work.NetworkType.UNMETERED,UpdateWorker.request(true).workSpec.constraints.requiredNetworkType)
    }
    @Test fun blockedChannelDoesNotReportSuccessfulDelivery() {
        val ctx = context(); Shadows.shadowOf(ctx as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel(UpdateWorker.CHANNEL,"Updates",NotificationManager.IMPORTANCE_NONE))
        assertFalse(UpdateWorker.notify(ctx,ReleaseParser.parseList(sample()).first()))
    }
    @Test fun modifiedCachedApkIsRejectedBeforeAnyInstallerSessionStarts() = runBlocking {
        val ctx = context(); ctx.getSharedPreferences("updater",0).edit().clear().commit()
        val prefs = UpdatePrefs(ctx)
        val rel = ReleaseParser.parseList(sample()).first()
        val file = java.io.File(ctx.cacheDir,"tampered.apk").apply { writeText("tampered bytes") }
        prefs.verifiedHash = "a".repeat(64); prefs.verifiedKey = rel.key
        val manager = UpdateManager(ctx,prefs)
        manager.install(rel,file)
        val error = withTimeout(5000) { manager.state.first { it is UpdateState.Error } } as UpdateState.Error
        assertTrue(error.message.contains("Checksum changed"))
        assertNull(prefs.verifiedHash); assertNull(prefs.verifiedKey)
        assertTrue(ctx.packageManager.packageInstaller.mySessions.isEmpty())
    }
    @Test fun laterIsPerReleaseAndAcceptedHandoffIsDurable() {
        val ctx = context(); ctx.getSharedPreferences("updater",0).edit().clear().commit()
        val prefs = UpdatePrefs(ctx); val release = ReleaseParser.parseList(sample()).first()
        prefs.acceptedJson = ReleaseParser.toJson(release).toString()
        prefs.defer(release.key)
        val reopened = UpdatePrefs(ctx)
        assertEquals(release,reopened.acceptedRelease())
        assertTrue(reopened.isDeferred(release.key)); assertFalse(reopened.isDeferred("new-release:12"))
    }
    @Test fun acceptingAnAlreadyDownloadedVersionEmitsConsentEvenWhenUiStateDoesNotChange() {
        val ctx = context(); ctx.getSharedPreferences("updater",0).edit().clear().commit()
        val prefs = UpdatePrefs(ctx); val rel = ReleaseParser.parseList(sample()).first()
        assertNull(prefs.accepted.value)
        prefs.acceptedJson = ReleaseParser.toJson(rel).toString()
        assertNotNull(prefs.accepted.value)
        prefs.acceptedJson = null
        assertNull(prefs.accepted.value)
    }
    @Test fun acceptedReleaseRestoresWithoutNetworkCacheAndInstalledAcceptanceIsCleared() {
        val ctx = context(); ctx.getSharedPreferences("updater",0).edit().clear().commit()
        val prefs = UpdatePrefs(ctx); val rel = ReleaseParser.parseList(sample()).first()
        prefs.acceptedJson = ReleaseParser.toJson(rel).toString()
        assertTrue(UpdateManager(ctx,prefs).state.value is UpdateState.Available)
        val old = ReleaseParser.parseList("""[{"tag_name":"v0.1.0-beta.1","prerelease":true,"body":"versionCode: 2","assets":[{"name":"a.apk"}]}]""").single()
        prefs.acceptedJson = ReleaseParser.toJson(old).toString()
        UpdateManager(ctx,prefs)
        assertNull(prefs.acceptedJson)
    }
    @Test fun foregroundServiceFailureRemainsRetryableInUiInsteadOfStuckDownloading() {
        val ctx = context(); ctx.getSharedPreferences("updater",0).edit().clear().commit()
        val prefs = UpdatePrefs(ctx); val rel = ReleaseParser.parseList(sample()).first()
        prefs.acceptedJson = ReleaseParser.toJson(rel).toString()
        val manager = UpdateManager(ctx,prefs)
        manager.downloadFailed("Android couldn't start the download")
        val error = manager.state.value as UpdateState.Error
        assertEquals(rel,error.release); assertTrue(error.message.contains("couldn't start"))
    }
}
