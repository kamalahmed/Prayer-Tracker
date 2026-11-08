package com.kamalahmed.prayerapp.nafl

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Clock12 {
    fun minute(hour: Int, minute: Int, pm: Boolean): Int {
        require(hour in 1..12 && minute in 0..59)
        return (hour % 12 + if (pm) 12 else 0) * 60 + minute
    }
    fun label(minute: Int): String = LocalTime.ofSecondOfDay(minute.coerceIn(0, 1439) * 60L)
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
}
