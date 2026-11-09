package com.kamalahmed.prayerapp.qada

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.kamalahmed.prayerapp.core.safety.guard

/**
 * Tap haptics for Qada logging. Shares the app's "Tap haptics" preference with the Tasbih counter
 * (SharedPreferences "tasbih"/"haptics", default on, included in backups) so one switch controls both.
 */
object QadaFeedback {
    private const val PREFS = "tasbih"
    private const val KEY = "haptics"

    fun enabled(ctx: Context): Boolean = guard("QadaFeedback", "read haptics") { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true) } ?: true
    fun setEnabled(ctx: Context, on: Boolean) { guard("QadaFeedback", "save haptics") { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply() } }

    /** [strong] = a milestone (daily goal reached); otherwise a short click. Undo uses a lighter tick. */
    fun play(ctx: Context, kind: Kind = Kind.TAP) {
        guard("QadaFeedback", "haptics") {
            if (!enabled(ctx)) return@guard
            val vibrator = ctx.getSystemService(Vibrator::class.java) ?: return@guard
            if (!vibrator.hasVibrator()) return@guard
            val effect = when (kind) {
                Kind.TAP -> if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK) else VibrationEffect.createOneShot(18, 120)
                Kind.UNDO -> if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) else VibrationEffect.createOneShot(8, 60)
                Kind.GOAL -> VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 90), -1)
            }
            vibrator.vibrate(effect)
        }
    }

    enum class Kind { TAP, UNDO, GOAL }
}
