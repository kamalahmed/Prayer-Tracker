package com.kamalahmed.prayerapp.phase18

import com.kamalahmed.prayerapp.widget.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WidgetCoverageTest {
    @Test fun coverageSettingsRoundTripForEveryKind() {
        WidgetKind.entries.forEach { k ->
            val s = WidgetStyle.defaults(k).copy(contentScale = 1.25f, fillPanel = false, outerMargin = 9, tickTimes = TickTimes.ABOVE)
            assertEquals(s, WidgetStyle.decode(s.encode(), k))
        }
    }
    @Test fun oldInstallsFillTheCellWithoutLosingExistingAppearance() {
        val s = WidgetStyle.decode("v=2;opacity=65;padv=3;text=1.20", WidgetKind.TRACKER)
        assertTrue(s.fillPanel)
        assertEquals(0, s.outerMargin)
        assertEquals(1f, s.contentScale)
        assertEquals(65, s.opacity)
        assertEquals(3, s.padV)
        assertEquals(1.2f, s.textScale)
        assertEquals(TickTimes.OFF, s.tickTimes)
    }
    @Test fun unsafeDimensionsAreBoundedAndNonFiniteValuesAreRejected() {
        val s = WidgetStyle.decode("scale=NaN;text=Infinity;icon=NaN;box=-Infinity;margin=999", WidgetKind.TASBIH)
        assertEquals(1f, s.contentScale)
        assertEquals(1f, s.textScale)
        assertEquals(1f, s.iconScale)
        assertEquals(1f, s.boxScale)
        assertEquals(24, s.outerMargin)
    }
    @Test fun counterUsesWideLayoutInShortOrWideCells() {
        assertTrue(WidgetLayout.wideCounter(300f, 80f))
        assertTrue(WidgetLayout.wideCounter(360f, 160f))
        assertFalse(WidgetLayout.wideCounter(160f, 160f))
        assertFalse(WidgetLayout.wideCounter(220f, 260f))
    }
    @Test fun fitScaleRespondsToBothAxesAndKeepsAllPrayerCells() {
        val s = WidgetStyle.defaults(WidgetKind.ALL_WIDE)
        val small = WidgetLayout.fitScale(WidgetKind.ALL_WIDE, 250f, 80f, s)
        val big = WidgetLayout.fitScale(WidgetKind.ALL_WIDE, 600f, 180f, s)
        assertTrue(small < big)
        assertTrue(small.isFinite() && small > 0)
        assertEquals(5, WidgetLayout.cellCount(WidgetKind.ALL_WIDE, 5))
    }
    @Test fun counterProviderCanShrinkToOneRowAndHasItsOwnPreview() {
        val dir = listOf("src/main/res/xml", "app/src/main/res/xml").map(::File).first { it.exists() }
        val xml = File(dir, "widget_tasbih.xml").readText()
        assertTrue(xml.contains("horizontal|vertical"))
        assertTrue(xml.contains("widget_preview_tasbih"))
        assertTrue(xml.contains("previewLayout"))
        val minH = Regex("minResizeHeight=\"(\\d+)").find(xml)!!.groupValues[1].toInt()
        assertTrue(minH <= 60)
        assertTrue(xml.contains("targetCellHeight=\"2\""))
    }
}
