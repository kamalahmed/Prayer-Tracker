package com.kamalahmed.prayerapp.core.safety

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.emitAll
import java.io.File
import java.time.Instant

/**
 * Local-only crash and error log. Nothing is sent over the network. Entries contain the app version, Android
 * version, device model, thread name and stack trace; no location, settings values or user content is added.
 */
object CrashLog {
    private const val TAG = "CrashLog"
    private const val DIR = "diagnostics"
    private const val LAST = "last_crash.txt"
    private const val ERRORS = "recent_errors.txt"
    private const val MAX_TRACE = 12_000
    private const val MAX_ERRORS_BYTES = 24_000

    @Volatile private var dir: File? = null
    @Volatile private var header: String = ""

    /** Installs the process-wide handler. The previous (system) handler still runs, so the process dies normally. */
    fun install(context: Context, versionName: String, versionCode: Int) {
        dir = File(context.filesDir, DIR)
        header = "App $versionName ($versionCode) · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { write(LAST, format("CRASH", thread.name, e), append = false) }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Records a caught, non-fatal error (also logged to logcat). Never throws. */
    fun nonFatal(tag: String, message: String, e: Throwable? = null) {
        Log.w(tag, message, e)
        runCatching {
            val f = dir?.let { File(it, ERRORS) } ?: return
            if (f.length() > MAX_ERRORS_BYTES) f.writeText(f.readText().takeLast(MAX_ERRORS_BYTES / 2))
            write(ERRORS, format("ERROR [$tag] $message", Thread.currentThread().name, e).take(2_000) + "\n", append = true)
        }
    }

    fun lastCrash(context: Context): String? = runCatching { File(File(context.filesDir, DIR), LAST).takeIf { it.isFile }?.readText() }.getOrNull()
    fun recentErrors(context: Context): String? = runCatching { File(File(context.filesDir, DIR), ERRORS).takeIf { it.isFile }?.readText() }.getOrNull()
    fun clear(context: Context) { runCatching { File(context.filesDir, DIR).deleteRecursively() } }

    /** Plain-text report for "Copy diagnostics". */
    fun diagnostics(context: Context): String = buildString {
        appendLine(header.ifEmpty { "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})" })
        appendLine("--- Last crash ---")
        appendLine(lastCrash(context) ?: "None recorded")
        appendLine("--- Recent handled errors ---")
        append(recentErrors(context)?.takeLast(6_000) ?: "None recorded")
    }

    internal fun format(kind: String, thread: String, e: Throwable?): String = buildString {
        appendLine("$kind at ${Instant.now()} on thread $thread")
        appendLine(header)
        if (e != null) append(Log.getStackTraceString(e).ifEmpty { e.stackTraceToString() }.take(MAX_TRACE))
    }

    private fun write(name: String, text: String, append: Boolean) {
        val d = dir ?: return
        d.mkdirs()
        val f = File(d, name)
        if (append) f.appendText(text) else f.writeText(text)
    }
}

/** Logs and records any uncaught coroutine exception instead of crashing the process. */
fun safeHandler(tag: String) = CoroutineExceptionHandler { _, e ->
    if (e !is CancellationException) {
        CrashLog.nonFatal(tag, "Uncaught coroutine error", e)
        AppErrors.report("Something went wrong (${e.javaClass.simpleName}). Details are in Settings > App > Diagnostics.")
    }
}

/** A supervisor scope whose failing children are logged, not fatal. */
fun safeScope(tag: String, dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default) =
    CoroutineScope(SupervisorJob() + dispatcher + safeHandler(tag))

/** goAsync() + coroutine with guaranteed finish() and no uncaught exceptions. */
fun BroadcastReceiver.launchAsync(tag: String, block: suspend () -> Unit) {
    val pending = goAsync()
    safeScope(tag).launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            CrashLog.nonFatal(tag, "Receiver work failed", e)
        } finally { runCatching { pending?.finish() } }
    }
}

/** runCatching that never swallows silently: failures are logged and recorded. */
inline fun <T> guard(tag: String, what: String, block: () -> T): T? = try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) {
    CrashLog.nonFatal(tag, what, e); null
}

/** App-wide, user-visible error channel: the main scaffold shows the latest message as a snackbar. */
object AppErrors {
    val latest = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    fun report(message: String) { latest.value = message }
}

/** Catches upstream failures of a UI flow: logs + records them, tells the user, and ends the flow quietly. */
fun <T> kotlinx.coroutines.flow.Flow<T>.reportErrors(tag: String, userMessage: String): kotlinx.coroutines.flow.Flow<T> =
    kotlinx.coroutines.flow.flow {
        try { emitAll(this@reportErrors) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            CrashLog.nonFatal(tag, userMessage, e)
            AppErrors.report(userMessage)
        }
    }
