package com.kamalahmed.prayerapp.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

/**
 * Small bitmaps for things RemoteViews cannot draw: rounded/gradient backgrounds on any API level and
 * the progress ring. Rendered at ≤1.5x density to keep RemoteViews payloads small.
 */
object WidgetBitmaps {
    private const val MAX_PX = 720

    private fun scale(density: Float) = min(density, 1.5f)

    fun background(widthDp: Float, heightDp: Float, density: Float, pal: WidgetPalette, cornerDp: Int, gradient: Boolean): Bitmap {
        val s = scale(density)
        val w = (widthDp.coerceAtLeast(40f) * s).toInt().coerceAtMost(MAX_PX)
        val h = (heightDp.coerceAtLeast(40f) * s).toInt().coerceAtMost(MAX_PX)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (gradient) paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), pal.bg, pal.bgEnd, Shader.TileMode.CLAMP)
        else paint.color = pal.bg
        val r = cornerDp * s * (w / (widthDp.coerceAtLeast(40f) * s))
        Canvas(bmp).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)
        return bmp
    }

    fun ring(sizeDp: Float, density: Float, progress: Float, track: Int, color: Int, strokeDp: Float = 7f): Bitmap {
        val s = scale(density)
        val px = (sizeDp * s).toInt().coerceIn(16, MAX_PX)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = strokeDp * s
        val rect = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        p.color = track
        c.drawArc(rect, 0f, 360f, false, p)
        p.color = color
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0.5f) c.drawArc(rect, -90f, sweep, false, p)
        return bmp
    }
}
