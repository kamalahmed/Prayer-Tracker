package com.kamalahmed.prayerapp.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetLayoutTest {
    private val st = WidgetStyle()

    @Test fun wideCompactOnlyDropsDetailNeverPrayers() {
        // 1-cell row on the Fold outer screen vs a 2-3 cell tall placement on the inner screen
        val sizes = listOf(250f to 48f, 250f to 70f, 420f to 110f, 600f to 160f, 900f to 300f, 320f to 90f)
        sizes.forEach { (w, h) ->
            assertEquals("cells at ${w}x$h", 5, WidgetLayout.cellCount(WidgetKind.ALL_WIDE, 5))
        }
        assertTrue(WidgetLayout.compact(WidgetKind.ALL_WIDE, 250f, 48f, st))
        assertFalse(WidgetLayout.compact(WidgetKind.ALL_WIDE, 420f, 110f, st))
    }

    @Test fun foldLikeSizeChangeKeepsSameCellCount() {
        // Same instance reported with portrait (minW x maxH) and landscape (maxW x minH) option sizes on fold/unfold
        val portrait = WidgetLayout.cellCount(WidgetKind.ALL_WIDE, 6)
        val landscape = WidgetLayout.cellCount(WidgetKind.ALL_WIDE, 6)
        assertEquals(portrait, landscape)
        listOf(WidgetKind.TRACKER, WidgetKind.NEXT_STREAK).forEach { assertEquals(5, WidgetLayout.cellCount(it, 6)) }
    }

    @Test fun alwaysFullOverridesCompact() {
        val full = st.copy(alwaysFull = true)
        listOf(WidgetKind.ALL_WIDE, WidgetKind.TRACKER, WidgetKind.NEXT_STREAK).forEach { k ->
            assertTrue(WidgetLayout.compact(k, 250f, 40f, st))
            assertFalse(WidgetLayout.compact(k, 250f, 40f, full))
        }
    }

    @Test fun unknownSizeUsesFullLayout() {
        assertFalse(WidgetLayout.compact(WidgetKind.ALL_WIDE, 0f, 0f, st))
    }

    @Test fun thresholds() {
        assertTrue(WidgetLayout.compact(WidgetKind.NEXT_STREAK, 300f, 129f, st))
        assertFalse(WidgetLayout.compact(WidgetKind.NEXT_STREAK, 300f, 130f, st))
        assertFalse(WidgetLayout.compact(WidgetKind.TRACKER, 300f, 100f, st))
        assertFalse(WidgetLayout.compact(WidgetKind.LARGE, 100f, 50f, st))
        assertFalse(WidgetLayout.compact(WidgetKind.NEXT_SMALL, 80f, 40f, st))
    }

    @Test fun alwaysFullPersistsPerInstance() {
        val s = WidgetStyle.decode(st.copy(alwaysFull = true).encode(), WidgetKind.ALL_WIDE)
        assertTrue(s.alwaysFull)
        assertFalse(WidgetStyle.decode(null, WidgetKind.ALL_WIDE).alwaysFull)
        // old (beta.2) strings without the key keep the default
        assertFalse(WidgetStyle.decode("v=2;theme=GLASS;padv=0", WidgetKind.ALL_WIDE).alwaysFull)
    }

    /** Regression guard for the beta.1/2 bug: R8 merged all widget classes into one. */
    @Test fun proguardKeepsWidgetClassesDistinct() {
        val f = listOf("proguard-rules.pro", "app/proguard-rules.pro").map(::File).first { it.exists() }
        assertTrue(f.readText().contains("-keep class com.kamalahmed.prayerapp.widget.** extends androidx.glance.appwidget.GlanceAppWidget"))
    }

    @Test fun declaredHeightsFitContent() {
        val dir = listOf("src/main/res/xml", "app/src/main/res/xml").map(::File).first { it.exists() }
        fun attr(file: String, a: String) = Regex("android:$a=\"(\\d+)").find(File(dir, file).readText())!!.groupValues[1].toInt()
        listOf("widget_wide.xml", "widget_tracker.xml", "widget_minimal.xml").forEach {
            assertEquals("$it targetCellHeight", 1, attr(it, "targetCellHeight"))
            assertTrue("$it minHeight", attr(it, "minHeight") <= 40)
        }
        File(dir.path).listFiles { f -> f.name.startsWith("widget_") }!!.forEach {
            assertTrue("${it.name} maxResizeWidth for fold inner screen", attr(it.name, "maxResizeWidth") >= 900)
        }
    }
}
