package com.kamalahmed.prayerapp.tracker

import com.kamalahmed.prayerapp.core.model.Prayer
import com.kamalahmed.prayerapp.core.prayer.PrayerSchedule
import com.kamalahmed.prayerapp.data.prayer.PrayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

fun List<PrayerLog>.toTrackerData(): TrackerData = groupBy { LocalDate.parse(it.date) }.mapValues { (_, logs) ->
    logs.mapNotNull { l -> Prayer.entries.firstOrNull { it.name == l.prayer }?.let { it to l.status } }.toMap()
}

@Singleton
class TrackerRepository @Inject constructor(
    private val dao: PrayerLogDao,
    private val prayers: PrayerRepository,
) {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** Emits after every change (used to refresh widgets). */
    val changes: SharedFlow<Unit> = _changes

    val all: Flow<TrackerData> = dao.observeAll().map { it.toTrackerData() }

    fun day(date: LocalDate): Flow<Map<Prayer, PrayerStatus>> =
        dao.observeRange(date.toString(), date.toString()).map { it.toTrackerData()[date].orEmpty() }

    suspend fun snapshot(): TrackerData = dao.all().toTrackerData()

    suspend fun set(date: LocalDate, prayer: Prayer, status: PrayerStatus?) {
        if (status == null) dao.delete(date.toString(), prayer.name)
        else dao.upsert(PrayerLog(date.toString(), prayer.name, status))
        _changes.tryEmit(Unit)
    }

    /** Cycle: none -> on time -> late -> missed -> none. */
    suspend fun cycle(date: LocalDate, prayer: Prayer) {
        val cur = dao.range(date.toString(), date.toString()).firstOrNull { it.prayer == prayer.name }?.status
        val next = when (cur) {
            null -> PrayerStatus.ON_TIME
            PrayerStatus.ON_TIME -> PrayerStatus.LATE
            PrayerStatus.LATE -> PrayerStatus.MISSED
            PrayerStatus.MISSED -> null
        }
        set(date, prayer, next)
    }

    /**
     * Quick "I prayed" toggle (widgets / notifications): marks on time if still inside the prayer's
     * window, otherwise late; tapping again clears.
     */
    suspend fun togglePrayed(date: LocalDate, prayer: Prayer, now: ZonedDateTime = ZonedDateTime.now()) {
        val cur = dao.range(date.toString(), date.toString()).firstOrNull { it.prayer == prayer.name }?.status
        if (cur == PrayerStatus.ON_TIME || cur == PrayerStatus.LATE) { set(date, prayer, null); return }
        val schedule = prayers.currentSchedule()
        set(date, prayer, statusFor(schedule, date, prayer, now.withZoneSameInstant(schedule.zone)))
    }

    companion object {
        /** ON_TIME if [now] is before the end of the prayer window, else LATE. */
        fun statusFor(schedule: PrayerSchedule, date: LocalDate, prayer: Prayer, now: ZonedDateTime): PrayerStatus {
            val day = schedule.day(date)
            val end = when (prayer) {
                Prayer.FAJR -> day[Prayer.SUNRISE]
                Prayer.DHUHR -> day[Prayer.ASR]
                Prayer.ASR -> day[Prayer.MAGHRIB]
                Prayer.MAGHRIB -> day[Prayer.ISHA]
                Prayer.ISHA -> day.midnight
                Prayer.SUNRISE -> day[Prayer.DHUHR]
            }
            return if (now.isBefore(end)) PrayerStatus.ON_TIME else PrayerStatus.LATE
        }
    }
}
