package com.kamalahmed.prayerapp.nafl

/** Session ownership is independent per item/day. Widget counts are never added to this ledger. */
class CounterSession {
    private val entries = mutableMapOf<Pair<String,String>,Int>()
    fun count(day: String, id: String): Int = entries[day to id] ?: 0
    fun record(day: String, id: String, actualDelta: Int) {
        entries[day to id] = (count(day,id).toLong() + actualDelta).coerceIn(0,1_000_000).toInt()
    }
    fun clear(day: String,id: String) { entries.remove(day to id) }
}
