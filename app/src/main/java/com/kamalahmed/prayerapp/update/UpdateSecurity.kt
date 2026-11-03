package com.kamalahmed.prayerapp.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

/** Pure checks applied before an update is downloaded or handed to PackageInstaller. */
object UpdateSecurity {
    /** Release metadata and assets must come from GitHub over HTTPS. */
    private val TRUSTED_HOSTS = setOf("github.com", "api.github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
    const val MAX_APK_BYTES = 100L * 1024 * 1024

    fun isTrustedUrl(url: String?): Boolean {
        val u = runCatching { URI(url ?: return false) }.getOrNull() ?: return false
        return u.scheme.equals("https", ignoreCase = true) && u.host?.lowercase() in TRUSTED_HOSTS && u.userInfo == null
    }

    /** A checksum is mandatory: no hash, no install. */
    fun requireSha(expected: String?): String {
        val h = expected?.trim()?.lowercase()
        if (h == null || !Regex("[0-9a-f]{64}").matches(h)) throw IOException("This release has no valid SHA-256 checksum, so it can't be verified. Not installing.")
        return h
    }

    /**
     * Returns null when the downloaded APK is acceptable, otherwise a user-facing reason: it must be this app,
     * strictly newer (no downgrade or reinstall) and signed with exactly the installed signing certificate(s).
     */
    fun checkApk(
        archivePackage: String?, archiveCode: Long, archiveCerts: Set<String>,
        expectedPackage: String, installedCode: Long, installedCerts: Set<String>,
    ): String? = when {
        archivePackage == null -> "The downloaded file is not a valid APK."
        archivePackage != expectedPackage -> "The downloaded APK is a different app ($archivePackage). Not installing."
        archiveCode <= installedCode -> "The downloaded APK is not newer than the installed version (versionCode $archiveCode ≤ $installedCode)."
        archiveCerts.isEmpty() || installedCerts.isEmpty() -> "Couldn't read the APK signature. Not installing."
        archiveCerts != installedCerts -> "The update is signed with a different key. Not installing."
        else -> null
    }

    fun certDigests(info: PackageInfo?): Set<String> {
        info ?: return emptySet()
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory?.takeLast(1)?.toTypedArray()
        } else @Suppress("DEPRECATION") info.signatures
        return sigs.orEmpty().filterNotNull().map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    fun verifyDownloadedApk(ctx: Context, file: File) {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        val archiveCode = archive?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() } ?: -1
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        checkApk(archive?.packageName, archiveCode, certDigests(archive), ctx.packageName, installedCode, certDigests(installed))
            ?.let { throw SecurityException(it) }
    }
}
