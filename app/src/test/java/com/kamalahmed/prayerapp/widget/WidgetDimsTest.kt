package com.kamalahmed.prayerapp.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDimsTest {
    @Test fun alphaIsLinearAndReachesBothEnds() {
        assertEquals(0, WidgetPalette.alphaFor(0))
        assertEquals(255, WidgetPalette.alphaFor(100))
        assertEquals(128, WidgetPalette.alphaFor(50))
        assertEquals(64, WidgetPalette.alphaFor(25))
        assertEquals(0, WidgetPalette.alphaFor(-5))
        assertEquals(255, WidgetPalette.alphaFor(140))
        for (o in 0..99) assertTrue(WidgetPalette.alphaFor(o) <= WidgetPalette.alphaFor(o + 1))
    }

    @Test fun opacityZeroRemovesEveryBackgroundLayerForAllThemesAndKinds() {
        for (k in WidgetKind.entries) for (t in WidgetTheme.entries) {
            val st = WidgetStyle.defaults(k).copy(theme = t, opacity = 0)
            val p = WidgetPalette.of(st)
            assertEquals("$k/$t bg", 0, p.bg ushr 24)
            assertEquals("$k/$t bgEnd", 0, p.bgEnd ushr 24)
            assertEquals("$k/$t highlight", 0, p.highlight)
            assertEquals("$k/$t badge", 0, p.badge)
            assertFalse(WidgetDims.of(st).hasBackground)
        }
    }

    @Test fun opacityHundredIsFullyOpaque() {
        for (t in WidgetTheme.entries) {
            val p = WidgetPalette.of(WidgetStyle(theme = t, opacity = 100))
            assertEquals(255, p.bg ushr 24)
            assertEquals(255, p.bgEnd ushr 24)
        }
    }

    @Test fun paddingAndSpacingReachZeroOnAllKinds() {
        for (k in WidgetKind.entries) {
            val d = WidgetDims.of(WidgetStyle.defaults(k).copy(padV = 0, padH = 0, spacing = 0))
            assertEquals(WidgetDims(0, 0, 0, 0, 0, 0, d.hasBackground), d)
        }
    }

    @Test fun verticalAndHorizontalAreIndependentAndClamped() {
        val d = WidgetDims.of(WidgetStyle(padV = 3, padH = 20, spacing = 9))
        assertEquals(3, d.padTop); assertEquals(3, d.padBottom); assertEquals(20, d.padStart); assertEquals(20, d.padEnd)
        assertEquals(9, d.gap); assertEquals(4, d.cellPad)
        val c = WidgetDims.of(WidgetStyle(padV = -4, padH = 99, spacing = 50))
        assertEquals(0, c.padTop); assertEquals(32, c.padStart); assertEquals(24, c.gap)
    }

    @Test fun legacyV1PaddingDecodesIntoBothAxes() {
        val s = WidgetStyle.decode("v=1;padding=5;spacing=2;opacity=0", WidgetKind.ALL_WIDE)
        assertEquals(5, s.padV); assertEquals(5, s.padH); assertEquals(2, s.spacing); assertEquals(0, s.opacity)
        assertEquals(WidgetAlign.CENTER, s.align)
    }
}
