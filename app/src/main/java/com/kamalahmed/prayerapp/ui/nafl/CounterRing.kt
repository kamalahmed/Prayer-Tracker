package com.kamalahmed.prayerapp.ui.nafl

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import com.kamalahmed.prayerapp.nafl.*

@Composable
fun CounterRing(count: Int,target: Int,modifier: Modifier=Modifier) {
    val track=MaterialTheme.colorScheme.onSurface.copy(alpha=0.12f).toArgb()
    Canvas(modifier) { drawIntoCanvas { CounterRingPainter.draw(it.nativeCanvas,minOf(size.width,size.height),CounterProgress.of(count,target),track) } }
}
