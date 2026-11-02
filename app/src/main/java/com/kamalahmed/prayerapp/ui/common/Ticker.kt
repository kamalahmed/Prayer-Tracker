package com.kamalahmed.prayerapp.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the current epoch millis aligned to each second. */
fun secondTicker(): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay(1000 - now % 1000)
    }
}
