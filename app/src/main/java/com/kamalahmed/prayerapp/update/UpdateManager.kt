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
    data class Error(val message: String, val release: ReleaseInfo? = null) : UpdateState
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var downloadJob: Job? = null
    /** Seam for tests (offline, malformed JSON); production fetches from the GitHub API. */
    internal var releasesSource: () -> String = { fetchReleases() }

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val currentCode: Int get() = BuildConfig.VERSION_CODE

    init {
        // Restore a known-available update (from the last check) so banners show without a network call.
        prefs.cachedReleasesJson()?.let { json ->
            runCatching { ReleaseParser.pickUpdate(ReleaseParser.parseList(json), prefs.includeBeta.value, currentVersion, currentCode) }
                .getOrNull()?.let { _state.value = UpdateState.Available(it) }
        }
    }

    /** Result of a check, used by both UI and the background worker. Never throws. */
    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Installing) return@withContext busy
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
        _state.value = result
        result
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
        fileFor(rel).takeIf { it.exists() && it.length() == rel.apk!!.size && prefs.verifiedApk == it.name }

    fun startDownload(rel: ReleaseInfo) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            try {
                val file = download(rel)
                _state.value = UpdateState.Downloaded(rel, file)
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(rel)
            } catch (e: UnknownHostException) {
                _state.value = UpdateState.Error("Download paused: you're offline. Tap Download to resume.", rel)
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Download failed", rel)
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    /** Cancels and removes the partial file so the next download starts fresh. */
    fun discardDownload(rel: ReleaseInfo) {
        downloadJob?.cancel()
        File(updatesDir(), fileFor(rel).name + ".part").delete()
        fileFor(rel).delete()
        _state.value = UpdateState.Available(rel)
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

    fun canInstall(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(rel: ReleaseInfo, file: File) {
        scope.launch {
            try {
                _state.value = UpdateState.Installing(rel)
                // Re-check right before installing (the file may have been downloaded by an older app version).
                UpdateSecurity.verifyDownloadedApk(ctx, file)
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
        val rel = (_state.value as? UpdateState.Installing)?.release
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

    fun isOnline(wifiOnly: Boolean): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !wifiOnly || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        private const val TAG = "UpdateManager"
        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
