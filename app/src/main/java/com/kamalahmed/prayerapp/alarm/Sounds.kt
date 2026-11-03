package com.kamalahmed.prayerapp.alarm

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kamalahmed.prayerapp.R
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.SoundSource
import com.kamalahmed.prayerapp.data.settings.AppSettings
import java.io.File

data class BundledSound(val id: String, val label: String, val resId: Int)

object Sounds {
    /** Bundled recordings are CC0 (Wikimedia Commons), see README for attribution. */
    val bundled = listOf(
        BundledSound("adhan_full", "Adhan (full, ~2.5 min)", R.raw.adhan_full),
        BundledSound("adhan_short", "Adhan (short, ~40 s)", R.raw.adhan_short),
        BundledSound("chime", "Gentle chime", R.raw.chime),
    )

    fun bundledUri(ctx: Context, id: String): Uri {
        val res = (bundled.firstOrNull { it.id == id } ?: bundled.first()).resId
        return Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(ctx.packageName).appendPath(res.toString()).build()
    }

    /** Resolve the sound to play for [prayer] from settings. */
    fun resolve(ctx: Context, s: AppSettings, prayer: Prayer?): Uri = when (s.soundSource) {
        SoundSource.BUNDLED -> bundledUri(ctx, if (prayer == Prayer.FAJR) s.fajrBundledSound else s.bundledSound)
        SoundSource.RINGTONE -> s.soundUri?.let(Uri::parse) ?: bundledUri(ctx, s.bundledSound)
        SoundSource.CUSTOM_FILE -> s.soundUri?.let { path ->
            val f = File(path)
            if (f.exists()) Uri.fromFile(f) else null
        } ?: bundledUri(ctx, s.bundledSound)
    }

    fun fallback(ctx: Context): Uri = bundledUri(ctx, "adhan_full")

    /**
     * Copy a user-picked audio document into app storage so playback never depends on
     * a revocable URI grant. Returns (absolutePath, displayName).
     */
    fun importCustom(ctx: Context, uri: Uri): Pair<String, String>? = runCatching {
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Custom sound"
        val dir = File(ctx.filesDir, "sounds").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        // The display name comes from another app: keep only a short alphanumeric extension (no path tricks).
        val ext = name.substringAfterLast('.', "audio").filter { it.isLetterOrDigit() }.take(5).ifEmpty { "audio" }
        val out = File(dir, "custom.$ext")
        (ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Cannot open $uri")).use { input -> out.outputStream().use { input.copyTo(it) } }
        out.absolutePath to name
    }.onFailure { com.kamalahmed.prayerapp.core.safety.CrashLog.nonFatal("Sounds", "Custom sound import failed", it) }.getOrNull()
}
