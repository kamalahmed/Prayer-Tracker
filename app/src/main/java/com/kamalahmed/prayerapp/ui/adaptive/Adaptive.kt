package com.kamalahmed.prayerapp.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Material window size classes (breakpoints from the Material 3 adaptive guidance). */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }
enum class HeightClass { COMPACT, MEDIUM, EXPANDED }

/**
 * All layout decisions derived from the current window size in dp. Pure, so it is unit tested
 * (see AdaptiveTest) for the phone (1080x2400) and Galaxy Z Fold inner/outer displays in both orientations.
 * Recomputed on every size change (fold/unfold, rotation, split-screen, freeform resize); screen state lives
 * in ViewModels/rememberSaveable so it survives those changes.
 */
data class AdaptiveSpec(val widthDp: Float, val heightDp: Float) {
    val widthClass = when {
        widthDp < 600f -> WidthClass.COMPACT
        widthDp < 840f -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
    val heightClass = when {
        heightDp < 480f -> HeightClass.COMPACT
        heightDp < 900f -> HeightClass.MEDIUM
        else -> HeightClass.EXPANDED
    }

    /** Navigation rail on wide windows and on short (landscape phone) windows; bottom bar otherwise. */
    val useRail get() = widthClass != WidthClass.COMPACT || heightClass == HeightClass.COMPACT

    /** Content area once the navigation chrome is removed. */
    val contentWidthDp get() = if (useRail) widthDp - RAIL_WIDTH else widthDp
    val contentHeightDp get() = if (useRail) heightDp else heightDp - BOTTOM_BAR_HEIGHT

    /** Two panes side by side (Prayers, Times, Qibla). */
    val twoPane get() = contentWidthDp >= 600f || (contentWidthDp > contentHeightDp * 1.2f && contentWidthDp >= 480f)

    /** Settings as list + detail. */
    val listDetail get() = contentWidthDp >= 700f

    /** Columns for the Streaks dashboard. */
    val dashboardColumns get() = when {
        contentWidthDp >= 1100f -> 3
        contentWidthDp >= 600f -> 2
        else -> 1
    }

    companion object {
        const val RAIL_WIDTH = 80f
        const val BOTTOM_BAR_HEIGHT = 80f
        const val MAX_CONTENT_WIDTH = 840f
        const val SETTINGS_LIST_WIDTH = 360f
    }
}

object AdaptiveMath {
    /**
     * Diameter for a round dial (prayer ring, Qibla compass) inside a box of [availW] x [availH] dp,
     * leaving [reservedH] dp of the height for text above/below. Never exceeds the available min dimension,
     * so the circle is never cropped.
     */
    fun dialDiameter(availW: Float, availH: Float, reservedH: Float = 0f, maxDp: Float = 560f, minDp: Float = 120f): Float {
        val fit = min(availW, availH - reservedH)
        val hardMax = min(availW, availH).coerceAtLeast(0f)
        return fit.coerceAtMost(maxDp).coerceAtLeast(minDp).coerceAtMost(hardMax)
    }
}

val LocalAdaptive = staticCompositionLocalOf { AdaptiveSpec(400f, 800f) }

/** Centers content and caps its width on large screens. */
@Composable
fun MaxWidth(maxWidth: Float = AdaptiveSpec.MAX_CONTENT_WIDTH, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth.dp).fillMaxWidth()) { content() }
    }
}

/** BoxWithConstraints with the box size in dp as floats. */
@Composable
fun SizedBox(modifier: Modifier = Modifier, content: @Composable BoxWithConstraintsScope.(w: Float, h: Float) -> Unit) {
    BoxWithConstraints(modifier) { content(maxWidth.value, maxHeight.value) }
}
