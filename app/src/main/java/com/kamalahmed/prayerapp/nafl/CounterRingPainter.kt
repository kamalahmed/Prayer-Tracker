package com.kamalahmed.prayerapp.nafl

import android.graphics.*
import kotlin.math.*

/** Shared by real Compose Canvas and Glance bitmaps. The centre and corners stay transparent. */
object CounterRingPainter {
    const val GREEN=0xFF5BC16B.toInt()
    const val GOLD=0xFFE6A23C.toInt()
    const val TEAL=0xFF2EC4B6.toInt()
    private fun color(lap: Int,accent: Int,gold: Int)=when((lap-1).coerceAtLeast(0)%3) { 0->accent;1->gold;else->TEAL }
    fun draw(canvas: Canvas,size: Float,progress: CounterProgress,track: Int,accent: Int=GREEN,gold: Int=GOLD) {
        if(!size.isFinite() || size<=0) return
        val stroke=size*0.085f;val inset=stroke*0.85f
        val rect=RectF(inset,inset,size-inset,size-inset)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=stroke;strokeCap=Paint.Cap.ROUND }
        p.color=track;canvas.drawOval(rect,p)
        if(!progress.hasTarget) return
        // Completed laps leave a full underlay; a new colour overlaps it, like an activity ring.
        if(progress.activeLap>1) { p.color=color(progress.activeLap-1,accent,gold);p.alpha=120;canvas.drawOval(rect,p);p.alpha=255 }
        if(progress.fraction<=0f) return
        p.color=color(progress.activeLap,accent,gold)
        canvas.drawArc(rect,-90f,360f*progress.fraction,false,p)
        // A bright rounded end cap makes the advancing edge visible on overlapping laps.
        val angle=Math.toRadians((360f*progress.fraction-90f).toDouble())
        val radius=rect.width()/2f
        val x=size/2f+cos(angle).toFloat()*radius;val y=size/2f+sin(angle).toFloat()*radius
        val cap=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;alpha=110 }
        canvas.drawCircle(x,y,stroke*0.22f,cap)
    }
    fun bitmap(sizeDp: Float,density: Float,progress: CounterProgress,track: Int,accent: Int=GREEN,gold: Int=GOLD): Bitmap {
        val safeDensity=if(density.isFinite()) density.coerceIn(0.5f,1.5f) else 1f
        val px=((if(sizeDp.isFinite()) sizeDp else 100f)*safeDensity).toInt().coerceIn(24,720)
        return Bitmap.createBitmap(px,px,Bitmap.Config.ARGB_8888).also { draw(Canvas(it),px.toFloat(),progress,track,accent,gold) }
    }
}
