package com.kamalahmed.prayerapp.insights

import android.content.Context
import com.kamalahmed.prayerapp.core.safety.guard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class CoachPrefs(val hidden: Boolean = false, val mutedUntil: Map<String, Long> = emptyMap()) {
    fun muted(today: LocalDate): Set<String> = mutedUntil.filterValues { it >= today.toEpochDay() }.keys
}

/**
 * Coach choices persisted on the device (SharedPreferences "insights"):
 * "Hide coach" (backed up with other preferences) and "Not helpful" (mutes the shown suggestions for [MUTE_DAYS] days).
 */
@Singleton
class InsightsPrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val prefs get() = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val coach: StateFlow<CoachPrefs> = state.asStateFlow()
    // Backup restore writes the same file; keep the in-memory copy in sync (strong reference: Android holds it weakly).
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = read() }
    init { guard("InsightsPrefs", "listen") { prefs.registerOnSharedPreferenceChangeListener(listener) } }

    private fun read(): CoachPrefs = guard("InsightsPrefs", "read") {
        val p = prefs
        CoachPrefs(p.getBoolean(KEY_HIDDEN, false), p.getStringSet(KEY_MUTED, emptySet()).orEmpty().mapNotNull { e ->
            val id = e.substringBeforeLast('|', ""); val until = e.substringAfterLast('|').toLongOrNull()
            if (id.isEmpty() || until == null) null else id to until
        }.toMap())
    } ?: CoachPrefs()

    fun setHidden(hidden: Boolean) {
        guard("InsightsPrefs", "save hidden") { prefs.edit().putBoolean(KEY_HIDDEN, hidden).apply() }
        state.value = read()
    }

    /** "Not helpful": these suggestions stay hidden for [MUTE_DAYS] days; expired mutes are dropped. */
    fun mute(ids: Collection<String>, today: LocalDate = LocalDate.now()) {
        val until = today.plusDays(MUTE_DAYS).toEpochDay()
        val kept = state.value.mutedUntil.filterValues { it >= today.toEpochDay() } + ids.associateWith { until }
        guard("InsightsPrefs", "save muted") { prefs.edit().putStringSet(KEY_MUTED, kept.map { (k, v) -> "$k|$v" }.toSet()).apply() }
        state.value = read()
    }

    fun reload() { state.value = read() }

    companion object {
        const val NAME = "insights"
        const val KEY_HIDDEN = "coach_hidden"
        const val KEY_MUTED = "coach_muted"
        const val MUTE_DAYS = 28L
    }
}
