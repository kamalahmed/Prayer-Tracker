package com.kamalahmed.prayerapp.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.model.TimeFormat
import com.kamalahmed.prayerapp.data.settings.AppSettings
import com.kamalahmed.prayerapp.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import com.kamalahmed.prayerapp.core.safety.CrashLog
import javax.inject.Inject

data class PlayingInfo(val prayer: Prayer?, val prayerTime: Long, val preview: Boolean)

/**
 * Foreground service that plays the full adhan / chosen sound (not limited to a notification
 * channel clip), with max duration, optional loop, stream + volume choice, Stop & Snooze actions
 * and an optional full-screen alarm activity.
 */
@AndroidEntryPoint
class AdhanService : Service() {

    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var scheduler: AlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + com.kamalahmed.prayerapp.core.safety.safeHandler(TAG))
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var current: PlayingInfo? = null
    private var vibrator: Vibrator? = null
    private var settings: AppSettings? = null
    private var triedFallback = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopAll(postDua = false); return START_NOT_STICKY }
            ACTION_SNOOZE -> { snooze(); return START_NOT_STICKY }
        }
        val prayer = intent?.getStringExtra(EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } }
        val time = intent?.getLongExtra(EXTRA_TIME, System.currentTimeMillis()) ?: System.currentTimeMillis()
        val preview = intent?.action == ACTION_PREVIEW
        triedFallback = false
        val info = PlayingInfo(prayer, time, preview)
        // Must call startForeground promptly.
        try {
            Channels.create(this)
            startInForeground(buildNotification(info, null))
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException / missing FGS permission: give up cleanly.
            CrashLog.nonFatal(TAG, "startForeground failed", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                val s = settingsRepo.current()
                settings = s
                startForeground(info, s)
                play(info, s)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashLog.nonFatal(TAG, "Could not start playback", e)
                stopAll(postDua = false)
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NotificationIds.ADHAN_PLAYING, n, type)
    }

    private fun startForeground(info: PlayingInfo, s: AppSettings) {
        stopPlayer()
        current = info
        _playing.value = info
        startInForeground(buildNotification(info, s))
    }

    private fun buildNotification(info: PlayingInfo, s: AppSettings?): Notification {
        val zone = s?.let { runCatching { ZoneId.of(it.timeZoneId) }.getOrNull() } ?: ZoneId.systemDefault()
        val timeText = TimeFormat.time(Instant.ofEpochMilli(info.prayerTime).atZone(zone), s?.use24h ?: false)
        val title = when {
            info.preview -> "Sound preview"
            info.prayer != null -> "${info.prayer.displayName} · $timeText"
            else -> "Adhan"
        }
        val b = NotificationCompat.Builder(this, Channels.ADHAN)
            .setSmallIcon(com.kamalahmed.prayerapp.R.drawable.ic_stat_prayer)
            .setContentTitle(title)
            .setContentText(if (info.preview) "Playing selected sound" else "It's time for prayer")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(openAppIntent(this))
            .setDeleteIntent(serviceIntent(ACTION_STOP, 2))
            .addAction(0, "Stop", serviceIntent(ACTION_STOP, 2))
        if (!info.preview && info.prayer != null) {
            b.addAction(0, "Snooze ${s?.snoozeMinutes ?: 5} min", serviceIntent(ACTION_SNOOZE, 3))
            if (s?.fullScreenAlarm == true && canUseFullScreen(this)) {
                val fs = PendingIntent.getActivity(
                    this, 4, AlarmActivity.intent(this, info.prayer, info.prayerTime),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                b.setFullScreenIntent(fs, true)
            }
        }
        return b.build()
    }

    private fun serviceIntent(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, AdhanService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun play(info: PlayingInfo, s: AppSettings, uriOverride: Uri? = null) {
        val uri = uriOverride ?: Sounds.resolve(this, s, info.prayer)
        val usage = if (s.useAlarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val attrs = AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val mp = MediaPlayer()
        player = mp
        try {
            requestFocus(attrs)
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(this, uri)
            mp.isLooping = s.loopSound
            val v = (s.volumePercent.coerceIn(0, 100) / 100f)
            mp.setVolume(v, v)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { if (!s.loopSound) stopAll(postDua = !info.preview && s.adhanDua) }
            mp.setOnErrorListener { _, what, extra ->
                CrashLog.nonFatal(TAG, "Playback error $what/$extra (${uri.scheme})")
                fallbackOrStop(info, s); true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            CrashLog.nonFatal(TAG, "Cannot play sound (${uri.scheme})", e)
            fallbackOrStop(info, s)
            return
        }
        if (s.vibrate && !info.preview) vibrate()
        val maxMs = (if (info.preview) minOf(s.maxPlaySeconds, 30) else s.maxPlaySeconds).coerceAtLeast(5) * 1000L
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopAll(postDua = !info.preview && s.adhanDua) }, maxMs)
    }

    private fun fallbackOrStop(info: PlayingInfo, s: AppSettings) {
        stopPlayer()
        if (!triedFallback) {
            triedFallback = true
            play(info, s, Sounds.fallback(this))
        } else stopAll(postDua = false)
    }

    private fun requestFocus(attrs: AudioAttributes) {
        val am = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { change ->
                    // Stop when a phone call or another alarm takes focus permanently.
                    if (change == AudioManager.AUDIOFOCUS_LOSS) stopAll(postDua = false)
                }.build()
            focusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        vibrator = v
        runCatching { v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400, 600), -1)) }
    }

    private fun snooze() {
        val info = current
        val minutes = settings?.snoozeMinutes ?: 5
        if (info?.prayer != null) scheduler.scheduleSnooze(info.prayer, info.prayerTime, minutes)
        stopAll(postDua = false)
    }

    private fun stopPlayer() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        runCatching { vibrator?.cancel() }
        focusRequest?.let { f -> runCatching { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(f) } }
        focusRequest = null
    }

    private fun stopAll(postDua: Boolean) {
        stopPlayer()
        if (postDua) {
            notifySafely(
                NotificationIds.DUA,
                baseNotification(this, Channels.SILENT, "Dua after the adhan", DUA_TEXT)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(DUA_TEXT_FULL))
                    .setTimeoutAfter(30 * 60_000L),
            )
        }
        _playing.value = null
        current = null
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayer()
        _playing.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdhanService"
        const val ACTION_PLAY = "com.kamalahmed.prayerapp.PLAY"
        const val ACTION_PREVIEW = "com.kamalahmed.prayerapp.PREVIEW"
        const val ACTION_STOP = "com.kamalahmed.prayerapp.STOP"
        const val ACTION_SNOOZE = "com.kamalahmed.prayerapp.SNOOZE"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_TIME = "time"

        const val DUA_TEXT = "Allahumma Rabba hadhihi-d-da'wati-t-tammah…"
        const val DUA_TEXT_FULL =
            "Allahumma Rabba hadhihi-d-da'wati-t-tammah, wa-s-salati-l-qa'imah, ati Muhammadan al-wasilata wa-l-fadilah, " +
                "wa-b'athhu maqaman mahmudan alladhi wa'adtah.\n\nO Allah, Lord of this perfect call and established prayer, " +
                "grant Muhammad the intercession and favour, and raise him to the praised station You promised him."

        private val _playing = MutableStateFlow<PlayingInfo?>(null)
        val playing: StateFlow<PlayingInfo?> = _playing

        fun playIntent(ctx: Context, prayer: Prayer, time: Long) = Intent(ctx, AdhanService::class.java)
            .setAction(ACTION_PLAY).putExtra(EXTRA_PRAYER, prayer.name).putExtra(EXTRA_TIME, time)

        fun previewIntent(ctx: Context) = Intent(ctx, AdhanService::class.java).setAction(ACTION_PREVIEW)

        fun stopIntent(ctx: Context) = Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP)

        fun snoozeIntent(ctx: Context) = Intent(ctx, AdhanService::class.java).setAction(ACTION_SNOOZE)

        fun canUseFullScreen(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
}
