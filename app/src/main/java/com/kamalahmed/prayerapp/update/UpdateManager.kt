package com.kamalahmed.prayerapp.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.kamalahmed.prayerapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.kamalahmed.prayerapp.core.safety.guard
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val bytes: Long, val total: Long) : UpdateState {
        val fraction get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data class Downloaded(val release: ReleaseInfo, val file: File) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class Error(val message: String, val release: ReleaseInfo? = null, val retryable: Boolean = false) : UpdateState
}

/**
 * Checks GitHub Releases (unauthenticated, ETag-cached), downloads the APK resumably with size/SHA-256
 * verification, and installs it through the PackageInstaller session API.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    val prefs: UpdatePrefs,
) {
    private val scope = com.kamalahmed.prayerapp.core.safety.safeScope("UpdateManager", Dispatchers.IO)
    private val checks = Mutex()
    private val downloads = Mutex()
    val pendingConfirmation = MutableStateFlow<Intent?>(null)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    /** Seam for tests (offline, malformed JSON); production fetches from the GitHub API. */
    internal var releasesSource: () -> String = { fetchReleases() }

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val currentCode: Int get() = BuildConfig.VERSION_CODE

    init {
        guard(TAG,"restore update") {
            val accepted = prefs.acceptedRelease()
            val pending = accepted?.takeIf { it.apk != null && VersionCompare.isNewer(it.version,it.versionCode,currentVersion,currentCode) }
            if (accepted != null && pending == null) prefs.acceptedJson = null
            val rel = pending ?: prefs.cachedReleasesJson()?.let { json ->
                ReleaseParser.pickUpdate(ReleaseParser.parseList(json),prefs.includeBeta.value,currentVersion,currentCode)
            }
            if (rel != null) _state.value = downloadedFileFor(rel)?.let { UpdateState.Downloaded(rel,it) } ?: UpdateState.Available(rel)
        }
    }

    /** Result of a check, used by both UI and the background worker. Never throws. */
    suspend fun checkAutomatic(): UpdateState {
        prefs.clearExpiredDeferral()
        if (!prefs.autoCheck.value || !isOnline(prefs.wifiOnly.value)) return _state.value
        return check(force = false)
    }

    suspend fun check(force: Boolean = true): UpdateState = withContext(Dispatchers.IO) { checks.withLock {
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Installing || busy is UpdateState.Downloaded) {
            if (busy is UpdateState.Downloaded) maybeNotify(busy)
            return@withLock busy
        }
        if (!force && !UpdateThrottle.due(System.currentTimeMillis(),prefs.lastAttempt)) {
            maybeNotify(busy)
            return@withLock busy
        }
        prefs.lastAttempt = System.currentTimeMillis()
        _state.value = UpdateState.Checking
        val result = try {
            val json = releasesSource()
            val rel = ReleaseParser.pickUpdate(ReleaseParser.parseList(json), prefs.includeBeta.value, currentVersion, currentCode)
            prefs.lastCheck = System.currentTimeMillis()
            if (rel == null) UpdateState.UpToDate
            else downloadedFileFor(rel)?.let { UpdateState.Downloaded(rel, it) } ?: UpdateState.Available(rel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RateLimitException) {
            UpdateState.Error(e.message ?: "GitHub rate limit reached")
        } catch (e: UnknownHostException) {
            UpdateState.Error("You're offline. Check your connection and try again.")
        } catch (e: IOException) {
            UpdateState.Error("Couldn't reach GitHub (${e.message ?: e.javaClass.simpleName}).")
        } catch (e: Exception) {
            com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal(TAG, "Release info unreadable", e)
            UpdateState.Error("Couldn't read release info.")
        }
        // A transient offline/rate-limit error must not erase a previously discovered update.
        val visible = if (result is UpdateState.Error && busy is UpdateState.Available) busy else result
        _state.value = visible
        maybeNotify(visible)
        visible
    } }

    private fun maybeNotify(s: UpdateState) {
        val rel = (s as? UpdateState.Available)?.release ?: (s as? UpdateState.Downloaded)?.release ?: return
        val key = if (s is UpdateState.Downloaded) "ready:${rel.key}" else rel.key
        if (!prefs.isDeferred(rel.key) && prefs.notifiedTag != key && UpdateWorker.notify(ctx,rel,s is UpdateState.Downloaded)) prefs.notifiedTag = key
    }

    internal fun notifyDownloaded(s: UpdateState.Downloaded) { maybeNotify(s) }

    fun acceptUpdate(rel: ReleaseInfo) {
        guard(TAG,"accept update") {
            if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return@guard
            require(!rel.draft && rel.apk != null && VersionCompare.isNewer(rel.version,rel.versionCode,currentVersion,currentCode))
            prefs.acceptedJson = ReleaseParser.toJson(rel).toString()
            val ready = downloadedFileFor(rel)
            if (ready != null) _state.value = UpdateState.Downloaded(rel,ready)
            else {
                try {
                    _state.value = UpdateState.Downloading(rel,0,rel.apk.size)
                    UpdateDownloadWorker.enqueue(ctx)
                } catch (e: Exception) {
                    prefs.acceptedJson = null
                    _state.value = UpdateState.Error("Couldn't start background download. Tap Update to retry.",rel)
                    com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal(TAG,"enqueue download",e)
                }
            }
        }
    }

    fun acceptNotification(tag: String) = scope.launch {
        guard(TAG,"notification action") {
            val rel = prefs.acceptedRelease()?.takeIf { it.tag == tag }
                ?: prefs.cachedReleasesJson()?.let { ReleaseParser.parseList(it).firstOrNull { r -> r.tag == tag } }
            if (rel != null) acceptUpdate(rel)
        }
    }

    fun later(rel: ReleaseInfo) { guard(TAG,"later") { prefs.defer(rel.key); if (prefs.acceptedRelease()?.key == rel.key) postponeInstall() } }
    fun postponeInstall() { guard(TAG,"postpone install") { prefs.acceptedJson = null } }

    internal fun downloadFailed(message: String) {
        guard(TAG,"download failure") {
            val rel = prefs.acceptedRelease() ?: return@guard
            _state.value = UpdateState.Error(message,rel)
        }
    }

    internal suspend fun downloadAccepted(): UpdateState = downloads.withLock {
        val rel = prefs.acceptedRelease() ?: return@withLock _state.value
        try {
            _state.value = UpdateState.Downloading(rel,0,rel.apk?.size ?: 0)
            val file = download(rel)
            _state.value = UpdateState.Downloaded(rel,file)
        } catch (e: CancellationException) { _state.value = UpdateState.Available(rel); throw e }
        catch (e: Exception) {
            com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal(TAG,"download",e)
            val retry = e is UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.SocketException
            _state.value = UpdateState.Error(if (retry) "Download paused by the network; it will retry when connected." else e.message ?: "Download failed. Tap Update to retry.",rel,retry)
        }
        _state.value
    }


    private class RateLimitException(msg: String) : IOException(msg)

    private fun fetchReleases(): String {
        val conn = (URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=15").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PrayerApp/${BuildConfig.VERSION_NAME} (Android)")
            // Conditional request: a 304 does not count against the 60/hour unauthenticated limit.
            val cached = prefs.cachedReleasesJson()
            prefs.etag?.takeIf { cached != null }?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    prefs.saveReleases(body, conn.getHeaderField("ETag"))
                    return body
                }
                HttpURLConnection.HTTP_NOT_MODIFIED -> return prefs.cachedReleasesJson() ?: throw IOException("empty cache")
                403, 429 -> {
                    val remaining = conn.getHeaderField("x-ratelimit-remaining")
                    val reset = conn.getHeaderField("x-ratelimit-reset")?.toLongOrNull()
                    if (code == 429 || remaining == "0") {
                        val mins = reset?.let { ((it * 1000 - System.currentTimeMillis()) / 60_000).coerceAtLeast(1) }
                        throw RateLimitException("GitHub rate limit reached. Try again" + (mins?.let { " in about $it min." } ?: " later."))
                    }
                    throw IOException("HTTP $code")
                }
                404 -> throw IOException("no releases published yet")
                else -> throw IOException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    // ---------------- Download ----------------

    private fun updatesDir() = File(ctx.cacheDir, "updates").apply { mkdirs() }
    private fun fileFor(rel: ReleaseInfo) = File(updatesDir(), rel.apk!!.name.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    private fun downloadedFileFor(rel: ReleaseInfo): File? =
        fileFor(rel).takeIf { it.exists() && it.length() == rel.apk!!.size && prefs.verifiedApk == it.name && prefs.verifiedKey == rel.key && prefs.verifiedHash != null && (rel.notesSha256 == null || rel.notesSha256.equals(prefs.verifiedHash,true)) }

    fun startDownload(rel: ReleaseInfo) = acceptUpdate(rel)

    fun cancelDownload() {
        guard(TAG,"pause download") {
            val rel = prefs.acceptedRelease()
            postponeInstall()
            androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork(UpdateDownloadWorker.NAME)
            if (rel != null) _state.value = UpdateState.Available(rel)
        }
    }

    /** Cancels and removes the partial file so the next download starts fresh. */
    fun discardDownload(rel: ReleaseInfo) {
        cancelDownload()
        scope.launch {
            downloads.withLock {
                guard(TAG,"discard partial") {
                    File(updatesDir(),fileFor(rel).name+".part").delete()
                    fileFor(rel).delete()
                    _state.value = UpdateState.Available(rel)
                }
            }
        }
    }

    private suspend fun download(rel: ReleaseInfo): File {
        val asset = rel.apk!!
        val out = fileFor(rel)
        downloadedFileFor(rel)?.let { return it }
        // Clear stale APKs from older releases.
        updatesDir().listFiles()?.filter { !it.name.startsWith(out.name) }?.forEach { it.delete() }
        val part = File(updatesDir(), out.name + ".part")
        if (!UpdateSecurity.isTrustedUrl(asset.url)) throw IOException("Refusing to download from an untrusted address.")
        if (asset.size > UpdateSecurity.MAX_APK_BYTES) throw IOException("The update file is unexpectedly large. Not downloading.")
        val shaFromAsset = rel.sha256Asset?.takeIf { UpdateSecurity.isTrustedUrl(it.url) }?.let { a ->
            runCatching { fetchText(a.url) }
                .onFailure { com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal(TAG, "SHA-256 asset fetch failed, using release notes", it) }
                .getOrNull()?.let(ReleaseParser::parseShaFile)
        }
        val expectedSha = UpdateSecurity.requireSha(shaFromAsset ?: rel.notesSha256)
        if (part.exists() && asset.size > 0 && part.length() > asset.size) part.delete()

        var existing = part.length()
        val conn = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PrayerApp/${BuildConfig.VERSION_NAME} (Android)")
            setRequestProperty("Accept", "application/octet-stream")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            // After redirects (github.com -> objects.githubusercontent.com) the final address must still be trusted HTTPS.
            if (!UpdateSecurity.isTrustedUrl(conn.url.toString())) throw IOException("Download was redirected to an untrusted address.")
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> { existing = 0; false }
                416 -> { part.delete(); throw IOException("Download restarted, please try again") }
                else -> throw IOException("Download failed (HTTP $code)")
            }
            val total = asset.size.takeIf { it > 0 } ?: (existing + conn.contentLengthLong)
            var bytes = existing
            _state.value = UpdateState.Downloading(rel, bytes, total)
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { os ->
                    val buf = ByteArray(64 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        currentCoroutineContextEnsureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        bytes += n
                        if (bytes > UpdateSecurity.MAX_APK_BYTES) throw IOException("The update file is unexpectedly large. Download stopped.")
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 150) { lastEmit = now; _state.value = UpdateState.Downloading(rel, bytes, total) }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (asset.size > 0 && part.length() != asset.size) {
            part.delete()
            throw IOException("Size mismatch (got ${part.length()} of ${asset.size} bytes). Please retry.")
        }
        val actual = sha256(part)
        if (!actual.equals(expectedSha, true)) {
            part.delete()
            throw IOException("Checksum mismatch: the download is corrupt or was tampered with.")
        }
        try { UpdateSecurity.verifyDownloadedApk(ctx, part) } catch (e: Exception) {
            part.delete()
            throw IOException(e.message ?: "The downloaded APK failed verification.")
        }
        out.delete()
        if (!part.renameTo(out)) throw IOException("Couldn't save the update")
        prefs.verifiedApk = out.name
        prefs.verifiedHash = actual
        prefs.verifiedKey = rel.key
        return out
    }

    private suspend fun currentCoroutineContextEnsureActive() = kotlin.coroutines.coroutineContext.ensureActive()

    private fun fetchText(url: String): String {
        if (!UpdateSecurity.isTrustedUrl(url)) throw IOException("untrusted checksum URL")
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PrayerApp/${BuildConfig.VERSION_NAME} (Android)")
        }
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            if (!UpdateSecurity.isTrustedUrl(c.url.toString())) throw IOException("checksum redirected to an untrusted address")
            return c.inputStream.bufferedReader().use { it.readText().take(4096) }
        } finally { c.disconnect() }
    }

    // ---------------- Install ----------------

    fun onPendingConfirmation(intent: Intent) { pendingConfirmation.value = intent }
    fun consumeConfirmation() { pendingConfirmation.value = null }

    fun canInstall(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(rel: ReleaseInfo, file: File) {
        scope.launch {
            try {
                _state.value = UpdateState.Installing(rel)
                // Re-check right before installing (the file may have been downloaded by an older app version).
                try {
                    val expectedHash = UpdateSecurity.requireSha(prefs.verifiedHash)
                    if (prefs.verifiedKey != rel.key || !sha256(file).equals(expectedHash,true)) throw IOException("Checksum changed since download. Download the update again.")
                    UpdateSecurity.verifyDownloadedApk(ctx,file)
                } catch (e: Exception) {
                    // Do not leave a corrupt cached file in an endless Install -> failure loop.
                    prefs.verifiedApk = null; prefs.verifiedHash = null; prefs.verifiedKey = null
                    throw e
                }
                val installer = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(ctx.packageName)
                    setSize(file.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    file.inputStream().use { input ->
                        session.openWrite("base.apk", 0, file.length()).use { os ->
                            input.copyTo(os, 64 * 1024)
                            session.fsync(os)
                        }
                    }
                    val intent = Intent(ctx, InstallReceiver::class.java).setAction(InstallReceiver.ACTION)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Error("Install failed: ${e.message ?: e.javaClass.simpleName}", rel)
            }
        }
    }

    /** Called by [InstallReceiver] with the PackageInstaller status. */
    internal fun onInstallStatus(status: Int, message: String?) {
        val rel = (_state.value as? UpdateState.Installing)?.release ?: prefs.acceptedRelease()
        prefs.acceptedJson = null
        pendingConfirmation.value = null
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                rel?.let { r -> downloadedFileFor(r)?.let { UpdateState.Downloaded(r, it) } } ?: UpdateState.Error("Install cancelled", rel)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Error("Install blocked: this update is signed with a different key. Uninstall the current app (e.g. a debug build) and install from GitHub once.", rel)
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Error("Not enough storage to install the update.", rel)
            else -> UpdateState.Error("Install failed" + (message?.let { ": $it" } ?: ""), rel)
        }
    }

    fun isOnline(wifiOnly: Boolean): Boolean = guard(TAG,"connectivity") {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return@guard false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@guard false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@guard false
        !wifiOnly || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    } ?: false

    companion object {
        private const val TAG = "UpdateManager"
        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
