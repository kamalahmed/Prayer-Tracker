package com.kamalahmed.prayerapp.nafl

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.kamalahmed.prayerapp.core.safety.guard

object CounterFeedback {
    fun play(ctx: Context,lap: Boolean) {
        guard("CounterFeedback","haptics") {
            if(!ctx.getSharedPreferences("tasbih",Context.MODE_PRIVATE).getBoolean("haptics",true)) return@guard
            val vibrator=ctx.getSystemService(Vibrator::class.java) ?: return@guard
            if(!vibrator.hasVibrator()) return@guard
            vibrator.vibrate(if(lap) VibrationEffect.createWaveform(longArrayOf(0,50,45,110),-1) else VibrationEffect.createOneShot(12,60))
        }
    }
}
