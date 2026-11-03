package com.kamalahmed.prayerapp.hardening

import com.kamalahmed.prayerapp.core.model.HighLatitudeRule
import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.CalcParams
import com.kamalahmed.prayerapp.core.prayer.CalculationMethod
import com.kamalahmed.prayerapp.core.prayer.PrayerTimesCalculator
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Polar day / polar night used to throw "Cannot round NaN value" from the calculator. */
class PolarTest {
    private val places = listOf(69.65 to 18.96 /* Tromso */, 78.22 to 15.65 /* Longyearbyen */, -77.85 to 166.67 /* McMurdo */, 89.9 to 0.0)
    private val dates = listOf(LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21), LocalDate.of(2026, 3, 20))

    @Test fun everyMethodAndRuleProducesOrderedTimesAtPolarLatitudes() {
        for ((lat, lng) in places) for (d in dates) for (m in CalculationMethod.entries) for (r in HighLatitudeRule.entries) {
            val day = PrayerTimesCalculator.calculate(d, CalcParams(lat, lng, ZoneId.of("UTC"), m, highLatRule = r))
            val t = Prayer.entries.map { day[it] }
            for (i in 1 until t.size) assertTrue("$lat $d $m $r ${t}", !t[i].isBefore(t[i - 1]))
        }
    }
}
