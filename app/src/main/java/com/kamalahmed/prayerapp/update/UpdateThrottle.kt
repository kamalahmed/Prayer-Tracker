package com.kamalahmed.prayerapp.update

object UpdateThrottle {
    const val INTERVAL_MS = 5 * 60_000L
    /** Wall-clock rollback must not suppress checks indefinitely. Failed attempts are throttled too. */
    fun due(now: Long, lastAttempt: Long): Boolean = lastAttempt <= 0 || now < lastAttempt || now - lastAttempt >= INTERVAL_MS
}
