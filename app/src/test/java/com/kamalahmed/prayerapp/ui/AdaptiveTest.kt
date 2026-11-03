package com.kamalahmed.prayerapp.ui

import com.kamalahmed.prayerapp.ui.adaptive.AdaptiveMath
import com.kamalahmed.prayerapp.ui.adaptive.AdaptiveSpec
import com.kamalahmed.prayerapp.ui.adaptive.HeightClass
import com.kamalahmed.prayerapp.ui.adaptive.WidthClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {
    private val phone = AdaptiveSpec(411f, 914f)
    private val phoneLand = AdaptiveSpec(914f, 411f)
    private val foldOuter = AdaptiveSpec(390f, 860f)
    private val foldInner = AdaptiveSpec(724f, 870f)
    private val foldInnerLand = AdaptiveSpec(870f, 724f)
    private val tablet = AdaptiveSpec(1280f, 800f)

    @Test fun phonePortraitIsSinglePaneWithBottomBar() {
        assertEquals(WidthClass.COMPACT, phone.widthClass)
        assertFalse(phone.useRail); assertFalse(phone.twoPane); assertFalse(phone.listDetail)
        assertEquals(1, phone.dashboardColumns)
        assertFalse(foldOuter.useRail); assertFalse(foldOuter.twoPane)
    }

    @Test fun phoneLandscapeUsesRailAndTwoPanes() {
        assertEquals(HeightClass.COMPACT, phoneLand.heightClass)
        assertTrue(phoneLand.useRail); assertTrue(phoneLand.twoPane)
        assertEquals(834f, phoneLand.contentWidthDp, 0.01f)
    }

    @Test fun foldInnerBothOrientations() {
        for (s in listOf(foldInner, foldInnerLand)) {
            assertTrue(s.useRail); assertTrue(s.twoPane)
            assertEquals(2, s.dashboardColumns)
        }
        assertEquals(WidthClass.MEDIUM, foldInner.widthClass)
        assertEquals(WidthClass.EXPANDED, foldInnerLand.widthClass)
        assertFalse(foldInner.listDetail) // 644dp content: single settings pane
        assertTrue(foldInnerLand.listDetail)
    }

    @Test fun tabletIsExpandedThreeColumns() {
        assertEquals(WidthClass.EXPANDED, tablet.widthClass)
        assertTrue(tablet.listDetail); assertEquals(3, tablet.dashboardColumns)
    }

    @Test fun dialNeverExceedsMinDimension() {
        val sizes = listOf(300f to 900f, 900f to 300f, 400f to 400f, 700f to 650f, 1200f to 1200f, 100f to 80f)
        for ((w, h) in sizes) {
            val d = AdaptiveMath.dialDiameter(w, h)
            assertTrue("$w x $h -> $d", d <= minOf(w, h) + 0.01f)
            assertTrue(d <= 560f)
        }
        assertEquals(300f, AdaptiveMath.dialDiameter(300f, 900f), 0.01f)
        assertEquals(560f, AdaptiveMath.dialDiameter(1200f, 1200f), 0.01f)
    }

    @Test fun dialHonoursReservedHeight() {
        assertEquals(250f, AdaptiveMath.dialDiameter(400f, 400f, reservedH = 150f), 0.01f)
        // min size never overflows the box
        assertTrue(AdaptiveMath.dialDiameter(400f, 130f, reservedH = 100f) <= 130f)
    }
}
